package com.example.simplethumb.job;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.StrPool;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.simplethumb.constant.ThumbConstant;
import com.example.simplethumb.manager.cache.CacheManager;
import com.example.simplethumb.manager.cache.TopK;
import com.example.simplethumb.mapper.BlogMapper;
import com.example.simplethumb.model.entity.Thumb;
import com.example.simplethumb.model.enums.ThumbTypeEnum;
import com.example.simplethumb.service.ThumbService;
import com.example.simplethumb.util.RedisKeyUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 定时将 Redis 中的临时点赞数据同步到数据库
 *
 */

@Component
@Slf4j
public class SyncThumb2DBJob {

    @Resource
    private ThumbService thumbService;

    @Resource
    private BlogMapper blogMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private TopK hotKeyDetector;

    @Scheduled(fixedRate = 10000)
    @Transactional(rollbackFor = Exception.class)
    public void run() {
        log.info("开始执行临时点赞数据同步任务");
        DateTime nowDate = DateUtil.date();
        // 如果秒数为0 ~ 9，则回到上一分钟的 50 秒
        int second = (DateUtil.second(nowDate) / 10 - 1) * 10;
        if(second == -10) {
            second = 50;
            // 回到上一分钟
            nowDate = DateUtil.offsetMinute(nowDate, -1);
        }
        String date = DateUtil.format(nowDate, "HH:mm:") + second;
        syncThumb2DBByDate(date);
        log.info("临时数据同步完成");
    }

    public void syncThumb2DBByDate(String date) {
        // 获取到临时点赞和取消点赞数据
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(date);
        Map<Object, Object> allTempThumbMap = redisTemplate.opsForHash().entries(tempThumbKey);
        boolean thumbMapEmpty = CollUtil.isEmpty(allTempThumbMap);

        // 同步 点赞 到数据库
        // 构建插入列表并收集 blogId
        Map<Long, Long> blogThumbCountMap = new HashMap<>();
        // 如果灭有点赞数据，则直接返回
        if(thumbMapEmpty) {
            return;
        }
        ArrayList<Thumb> thumbList = new ArrayList<>();
        LambdaQueryWrapper<Thumb> wrapper = new LambdaQueryWrapper<>();
        // 用于判断是否存在取消点赞的情况
        boolean needRemove = false;
        // 保存在Redis临时表中的key是一个由userId和blogId组成的字符串，格式为userId:blogId
        for(Object userIdBlogIdObj : allTempThumbMap.keySet()) {
            // 强转字符串
            String userIdBlogId = (String) userIdBlogIdObj;
            // 分割字符串，拿到userId和blogId
            String[] userIdAndBlogId = userIdBlogId.split(StrPool.COLON);
            Long userId = Long.valueOf(userIdAndBlogId[0]);
            Long blogId = Long.valueOf(userIdAndBlogId[1]);
            // -1 取消点赞， 1 点赞
            Integer thumbType = Integer.valueOf(allTempThumbMap.get(userIdBlogId).toString());
            if (thumbType == ThumbTypeEnum.INCR.getValue()) {
                Thumb thumb = new Thumb();
                thumb.setUserId(userId);
                thumb.setBlogId(blogId);
                thumbList.add(thumb);
            } else if (thumbType == ThumbTypeEnum.DECR.getValue()) {
                // 判断确认要删除
                needRemove = true;
                // 拼接查询条件，批量删除
                wrapper.or().eq(Thumb::getUserId, userId).eq(Thumb::getBlogId, blogId);
            }else {
                if (thumbType != ThumbTypeEnum.NON.getValue()) {
                    log.warn("数据异常：{}", "用户id" + userId + "," + "博客id" + blogId + "," + "点赞类型" + thumbType);
                }
            }
            // 计算点赞数
            blogThumbCountMap.put(blogId, blogThumbCountMap.getOrDefault(blogId, 0L) + thumbType);
        }
        // 批量插入点赞数据
        thumbService.saveBatch(thumbList);
        // 批量删除取消点赞数据
        if (needRemove) {
            blogMapper.batchUpdateThumbCount(blogThumbCountMap);
        }

        // 虚拟线程将点赞数据在数据库同步后，异步删除 Redis 中的缓存
        Thread.startVirtualThread(() -> {
            redisTemplate.delete(tempThumbKey);

        });
    }

}
