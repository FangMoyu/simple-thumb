package com.example.simplethumb.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.simplethumb.common.ErrorCode;
import com.example.simplethumb.exception.ThrowUtils;
import com.example.simplethumb.mapper.ThumbMapper;
import com.example.simplethumb.model.dto.thumb.DoThumbRequest;
import com.example.simplethumb.model.entity.Blog;
import com.example.simplethumb.model.entity.Thumb;
import com.example.simplethumb.model.entity.User;
import com.example.simplethumb.service.BlogService;
import com.example.simplethumb.service.ThumbService;
import com.example.simplethumb.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 *
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb>
    implements ThumbService {

    private final UserService userService;

    private final BlogService blogService;

    private final TransactionTemplate transactionTemplate;

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(doThumbRequest == null || doThumbRequest.getBlogId() == null,
                ErrorCode.PARAMS_ERROR, "参数错误");
        User loginUser = userService.getLoginUser(request);
        // 加锁，避免用户短时间多次点赞
        synchronized (loginUser.getId().toString().intern()) {

            // 编程式事务
            return transactionTemplate.execute(status -> {
                // 获取当前当前进行点赞的博客id
                Long blogId = doThumbRequest.getBlogId();
                // 判断当前用户是否已经点赞过该博客
                boolean exists = this.lambdaQuery()
                        .eq(Thumb::getBlogId, blogId)
                        .eq(Thumb::getUserId, loginUser.getId())
                        .exists();
                // 如果已经点赞过，抛出异常
                ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "已经点赞过");

                // 更新博客点赞数 + 1
                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, blogId)
                        .setSql("thumbCount = thumbCount + 1")
                        .update();
                // 保存点赞记录
                Thumb thumb = new Thumb();
                thumb.setBlogId(blogId);
                thumb.setUserId(loginUser.getId());
                return update && this.save(thumb);
            });
        }
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(doThumbRequest == null || doThumbRequest.getBlogId() == null, ErrorCode.PARAMS_ERROR, "参数错误");
        User loginUser = userService.getLoginUser(request);
        // 加锁，避免用户短时间多次取消点赞
        synchronized (loginUser.getId().toString().intern()) {
            // 编程式事务
            return transactionTemplate.execute(status -> {
                // 获取当前当前进行点赞的博客id
                Long blogId = doThumbRequest.getBlogId();
                // 判断当前用户是否已经点赞过该博客
                Thumb thumb = this.lambdaQuery()
                        .eq(Thumb::getBlogId, blogId)
                        .eq(Thumb::getUserId, loginUser.getId())
                        .one();
                // 如果没有点赞过，抛出异常
                ThrowUtils.throwIf(thumb == null, ErrorCode.OPERATION_ERROR, "未点赞过");

                // 更新博客点赞数 - 1
                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, blogId)
                        .setSql("thumbCount = thumbCount - 1")
                        .update();
                // 删除点赞记录
                return update && this.removeById(thumb.getId());
            });
        }
    }
}





