package com.hmdp;

import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.redisson.Redisson;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private ShopServiceImpl shopService;
    @Autowired
    private RedissonClient client;

    @Test
    public void load() throws InterruptedException {

        RBloomFilter<Object> bloomFilter = client.getBloomFilter("user");
        bloomFilter.tryInit(10000, 0.01);

        for (Integer i = 0; i < 10000; i++) {
            bloomFilter.add(i);
        }

        // 统计元素
        int count = 0;
        for (int i = 10000; i < 10000*2; i++) {
            if (bloomFilter.contains(i)) {
                count++;
            }
        }
        System.out.println("误判次数" + count);



    }

}
