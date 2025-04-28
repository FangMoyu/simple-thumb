package com.example.simplethumb.service.impl;


import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.simplethumb.common.ErrorCode;
import com.example.simplethumb.constant.RedisLuaScriptConstant;
import com.example.simplethumb.constant.ThumbConstant;
import com.example.simplethumb.exception.ThrowUtils;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;

/**
 * 时间片策略实现Redis点赞和取消点赞，基于 Lua 脚本实现
 */
@Service("thumbServiceRedis")
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceRedisImpl extends ServiceImpl<ThumbMapper, Thumb>
    implements ThumbService {

    private final UserService userService;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 点赞逻辑
     * @param doThumbRequest 点赞请求
     * @param request 用于获取登录用户信息
     * @return 点赞结果
     */
    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(doThumbRequest == null || doThumbRequest.getBlogId() == null,
                ErrorCode.PARAMS_ERROR, "参数错误");
        User loginUser = userService.getLoginUser(request);
        Long blogId = doThumbRequest.getBlogId();
        // 计算时间片
        String timeSlice = getTimeSlice();
        // 拼接 Redis Key
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUser.getId());

        // 执行 Lua 脚本
        Long result = redisTemplate.execute(
                RedisLuaScriptConstant.THUMB_SCRIPT,
                Arrays.asList(tempThumbKey, userThumbKey),
                loginUser.getId(),
                blogId
        );
        // 判断执行结果
        ThrowUtils.throwIf(LuaStatusEnum.FAIL.getValue().equals(result),
                ErrorCode.OPERATION_ERROR, "点赞失败");
        // 更新成功则返回
        return LuaStatusEnum.SUCCESS.getValue().equals(result);

    }

    /**
     * 取消点赞逻辑
     * @param doThumbRequest 点赞请求
     * @param request 用于获取登录用户信息
     * @return 取消点赞结果
     */
    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(doThumbRequest == null || doThumbRequest.getBlogId() == null, ErrorCode.PARAMS_ERROR, "参数错误");
        User loginUser = userService.getLoginUser(request);
        Long blogId = doThumbRequest.getBlogId();
        // 计算时间片
        String timeSlice = getTimeSlice();
        // 拼接 Redis Key
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUser.getId());

        // 执行 Lua 脚本
        Long result = redisTemplate.execute(
                RedisLuaScriptConstant.UNDO_THUMB_SCRIPT,
                Arrays.asList(tempThumbKey, userThumbKey),
                loginUser.getId(),
                blogId
        );
        // 根据返回值处理结果
        ThrowUtils.throwIf(LuaStatusEnum.FAIL.getValue().equals(result), ErrorCode.OPERATION_ERROR, "取消点赞失败");
        // 更新成功则返回
        return LuaStatusEnum.SUCCESS.getValue().equals(result);
    }

    /**
     * 获取当前时间的时间片，用于拼接缓存 Key
     * @return
     */
    private String getTimeSlice() {
        DateTime nowDate = DateUtil.date();
        // 获取到当前时间前最近的整数秒，例如 12:30:34, 则获取到 12:30:30
        return DateUtil.format(nowDate, "HH:mm:") + (DateUtil.second(nowDate) / 10) * 10;
    }

    /**
     * 判断当前博客是否点赞
     * @param blogId 博客Id
     * @param userId 用户Id
     * @return 返回缓存中是否存在点赞信息
     */
    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        return redisTemplate.opsForHash().hasKey(ThumbConstant.USER_THUMB_KEY_PREFIX + userId, blogId.toString());
    }
}





