package com.example.simplethumb.listener.thumb.msg;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.pulsar.common.events.EventType;

import java.io.Serializable;
import java.time.LocalDateTime;


/**
 * 点赞事件类
 * 用于作为消息队列的传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThumbEvent implements Serializable {

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 博客 id
     */
    private Long blogId;

    /**
     * 事件类型
     */
    private EventType type;

    /**
     * 事件发生时间
     */
    private LocalDateTime eventTime;

    /**
     * 事件枚举类
     */
    public enum EventType {
        /**
         * 点赞
         */
        INCR,

        /**
         * 取消点赞
         */
        DECR,
    }
}
