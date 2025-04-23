package com.example.simplethumb.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.simplethumb.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;

/**
 *
 */
public interface UserService extends IService<User> {
    User getLoginUser(HttpServletRequest request);
}
