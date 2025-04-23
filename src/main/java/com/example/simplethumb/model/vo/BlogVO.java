package com.example.simplethumb.model.vo;

import com.example.simplethumb.model.entity.Blog;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.util.Date;

/**
 * 博客返回包装类
 * 额外关联上传图片的点赞信息
 */
@Data
public class BlogVO {

    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 封面
     */
    private String coverImg;

    /**
     * 内容
     */
    private String content;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 是否已点赞
     */
    private Boolean hasThumb;


    /**
     * 封装类转对象
     *
     * @param blogVO
     * @return
     */
    public static Blog voToObj(BlogVO blogVO) {
        if (blogVO == null) {
            return null;
        }
        Blog blog = new Blog();
        BeanUtils.copyProperties(blogVO, blog);
        return blog;
    }

    /**
     * 对象转封装类
     *
     * @param blog
     * @return
     */
    public static BlogVO objToVo(Blog blog) {
        if (blog == null) {
            return null;
        }
        BlogVO blogVO = new BlogVO();
        BeanUtils.copyProperties(blog, blogVO);
        return blogVO;
    }
}
