package com.hmdp.utils;
import org.redisson.Redisson;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

public class BloomFilterManager {
    //单例模式
    private static volatile RBloomFilter<Object> bloomFilter;
    private static volatile RedissonClient client;
    //构造函数
    private BloomFilterManager(){}
    //static 引用这个实例的函数
    public static RBloomFilter<Object> getInstance() {
        if(bloomFilter == null) {
            synchronized(BloomFilterManager.class) {
                if(bloomFilter == null) {
                    Config config = new Config();
                    config.useSingleServer().setAddress("redis://127.0.0.1:6379");
                    client = Redisson.create(config);
                    bloomFilter = client.getBloomFilter("bloomFilter");
                    bloomFilter.tryInit(10000, 0.01);
                }
            }
        }
        return bloomFilter;
    }
    public static void shutdown() {
        if (client != null) {
            client.shutdown();
        }
    }

}
