package com.example.simplethumb.service.impl;

import cn.hutool.core.lang.Pair;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.simplethumb.listener.thumb.msg.ThumbEvent;
import com.example.simplethumb.mapper.BlogMapper;
import com.example.simplethumb.model.entity.Thumb;
import com.example.simplethumb.service.ThumbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.common.schema.SchemaType;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 点赞消息消费者
 * 负责将存储在 Redis 上的数据批量保存到数据库中
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThumbConsumer {

    private final BlogMapper blogMapper; // 博客数据访问对象

    private final ThumbService thumbService; // 点赞服务对象

    @PulsarListener(
            subscriptionName = "thumb-subscription", // 订阅名称
            topics = "thumb-topic", // 主题名称
            schemaType = SchemaType.JSON, // 消息的序列化类型
            batch = true, // 是否批量处理消息
            consumerCustomizer = "thumbConsumerConfig", // 消费者自定义配置
            negativeAckRedeliveryBackoff = "negativeAckRedeliveryBackOff", // 负确认重试间隔
            ackTimeoutRedeliveryBackoff = "ackTimeoutRedeliveryBackOff", // 超时重试间隔
            subscriptionType = SubscriptionType.Shared,// 订阅类型
            deadLetterPolicy = "deadLetterPolicy" // 死信队列策略
    )
    @Transactional(rollbackFor = Exception.class) // 事务管理，遇到异常时回滚
    public void processMessage(List<Message<ThumbEvent>> messages) {
        log.info("ThumbConsumer processBatch: {}", messages.size()); // 记录处理的消息数量
        Map<Long, Long> countMap = new ConcurrentHashMap<>(); // 用于存储点赞计数
        ArrayList<Thumb> thumbs = new ArrayList<>(); // 用于存储点赞信息

        // 并行处理消息
        LambdaQueryWrapper<Thumb> wrapper = new LambdaQueryWrapper<>(); // 构建查询条件
        AtomicReference<Boolean> needRemove = new AtomicReference<>(false); // 标记是否需要删除数据

        // 提取事件并过滤无效信息
        List<ThumbEvent> events = messages.stream()
                .map(Message::getValue) // 获取消息中的事件
                .filter(Objects::nonNull) // 过滤掉空事件
                .toList();

        // 按照 {userId, blogId} 分组，并取每个分组的最新事件
        Map<Pair<Long, Long>, ThumbEvent> latestEvents = events.stream()
                .collect(Collectors.groupingBy(
                        event -> Pair.of(event.getUserId(), event.getBlogId()), // 按 {userId, blogId} 分组
                        Collectors.collectingAndThen(
                                Collectors.toList(), // 收集到列表中
                                list -> {
                                    // 按照时间升序排序，取最后一个事件作为最新事件
                                    list.sort(Comparator.comparing(ThumbEvent::getEventTime));
                                    if (list.size() % 2 == 0) {
                                        return null; // 如果事件数量为偶数，返回null
                                    }
                                    return list.getLast(); // 返回最后一个事件
                                }
                        )
                ));
        // 处理最新事件
        latestEvents.forEach((userBlogPair, event)-> {
            if(event == null) {
                return; // 如果事件为空，跳过
            }
            // 确定属于哪一类事件
            ThumbEvent.EventType finalAction = event.getType();

            // 点赞事件
            if(finalAction == ThumbEvent.EventType.INCR) {
                countMap.merge(userBlogPair.getKey(), 1L, Long::sum); // 更新点赞计数
                Thumb thumb = new Thumb();
                thumb.setBlogId(event.getBlogId()); // 设置博客ID
                thumb.setUserId(event.getUserId()); // 设置用户ID
                thumbs.add(thumb); // 添加到点赞列表
            } else {
                // 取消点赞事件
                needRemove.set(true); // 标记需要删除数据
                wrapper.or().eq(Thumb::getBlogId, event.getBlogId()).eq(Thumb::getUserId, event.getUserId()); // 添加删除条件
                countMap.merge(event.getBlogId(), -1L, Long::sum); // 更新点赞计数
            }
        });
        // 批量更新数据库
        if(needRemove.get()) {
            thumbService.remove(wrapper); // 执行删除操作
        }
        batchUpdateBlogs(countMap); // 批量更新博客的点赞数
        batchInsertThumbs(thumbs); // 批量插入点赞信息
    }

    public void batchUpdateBlogs(Map<Long, Long> countMap) {
    // 检查传入的countMap是否为空
        if(!countMap.isEmpty()) {
        // 如果countMap不为空，则调用blogMapper的batchUpdateThumbCount方法
        // 该方法用于批量更新博客的点赞数
            blogMapper.batchUpdateThumbCount(countMap);
        }
    }

    public void batchInsertThumbs(List<Thumb> thumbs) {
    // 检查传入的thumbs列表是否为空
        if(!thumbs.isEmpty()) {
        // 如果列表不为空，则调用thumbService的saveBatch方法批量保存数据
        // 其中，第一个参数是要保存的thumbs列表，第二个参数是每次批量保存的记录数，这里设置为500
            thumbService.saveBatch(thumbs, 500);
        }
    }

// 使用PulsarListener注解标记该方法为Pulsar消息监听器，并指定监听的topic为"thumb-dlq-topic"
    @PulsarListener(topics = "thumb-dlq-topic")
    public void consumerDlq(Message<ThumbEvent> message) {
    // 获取消息的唯一标识符MessageId
        MessageId messageId = message.getMessageId();
    // 使用日志记录器log输出dlq（死信队列）消息的MessageId
        log.info("dlq message = {}", messageId);
    // 使用日志记录器log输出消息已入库的提示信息，包含MessageId
        log.info("消息 {} 已入库", messageId);
    // 使用日志记录器log输出已通知相关人员处理消息的提示信息，包含处理人员的名字和MessageId
        log.info("已通知相关人员 {} 处理消息 {}", "方", messageId);

    }


}
