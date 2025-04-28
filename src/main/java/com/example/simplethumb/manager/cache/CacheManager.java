package com.example.simplethumb.manager.cache;

import com.example.simplethumb.constant.ThumbConstant;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;


@Component
@Slf4j
public class CacheManager {
    // 定义一个 TopK 类型的成员变量，用于检测热键
    private TopK hotKeyDetector;
    // 定义一个 Cache 类型的成员变量，用于本地缓存
    private Cache<String, Object> localCache;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;


    // 辅助方法，构造复合 Key
    private String buildCacheKey(String hashKey, String key) {
        return hashKey + ":" + key;
    }

    /**
     * 设计多级缓存获取缓存数据
     * 从本地缓存 -> Redis
     * @param hashKey Redis中的hash键
     * @param key Redis中的具体键
     * @return 缓存中的值，如果未找到则返回null
     */
    public Object getCache(String hashKey, String key) {
        // 构造唯一的 composite key
        String compositeKey = buildCacheKey(hashKey, key);
        // 1.从本地缓存中获取数据
        Object value = localCache.getIfPresent(compositeKey);
        if(value != null) {
            log.info("从本地缓存中获取数据: {} = {}", compositeKey, value);
            // 记录访问次数，每次访问次数 + 1;
            hotKeyDetector.add(key, 1);
            return value;
        }
        // 2.本地缓存未命中，查询 Redis
        Object redisValue = redisTemplate.opsForHash().get(hashKey, key);
        if(redisValue == null) {
            return null;
        }
        // 3.记录访问
        AddResult addResult = hotKeyDetector.add(key, 1);

        // 优化单点热点问题实现方案,如果当前博客是热点key，则扫描Redis中所有的点赞记录保存到本地缓存
//        if(addResult.isHotKey()) {
//        // 如果是热Key，扫描Redis中符合特定模式的键
//            ScanOptions scanOptions = ScanOptions.scanOptions().match("thumb:*").build();
//            Cursor<String> cursor = redisTemplate.scan(scanOptions);
//            while(cursor.hasNext()) {
//                String tmpKey = cursor.next();
//            // 跳过特定前缀的键
//                if(tmpKey.equals(ThumbConstant.TEMP_THUMB_KEY_PREFIX)) {
//                    continue;
//                }
//            // 如果当前键包含目标key，则将其值放入本地缓存
//                if(redisTemplate.opsForHash().hasKey(tmpKey, key)) {
//                    Object thumbId = redisTemplate.opsForHash().get(tmpKey, key);
//                    localCache.put(tmpKey + key, thumbId);
//                }
//            }
//        }
        // 4. 如果是热 Key 且不在本地缓存，则缓存数据
        if(addResult.isHotKey() && !localCache.asMap().containsKey(compositeKey)) {
            localCache.put(compositeKey, redisValue);
        }
        return redisValue;
    }

    // 定义一个方法，用于在缓存中更新指定键的值，但仅当该键已经存在时
    public void putIfPresent(String hashKey, String key, Object value) {
        // 构建一个复合键，由hashKey和key拼接而成
        String compositeKey = buildCacheKey(hashKey, key);
        // 从本地缓存中获取该复合键对应的值
        Object object = localCache.getIfPresent(compositeKey);
        // 如果该复合键不存在于缓存中，则直接返回，不进行任何操作
        if(object == null) {
            return;
        }
        // 如果该复合键存在于缓存中，则更新该键对应的值为传入的value
        localCache.put(compositeKey, value);
    }

    // 定期清理过期的热 Key 检测数据
    @Scheduled(fixedRate = 20, timeUnit = TimeUnit.SECONDS)
    public void cleanHotKeys() {
        hotKeyDetector.fading();
    }

    // 定义一个 Bean，用于获取热键检测器
    @Bean
    public TopK getHotKeyDetector() {
        // 监控 Top 100 Key， 数组宽度为 100000，深度为 5，衰减系数为 0.92，最小出现 10 次才记录
        hotKeyDetector = new HeavyKeeper(100, 100000, 5, 0.92, 10);
        return hotKeyDetector;
    }

    @Bean
// 标记该方法为一个Spring Bean，Spring框架会自动管理这个Bean的生命周期
    public Cache<String, Object> getLocalCache() {
    // 定义一个方法，返回一个类型为Cache<String, Object>的本地缓存实例
        return localCache = Caffeine.newBuilder()
            // 使用Caffeine库创建一个新的缓存构建器
                .maximumSize(1000)
            // 设置缓存的最大容量为1000个条目
                .expireAfterWrite(5, TimeUnit.MINUTES)
            // 设置缓存条目在写入后5分钟自动过期
                .build();
    // 构建并返回缓存实例
    }
}
