package com.example.simplethumb.job;

import com.example.simplethumb.constant.ThumbConstant;
import com.example.simplethumb.listener.thumb.msg.ThumbEvent;
import com.example.simplethumb.model.entity.Thumb;
import com.example.simplethumb.model.enums.ThumbTypeEnum;
import com.example.simplethumb.service.ThumbService;
import com.google.common.collect.Sets;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.DiffBuilder;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ThumbReconcileJob {
    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private ThumbService thumbService;

    @Resource
    private PulsarTemplate<ThumbEvent> pulsarTemplate;

    @Scheduled(cron = "0 0 2 * * *")
    public void run() {
        long startTime = System.currentTimeMillis();

        // 1. 获取分片下的所有用户 ID
        Set<Long> userIds = new HashSet<>();
        String pattern = ThumbConstant.USER_THUMB_KEY_PREFIX + "*";
        try(Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {
            while(cursor.hasNext()) {
                String key = cursor.next();
                Long userId = Long.valueOf(key.replace(ThumbConstant.USER_THUMB_KEY_PREFIX, ""));
                userIds.add(userId);
            }
        }

        // 逐个用户比对
        userIds.forEach(userId -> {
            Set<Long> redisBlogIds = redisTemplate
                    .opsForHash()
                    .keys(ThumbConstant.USER_THUMB_KEY_PREFIX + userId)
                    .stream()
                    .map(obj -> Long.valueOf(obj.toString()))
                    .collect(Collectors.toSet());
            // 用 Optional.ofNullable 判断当前查询是否为空
            Set<Long> mysqlBlogIds = Optional.ofNullable(thumbService.lambdaQuery()
                            .eq(Thumb::getUserId, userId)
                            .list()
                    // 为空则创建一个空的列表List
                    ).orElse(new ArrayList<>())
                    // 将其用 map 映射成 blogIds
                    .stream()
                    .map(Thumb::getBlogId)
                    .collect(Collectors.toSet());
            // 比对 Redis 中有的但 MySQL 中没有
            Set<Long> diffBlogIds = Sets.difference(redisBlogIds, mysqlBlogIds);

            // 发送补偿事件
            sendCompensationEvents(userId, diffBlogIds);
        });

        log.info("对账任务完成，耗时{}", System.currentTimeMillis() - startTime);
    }

    public void sendCompensationEvents(Long userId, Set<Long> blogIds) {
    // 遍历传入的博客ID集合
        blogIds.forEach(blogId -> {
        // 创建一个点赞事件对象，包含用户ID、博客ID、事件类型（增加）和当前时间
            ThumbEvent thumbEvent = new ThumbEvent(userId, blogId, ThumbEvent.EventType.INCR, LocalDateTime.now());
        // 异步发送点赞事件到指定的Pulsar主题
            pulsarTemplate.sendAsync("thumb-topic", thumbEvent)
                // 处理发送过程中可能出现的异常
                    .exceptionally(ex -> {
                    // 如果发送失败，记录错误日志，包含用户ID和博客ID
                        log.error("发送补偿事件失败 userId = {}, blogId = {}", userId, blogId);
                    // 返回null以表示异常已处理
                        return null;
                    });
        });
    }
}
