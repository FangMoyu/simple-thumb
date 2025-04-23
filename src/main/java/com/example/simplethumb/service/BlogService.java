package com.example.simplethumb.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.example.simplethumb.model.entity.Blog;
import com.example.simplethumb.model.vo.BlogVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * (博客)表服务接口
 */
public interface BlogService extends IService<Blog> {
    BlogVO getBlogVOById(Long blogId, HttpServletRequest request);

    List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request);
}
