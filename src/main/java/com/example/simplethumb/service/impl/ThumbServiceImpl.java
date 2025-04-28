package com.example.simplethumb.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.simplethumb.common.ErrorCode;
import com.example.simplethumb.constant.ThumbConstant;
import com.example.simplethumb.exception.ThrowUtils;
import com.example.simplethumb.manager.cache.CacheManager;
import com.example.simplethumb.mapper.ThumbMapper;
import com.example.simplethumb.model.dto.thumb.DoThumbRequest;
import com.example.simplethumb.model.entity.Blog;
import com.example.simplethumb.model.entity.Thumb;
import com.example.simplethumb.model.entity.User;
import com.example.simplethumb.service.BlogService;
import com.example.simplethumb.service.ThumbService;
import com.example.simplethumb.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

// 显示指定 Bean 的名称为 thumbServiceDB，注入时只需要把变量名改成thumbServiceDB即可
@Service("thumbService")
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb>
    implements ThumbService {

    private final UserService userService;

    private final BlogService blogService;

    private final TransactionTemplate transactionTemplate;

    private final CacheManager cacheManager;

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(doThumbRequest == null || doThumbRequest.getBlogId() == null,
                ErrorCode.PARAMS_ERROR, "参数错误");
        User loginUser = userService.getLoginUser(request);
        // 加锁，避免用户短时间多次点赞
        synchronized (loginUser.getId().toString().intern()) {

            // 编程式事务
            return transactionTemplate.execute(status -> {
                // 获取当前当前进行点赞的博客id
                Long blogId = doThumbRequest.getBlogId();
                // 判断当前用户是否已经点赞过该博客,利用Redis缓存提高效率
                Boolean exists = this.hasThumb(blogId, loginUser.getId());
//                  原先采用数据库直接判断是否点赞
//                boolean exists = this.lambdaQuery()
//                        .eq(Thumb::getUserId, loginUser.getId())
//                        .eq(Thumb::getBlogId, blogId)
//                        .exists();

                // 如果已经点赞过，抛出异常
                ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "已经点赞过");

                // 更新博客点赞数 + 1
                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, blogId)
                        .setSql("thumbCount = thumbCount + 1")
                        .update();

                Thumb thumb = new Thumb();
                thumb.setBlogId(blogId);
                thumb.setUserId(loginUser.getId());
                //  保存点赞记录到数据库
                boolean success = update && this.save(thumb);
                if(success) {
                    // 如果成功保存点赞记录，则将其存储在缓存，key是点赞前缀 + 用户id，field 是博客id，value是点赞记录id
                    String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId();
                    String fieldKey = blogId.toString();
                    Long realThumbId = thumb.getId();
                    redisTemplate.opsForHash().put(hashKey, fieldKey, realThumbId);
                    cacheManager.putIfPresent(hashKey, fieldKey, realThumbId);
                }
                return success;
            });
        }
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(doThumbRequest == null || doThumbRequest.getBlogId() == null, ErrorCode.PARAMS_ERROR, "参数错误");
        User loginUser = userService.getLoginUser(request);
        // 加锁，避免用户短时间多次取消点赞
        synchronized (loginUser.getId().toString().intern()) {
            // 编程式事务
            return transactionTemplate.execute(status -> {
                Long blogId = doThumbRequest.getBlogId();
                // 判断当前用户是否已经点赞过该博客
//                Thumb thumb = this.lambdaQuery()
//                        .eq(Thumb::getBlogId, blogId)
//                        .eq(Thumb::getUserId, loginUser.getId())
//                        .one();
                Object thumbIdObj = cacheManager.getCache(ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId().toString(), blogId.toString());
                // 如果没有点赞过，抛出异常
                ThrowUtils.throwIf(thumbIdObj == null || thumbIdObj.equals(ThumbConstant.UN_THUMB_CONSTANT), ErrorCode.OPERATION_ERROR, "未点赞过");
                Long thumbId = Long.valueOf(thumbIdObj.toString());
                // 更新博客点赞数 - 1
                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, blogId)
                        .setSql("thumbCount = thumbCount - 1")
                        .update();
                // 删除点赞记录
                 boolean success = update && this.removeById(thumbId);
                 if(success) {
                     // 如果成功删除点赞记录，则将其从缓存中删除
                     String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId();
                     String fieldKey = blogId.toString();
                     redisTemplate.opsForHash().delete(hashKey, fieldKey);
                     // 如果取消点赞了，但是如果是热点key，就设置本地缓存的值为 0
                     cacheManager.putIfPresent(hashKey, fieldKey, ThumbConstant.UN_THUMB_CONSTANT);

                 }
                 return success;
            });
        }
    }
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 判断用户是否已经点赞过该博客
     * @param blogId
     * @param userId
     * @return
     */
    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
    // ThumbConstant.USER_THUMB_KEY_PREFIX是点赞记录的键前缀，加上userId和blogId形成完整的键
        Object thumbIdObj = cacheManager.getCache(ThumbConstant.USER_THUMB_KEY_PREFIX + userId.toString(), blogId.toString());
    // 检查缓存中是否存在该用户的点赞记录
        if(thumbIdObj == null) {
        // 如果缓存中没有记录，说明用户没有点赞，返回false
            return false;
        }
    // 将缓存中的点赞记录转换为Long类型
        Long thumbId = (long) thumbIdObj;
    // 避免取消点赞导致存储在本地缓存的热点 key 未删除，而 Redis 中的缓存已经被删除，约定当值为 0 时表示未点赞。
        return !thumbId.equals(ThumbConstant.UN_THUMB_CONSTANT);
    }
}





