package com.example.simplethumb.Controller;


import com.example.simplethumb.common.BaseResponse;
import com.example.simplethumb.common.ErrorCode;
import com.example.simplethumb.common.ResultUtils;
import com.example.simplethumb.exception.ThrowUtils;
import com.example.simplethumb.model.entity.Blog;
import com.example.simplethumb.model.vo.BlogVO;
import com.example.simplethumb.service.BlogService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 博客 Controller
 * 处理博客相关的请求和简单的业务逻辑
 */
@RestController
@RequestMapping("/blog")
public class BlogController {
    @Resource
    private BlogService blogService;

    /**
     * 获取博客
     * @param blogId
     * @param request
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<BlogVO> get(long blogId, HttpServletRequest request) {
        BlogVO blogVO = blogService.getBlogVOById(blogId, request);
        return ResultUtils.success(blogVO);
    }

    /**
     * 添加博客
     * @param blog
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> add(@RequestBody Blog blog, HttpServletRequest request) {
        // 博客判空
        ThrowUtils.throwIf(blog == null, ErrorCode.PARAMS_ERROR, "博客信息不能为空");
        // 保存到数据库
        boolean save = blogService.save(blog);
        // 保存失败时抛出异常
        ThrowUtils.throwIf(save, ErrorCode.SYSTEM_ERROR, "添加博客失败");
        // 返回博客id
        return ResultUtils.success(blog.getId());
    }

    @GetMapping("/list")
    public BaseResponse<List<BlogVO>> list(HttpServletRequest request) {
        // 获取博客列表
        List<Blog> blogList = blogService.list();
        // 将其经过处理转为VO后返回给前端
        List<BlogVO> blogVOList = blogService.getBlogVOList(blogList, request);
        return ResultUtils.success(blogVOList);
    }

}
