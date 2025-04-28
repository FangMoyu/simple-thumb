package com.example.simplethumb.manager.cache;

import cn.hutool.core.util.HashUtil;
import lombok.Data;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class HeavyKeeper implements TopK {
    private static final int LOOKUP_TABLE_SIZE = 256;  
    private final int k;  
    private final int width;  
    private final int depth;  
    private final double[] lookupTable;  
    private final Bucket[][] buckets;  
    private final PriorityQueue<Node> minHeap;
    private final BlockingQueue<Item> expelledQueue;
    private final Random random;
    private long total;  
    private final int minCount;  
  
    public HeavyKeeper(int k, int width, int depth, double decay, int minCount) {  
        this.k = k;  
        this.width = width;  
        this.depth = depth;  
        this.minCount = minCount;  
  
        this.lookupTable = new double[LOOKUP_TABLE_SIZE];  
        for (int i = 0; i < LOOKUP_TABLE_SIZE; i++) {  
            lookupTable[i] = Math.pow(decay, i);  
        }  
  
        this.buckets = new Bucket[depth][width];  
        for (int i = 0; i < depth; i++) {  
            for (int j = 0; j < width; j++) {  
                buckets[i][j] = new Bucket();  
            }  
        }  
  
        this.minHeap = new PriorityQueue<>(Comparator.comparingInt(n -> n.count));  
        this.expelledQueue = new LinkedBlockingQueue<>();
        this.random = new Random();  
        this.total = 0;  
    }

    @Override
    public AddResult add(String key, int increment) {
    // 将字符串键转换为字节数组
        byte[] keyBytes = key.getBytes();
    // 计算键的指纹值
        long itemFingerprint = hash(keyBytes);
    // 初始化最大计数器
        int maxCount = 0;

    // 遍历每一层过滤器
        for (int i = 0; i < depth; i++) {
        // 计算桶的编号
            int bucketNumber = Math.abs(hash(keyBytes)) % width;
        // 获取对应的桶
            Bucket bucket = buckets[i][bucketNumber];

        // 同步锁，确保线程安全
            synchronized (bucket) {
            // 如果桶为空，则初始化桶
                if (bucket.count == 0) {
                    bucket.fingerprint = itemFingerprint;
                    bucket.count = increment;
                // 更新最大计数器
                    maxCount = Math.max(maxCount, increment);
                } else if (bucket.fingerprint == itemFingerprint) {
                    bucket.count += increment;
                    maxCount = Math.max(maxCount, bucket.count);
                } else {
                    for (int j = 0; j < increment; j++) {
                        double decay = bucket.count < LOOKUP_TABLE_SIZE ?
                                lookupTable[bucket.count] :
                                lookupTable[LOOKUP_TABLE_SIZE - 1];
                        if (random.nextDouble() < decay) {
                            bucket.count--;
                            if (bucket.count == 0) {
                                bucket.fingerprint = itemFingerprint;
                                bucket.count = increment - j;
                                maxCount = Math.max(maxCount, bucket.count);
                                break;
                            }
                        }
                    }
                }
            }
        }

        total += increment;

        if (maxCount < minCount) {
            return new AddResult(null, false, null);
        }

        synchronized (minHeap) {
            boolean isHot = false;
            String expelled = null;

            Optional<Node> existing = minHeap.stream()
                    .filter(n -> n.key.equals(key))
                    .findFirst();

            if (existing.isPresent()) {
                minHeap.remove(existing.get());
                minHeap.add(new Node(key, maxCount));
                isHot = true;
            } else {
                if (minHeap.size() < k || maxCount >= Objects.requireNonNull(minHeap.peek()).count) {
                    Node newNode = new Node(key, maxCount);
                    if (minHeap.size() >= k) {
                        expelled = minHeap.poll().key;
                        expelledQueue.offer(new Item(expelled, maxCount));
                    }
                    minHeap.add(newNode);
                    isHot = true;
                }
            }

            return new AddResult(expelled, isHot, key);
        }
    }


    @Override
    public List<Item> list() {
        synchronized (minHeap) {  
            List<Item> result = new ArrayList<>(minHeap.size());
            for (Node node : minHeap) {  
                result.add(new Item(node.key, node.count));  
            }  
            result.sort((a, b) -> Integer.compare(b.count(), a.count()));  
            return result;  
        }  
    }  
  
    @Override  
    public BlockingQueue<Item> expelled() {
        return expelledQueue;  
    }  
  
    @Override  
    public void fading() {  
        // 遍历所有的buckets二维数组中的每个Bucket对象
        for (Bucket[] row : buckets) {
            for (Bucket bucket : row) {  
                // 对每个Bucket对象加锁，确保线程安全
                synchronized (bucket) {
                    // 将Bucket对象的count值右移一位，相当于除以2
                    bucket.count = bucket.count >> 1;
                }  
            }  
        }  
          
        // 对minHeap优先队列加锁，确保线程安全
        synchronized (minHeap) {
            // 创建一个新的优先队列，用于存储更新后的Node对象
            PriorityQueue<Node> newHeap = new PriorityQueue<>(Comparator.comparingInt(n -> n.count));
            // 遍历minHeap中的每个Node对象
            for (Node node : minHeap) {
                // 将Node对象的count值右移一位，相当于除以2，并添加到新的优先队列中
                newHeap.add(new Node(node.key, node.count >> 1));
            }  
            // 清空原来的minHeap
            minHeap.clear();
            // 将新的优先队列中的所有元素添加回minHeap
            minHeap.addAll(newHeap);
        }  
          
        // 将total值右移一位，相当于除以2
        total = total >> 1;
    }  
  
    @Override  
    public long total() {  
        return total;  
    }  
  
    private static class Bucket {  
        long fingerprint;  
        int count;  
    }  
  
    private static class Node {  
        final String key;  
        final int count;  
          
        Node(String key, int count) {  
            this.key = key;  
            this.count = count;  
        }  
    }  
  
    private static int hash(byte[] data) {  
        return HashUtil.murmur32(data);
    }
 }
// 新增返回结果类
@Data
class AddResult {
    // 被挤出的 key
    private final String expelledKey;
    // 当前 key 是否进入 TopK
    private final boolean isHotKey;
    // 当前操作的 key
    private final String currentKey;

// 定义一个名为AddResult的构造函数，用于初始化AddResult类的实例
    public AddResult(String expelledKey, boolean isHotKey, String currentKey) {
    // 将传入的expelledKey参数赋值给当前对象的expelledKey属性
        this.expelledKey = expelledKey;
    // 将传入的isHotKey参数赋值给当前对象的isHotKey属性
        this.isHotKey = isHotKey;
    // 将传入的currentKey参数赋值给当前对象的currentKey属性
        this.currentKey = currentKey;
    }

}
