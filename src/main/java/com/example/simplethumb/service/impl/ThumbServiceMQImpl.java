package com.example.simplethumb.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.simplethumb.common.ErrorCode;
import com.example.simplethumb.constant.RedisLuaScriptConstant;
import com.example.simplethumb.constant.ThumbConstant;
import com.example.simplethumb.exception.ThrowUtils;
import com.example.simplethumb.listener.thumb.msg.ThumbEvent;
import com.example.simplethumb.manager.cache.CacheManager;
import com.example.simplethumb.mapper.ThumbMapper;
import com.example.simplethumb.model.dto.thumb.DoThumbRequest;
import com.example.simplethumb.model.entity.Blog;
import com.example.simplethumb.model.entity.Thumb;
import com.example.simplethumb.model.entity.User;
import com.example.simplethumb.model.enums.LuaStatusEnum;
import com.example.simplethumb.service.BlogService;
import com.example.simplethumb.service.ThumbService;
import com.example.simplethumb.service.UserService;
import com.example.simplethumb.util.RedisKeyUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息队列优化
 * 将业务改造为消息发送者
 */
// 显示指定 Bean 的名称为 thumbServiceDB，注入时只需要把变量名改成thumbServiceDB即可
@Service("thumbService")
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceMQImpl extends ServiceImpl<ThumbMapper, Thumb>
    implements ThumbService {

    private final UserService userService;

    private final RedisTemplate<String, Object> redisTemplate;
    private final PulsarTemplate<ThumbEvent> pulsarTemplate;

    /**
     * 消息队列优化点赞功能
     * 服务发送方
     * @param doThumbRequest
     * @param request
     * @return
     */
    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        // 参数校验
    // 如果 doThumbRequest 为空或 doThumbRequest 中的 blogId 为空，则抛出参数错误异常
        ThrowUtils.throwIf(doThumbRequest == null || doThumbRequest.getBlogId() == null,
                ErrorCode.PARAMS_ERROR, "参数错误");
    // 获取当前登录的用户信息
        User loginUser = userService.getLoginUser(request);
    // 获取当前登录用户的 ID
        Long loginUserId = loginUser.getId();
    // 获取请求中的博客 ID
        Long blogId = doThumbRequest.getBlogId();
    // 生成用户点赞的 Redis 键
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUserId);
        // 执行 Lua 脚本，点赞存入 Redis
        long result = redisTemplate.execute(
                RedisLuaScriptConstant.THUMB_SCRIPT_MQ,
                List.of(userThumbKey),
                blogId
        );
        ThrowUtils.throwIf(LuaStatusEnum.FAIL.getValue() == result, ErrorCode.OPERATION_ERROR, "点赞失败,用户已经点赞");
        ThumbEvent thumbEvent = ThumbEvent.builder()
                .userId(loginUserId)
                .blogId(blogId)
                .type(ThumbEvent.EventType.INCR)
                .eventTime(LocalDateTime.now())
                .build();
        // 发送点赞事件到 Pulsar
        // 当sendAsync方法出现异常时，会调用 exceptionally 的逻辑
        pulsarTemplate.sendAsync("thumb-topic", thumbEvent).exceptionally(ex -> {
            // 删除缓存
            redisTemplate.opsForHash().delete(userThumbKey, blogId.toString(), true);
            // 记录日志
            log.error("点赞事件发送失败: userId={}, blogId={}", loginUserId, blogId);
            // 新开线程,没有需要返回的内容
            return null;
        });

        return true;
    }

    /**
     * 消息队列优化取消点赞功能
     * 服务发送方
     * @param doThumbRequest
     * @param request
     * @return
     */
    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(doThumbRequest == null || doThumbRequest.getBlogId() == null, ErrorCode.PARAMS_ERROR, "参数错误");
        User loginUser = userService.getLoginUser(request);
        Long loginUserId = loginUser.getId();
        Long blogId = doThumbRequest.getBlogId();
        // 获取到当前登录用户点赞信息
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUserId);
        // 执行 Lua 脚本，点赞记录从 Redis 中删除
        long result = redisTemplate.execute(
                RedisLuaScriptConstant.UNTHUMB_SCRIPT_MQ,
                List.of(userThumbKey),
                blogId
        );
        ThrowUtils.throwIf(LuaStatusEnum.FAIL.getValue() == result, ErrorCode.OPERATION_ERROR, "取消点赞失败,用户未点赞");
        ThumbEvent thumbEvent = ThumbEvent.builder()
                .userId(loginUserId)
                .blogId(blogId)
                .eventTime(LocalDateTime.now())
                .type(ThumbEvent.EventType.DECR)
                .build();
        pulsarTemplate.sendAsync("thumb-topic", thumbEvent).exceptionally(ex -> {
            // 删除缓存
            redisTemplate.opsForHash().delete(userThumbKey, blogId.toString(), true);
            // 记录日志
            log.error("取消点赞事件发送失败: userId={}, blogId={}", loginUserId, blogId);
            // 新开线程,没有需要返回的内容
            return null;
        });
        return true;

    }

    /**
     * 判断用户是否已经点赞过该博客
     * @param blogId
     * @param userId
     * @return
     */
    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        return redisTemplate.opsForHash().hasKey(RedisKeyUtil.getUserThumbKey(userId), blogId.toString());
    }
}





