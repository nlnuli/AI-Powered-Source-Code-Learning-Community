package com.hmdp.config;

import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.BloomFilterManager;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BloomFilterInit implements CommandLineRunner {
    @Autowired
    private ShopServiceImpl shopService;
    @Autowired
    private ShopMapper shopMapper;
    @Override
    public void run(String... args) throws Exception {
        System.out.println(">>>>>>>>>>>>>>>服务启动执行，执行BloomFilter的加载工作 <<<<<<<<<<<<<");
        RBloomFilter<Object> bloomFilter = BloomFilterManager.getInstance();
        //初始化：
        List<Long> allIds = shopMapper.findAllIds();
        for(Long id : allIds) {
            bloomFilter.add(id);
        }
        System.out.println(">>>>>>>>>>>>>>>执行完毕 <<<<<<<<<<<<<");

    }
}
