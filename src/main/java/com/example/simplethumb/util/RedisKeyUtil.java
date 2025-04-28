package com.example.simplethumb.util;

import com.example.simplethumb.constant.ThumbConstant;

public class RedisKeyUtil {
    /**
     * 获取用户点赞 key
     * @param userId
     * @return
     */
    public static String getUserThumbKey(Long userId) {
        return ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
    }

    /**
     * 获取临时表点赞记录 key
     * @param time
     * @return
     */
    public static String getTempThumbKey(String time) {
        return ThumbConstant.TEMP_THUMB_KEY_PREFIX.formatted(time);
    }
}
