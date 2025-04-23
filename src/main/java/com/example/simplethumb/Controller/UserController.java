package com.example.simplethumb.Controller;

import cn.hutool.core.util.ObjectUtil;
import com.example.simplethumb.common.BaseResponse;
import com.example.simplethumb.common.ErrorCode;
import com.example.simplethumb.common.ResultUtils;
import com.example.simplethumb.constant.UserConstant;
import com.example.simplethumb.exception.ThrowUtils;
import com.example.simplethumb.model.entity.User;
import com.example.simplethumb.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UserController
 * 处理用户的请求和简单的业务逻辑
 */
@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private UserService userService;

    /**
     * 登录
     * @param userId
     * @param request
     * @return
     */
    @GetMapping("/login")
    public BaseResponse<User> login(long userId, HttpServletRequest request) {
        User user = userService.getById(userId);
        // 如果没有该用户，抛出异常
        ThrowUtils.throwIf(ObjectUtil.isNull(user), ErrorCode.PARAMS_ERROR,"用户不存在");
        // 将用户登录信息保存在 Session
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);
        return ResultUtils.success(user);
    }

    /**
     * 获取当前登录用户
     * @param request
     * @return
     */
    @GetMapping("/get/login")
    public BaseResponse<User> getLoginUser(HttpServletRequest request) {
        User loginUser = (User) request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        return ResultUtils.success(loginUser);
    }
}
