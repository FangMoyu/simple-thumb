package com.example.simplethumb.mapper;

import com.example.simplethumb.model.entity.Blog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * @Entity com.example.simplethumb.model.entity.Blog
 */
public interface BlogMapper extends BaseMapper<Blog> {
    void batchUpdateThumbCount(@Param("countMap") Map<Long, Long> countMap);
}




