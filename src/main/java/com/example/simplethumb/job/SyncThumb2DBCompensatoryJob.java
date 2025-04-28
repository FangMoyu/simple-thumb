package com.example.simplethumb.job;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import com.example.simplethumb.constant.ThumbConstant;
import com.example.simplethumb.util.RedisKeyUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 定时将 Redis 中的临时点赞数据同步到数据库的补偿措施
 */
@Component
@Slf4j
public class SyncThumb2DBCompensatoryJob {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private SyncThumb2DBJob syncThumb2DBJob;

    @Scheduled(cron = "0 0 2 * * *")
    public void run() {
        log.info("开始补偿缺失数据");
        Set<String> thumbKeys = redisTemplate.keys(RedisKeyUtil.getTempThumbKey("") + "*");
        Set<String> needHandleDataSet = new HashSet<>();
        thumbKeys.stream().filter(ObjUtil::isNotEmpty).forEach(thumbKey ->
                needHandleDataSet.add(
                        thumbKey.replace(
                                ThumbConstant.TEMP_THUMB_KEY_PREFIX.formatted(""), "")));

        if(CollUtil.isEmpty(needHandleDataSet)) {
            log.info("补偿缺失数据结束，未发现需要补偿的数据");
            return;
        }

        // 补偿数据
        for(String date : needHandleDataSet) {
            syncThumb2DBJob.syncThumb2DBByDate(date);
        }
        log.info("临时数据补偿完成");
    }
}
