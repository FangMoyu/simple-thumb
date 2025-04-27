package com.example.simplethumb.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.simplethumb.constant.ThumbConstant;
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
import org.springframework.data.redis.core.RedisTemplate;
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

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
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
     * 获取博客列表，目的是展示当前登录用户视角下的博客列表情况
     * @param blogList
     * @param request
     * @return
     */
    @Override
    public List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request) {
        // 获取登录用户，用于判断是否点赞
        User loginUser = userService.getLoginUser(request);
        // 用哈希表存储博客是否被点赞
        Map<Long, Boolean> blogIdHasThumbMap = new HashMap<>();
        // 判断用户是否登录
        if(ObjUtil.isNotEmpty(loginUser)) {
            // 若登录，获取到所有博客的 id
            List<Object> blogIdList = blogList.stream().map(blog -> blog.getId().toString()).collect(Collectors.toList());
            // 从 Redis 中找到所有的点赞记录
            List<Object> thumbList = redisTemplate.opsForHash().multiGet(ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId().toString(), blogIdList);
            for (int i = 0; i < thumbList.size(); i++) {
                // 如果没有记录则继续
                if(thumbList.get(i) == null) {
                    continue;
                }
                // 如果有记录，则将博客 id 和点赞状态存入哈希表
                blogIdHasThumbMap.put(Long.valueOf(blogIdList.get(i).toString()), true);
            }
        }
        // 将博客列表转换为博客VO列表
        ArrayList<BlogVO> blogVOList = new ArrayList<>();
        for (Blog blog : blogList) {
            BlogVO blogVO = new BlogVO();
            BeanUtil.copyProperties(blog, blogVO);
            // 默认是未点赞的，如果有点赞则为 true
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
        Boolean exist = thumbService.hasThumb(blog.getId(), loginUser.getId());
        blogVO.setHasThumb(exist);
        return blogVO;
    }
}




