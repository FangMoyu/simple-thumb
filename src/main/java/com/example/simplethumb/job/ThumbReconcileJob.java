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

/**
 * 点赞对账任务
 */
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
// 使用 @Scheduled 注解指定该方法按照 cron 表达式定时执行，每天凌晨2点执行一次
    public void run() {
        long startTime = System.currentTimeMillis();

    // 记录任务开始时间
        // 1. 获取分片下的所有用户 ID
        Set<Long> userIds = new HashSet<>();
    // 创建一个 HashSet 用于存储用户 ID
        String pattern = ThumbConstant.USER_THUMB_KEY_PREFIX + "*";
    // 定义 Redis 键的匹配模式，用于扫描 Redis 中所有以 USER_THUMB_KEY_PREFIX 开头的键
        try(Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match(pattern).count(1000).build())) {
        // 使用 RedisTemplate 的 scan 方法扫描匹配模式的键，每次扫描返回 1000 个键
            while(cursor.hasNext()) {
            // 遍历扫描结果
                String key = cursor.next();
            // 获取当前键
                Long userId = Long.valueOf(key.replace(ThumbConstant.USER_THUMB_KEY_PREFIX, ""));
            // 从键中提取用户 ID
                userIds.add(userId);
            // 将用户 ID 添加到 userIds 集合中
            }
        }

        // 逐个用户比对
        userIds.forEach(userId -> {
        // 遍历所有用户 ID
            Set<Long> redisBlogIds = redisTemplate
                    .opsForHash()
                    .keys(ThumbConstant.USER_THUMB_KEY_PREFIX + userId)
                    .stream()
                    .map(obj -> Long.valueOf(obj.toString()))
                    .collect(Collectors.toSet());
        // 从 Redis 中获取当前用户的所有点赞的博客 ID，并将其转换为 Set 集合
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
        // 从 MySQL 中获取当前用户的所有点赞的博客 ID，并将其转换为 Set 集合
            // 比对 Redis 中有的但 MySQL 中没有
            Set<Long> diffBlogIds = Sets.difference(redisBlogIds, mysqlBlogIds);

        // 计算两个集合的差集，即 Redis 中有但 MySQL 中没有的博客 ID
            // 发送补偿事件
            sendCompensationEvents(userId, diffBlogIds);
        // 调用 sendCompensationEvents 方法发送补偿事件，处理数据不一致的情况
        });

        log.info("对账任务完成，耗时{}", System.currentTimeMillis() - startTime);
    // 记录任务完成时间并计算耗时，输出日志信息
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
