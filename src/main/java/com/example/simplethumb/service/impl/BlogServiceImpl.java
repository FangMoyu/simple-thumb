package com.example.simplethumb.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.simplethumb.mapper.BlogMapper;
import com.example.simplethumb.model.entity.Blog;
import com.example.simplethumb.model.entity.Thumb;
import com.example.simplethumb.model.entity.User;
import com.example.simplethumb.model.vo.BlogVO;
import com.example.simplethumb.service.BlogService;
import com.example.simplethumb.service.ThumbService;
import com.example.simplethumb.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 *
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog>
    implements BlogService {

    @Resource
    private UserService userService;

    // @Lazy 解决循环引用问题
    @Resource
    @Lazy
    private ThumbService thumbService;

    @Override
    public BlogVO getBlogVOById(Long blogId, HttpServletRequest request) {
        Blog blog = this.getById(blogId);
        User loginUser = userService.getLoginUser(request);
        return this.getBlogVO(blog, loginUser);
    }

    /**
     * 获取博客列表
     * @param blogList
     * @param request
     * @return
     */
    @Override
    public List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Map<Long, Boolean> blogIdHasThumbMap = new HashMap<>();
        if(ObjUtil.isNotEmpty(loginUser)) {
            Set<Long> blogIdSet = blogList.stream().map(Blog::getId).collect(Collectors.toSet());
            List<Thumb> thumbList = thumbService.lambdaQuery()
                    .eq(Thumb::getBlogId, blogIdSet)
                    .eq(Thumb::getUserId, loginUser.getId())
                    .list();

            thumbList.forEach(thumb -> blogIdHasThumbMap.put(thumb.getBlogId(), true));
        }
        ArrayList<BlogVO> blogVOList = new ArrayList<>();
        for (Blog blog : blogList) {
            BlogVO blogVO = new BlogVO();
            BeanUtil.copyProperties(blog, blogVO);
            blogVO.setHasThumb(blogIdHasThumbMap.getOrDefault(blog.getId(), false));
            blogVOList.add(blogVO);
        }
           return blogVOList;
    }
    private BlogVO getBlogVO(Blog blog, User loginUser) {
        BlogVO blogVO = new BlogVO();
        // 将博客信息复制到博客VO中
        BeanUtils.copyProperties(blog, blogVO);
        // 没有登录用户直接返回博客信息
        if(loginUser == null) {
            return blogVO;
        }
        // 查找当前登录用户是否已点赞
        Thumb thumb = thumbService.lambdaQuery()
                .eq(Thumb::getBlogId, blog.getId())
                .eq(Thumb::getUserId, loginUser.getId())
                .one();
        blogVO.setHasThumb(thumb != null);

        return blogVO;
    }
}




