package com.example.simplethumb.service;

import com.example.simplethumb.model.dto.thumb.DoThumbRequest;
import com.example.simplethumb.model.entity.Thumb;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

/**
 *
 */
public interface ThumbService extends IService<Thumb> {
    Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);

    Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);
}
