package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import io.netty.util.internal.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate redisTemplate;

    // 保存一份！！！！
//    @Override
//    public Result queryById(Long id) {
//        //1. Redis 查询缓存
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        String val = redisTemplate.opsForValue().get(key);
//        //命中直接返回
//        if(StringUtils.isNotBlank(val)) {
//            //转换：
//            Shop shop = JSONUtil.toBean(val, Shop.class);
//            return Result.ok(shop);
//        }
//        if(val != null) {
//            return Result.fail("店铺不存在");
//        }
//        //不命中需要查询数据库：
//        Shop shop = getById(id);
//        if(shop == null) {
//            //不存在返回404
//            redisTemplate.opsForValue().set(key,"", 2L, TimeUnit.MINUTES);
//            return Result.fail("店铺不存在");
//        }
//        //存在：
//        val = JSONUtil.toJsonStr(shop);
//        //写会Redis中，然后返回商铺信息
//        redisTemplate.opsForValue().set(key, val, 30L, TimeUnit.MINUTES);
//        return Result.ok(shop);
//    }

    public Shop queryWithPassThrough(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String val = redisTemplate.opsForValue().get(key);
        //命中直接返回
        if(StringUtils.isNotBlank(val)) {
            //转换：
            Shop shop = JSONUtil.toBean(val, Shop.class);
            return shop;
        }
        if(val != null) {
            return null;
        }
        //不命中需要查询数据库：
        Shop shop = getById(id);
        if(shop == null) {
            //不存在返回404
            redisTemplate.opsForValue().set(key,"", 2L, TimeUnit.MINUTES);
            return null;
        }
        //存在：
        val = JSONUtil.toJsonStr(shop);
        //写会Redis中，然后返回商铺信息
        redisTemplate.opsForValue().set(key, val, 30L, TimeUnit.MINUTES);
        return shop;
    }

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
//       Shop shop = queryWithPassThrough(id);
//       if(shop == null) {
//           return Result.fail("店铺不存在");
//       }
//       return  Result.ok(shop);
//        Shop shop = queryWithMutexLock(id);
        Shop shop = queryLogicExpire(id);
        if(shop == null) {
           return Result.fail("店铺不存在");
       }
       return  Result.ok(shop);


    }



    public Shop queryWithMutexLock(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String val = redisTemplate.opsForValue().get(key);
        //命中直接返回
        if(StringUtils.isNotBlank(val)) {
            //转换：
            Shop shop = JSONUtil.toBean(val, Shop.class);
            return shop;
        }
        if(val != null) {
            return null;
        }
        //首先获取锁：
        Shop shop = null;
        try {
            if(! getLock(id)) {
                Thread.sleep(50);
                return queryWithMutexLock(id);
            }
            //不命中需要查询数据库：
            //发生的条件：1. 热点事件会有高并发同时访问   2。 重建情况比较久
            shop = getById(id);
            //模拟重建的延迟！！
            Thread.sleep(200);
            if(shop == null) {
                //不存在返回404
                redisTemplate.opsForValue().set(key,"", 2L, TimeUnit.MINUTES);
                return null;
            }
            //存在：
            val = JSONUtil.toJsonStr(shop);
            //写会Redis中，然后返回商铺信息
            redisTemplate.opsForValue().set(key, val, 30L, TimeUnit.MINUTES);

        }catch (Exception e) {
            throw new RuntimeException(e);

        }finally {
            unlock(id);
        }
        return shop;
    }


    //创建线程池然后再来处理：
    private static final ExecutorService pool = Executors.newFixedThreadPool(10);



    //只能查询出热点数据
    public Shop queryLogicExpire(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String val = redisTemplate.opsForValue().get(key);
        //未命中的话需要进行普通的更新操作-------》1。查询数据库，然后同步到redis中返回
        if(StringUtils.isBlank(val)) {
            //转换：
            return null;
        }
        if(val == null) {
            return null;
        }
        //命中的话：
        /**
         * 1. 查询是否过期：不过期直接返回
         * 2  过期的话需要刷新
         * 3  获取锁
         * 4。 没有获取到直接返回
         * 5。 获取到了直接返回然后
         * 5。 获取到了创建新线程进行缓存的重建
         *
         *
         */
        //1。 是否过期
        //对象的序列话
        RedisData redisData = JSONUtil.toBean(val, RedisData.class);
        LocalDateTime time = redisData.getExpireTime();
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);

        if(time.isAfter(LocalDateTime.now())){
            //没有过期：
            //还是返回的时shop信息
            return shop;
        }
        //过期：
        boolean lock = getLock(id);
        if(!lock) {
            //没有获取到锁：
            return shop;
        }
        //获取到了锁：
        //开启线程然后处理：
        pool.submit(()->{
            try {
                this.saveShop2Redis(id, 20L);

            }catch (Exception e) {
                throw  new RuntimeException(e);
            }finally {
                //释放锁！！！！！
                unlock(id);
            }


        });

        //返回结果：
        return shop;




        //不命中需要查询数据库：

    }







    //互斥锁来解决缓存
    //锁：
    public boolean getLock(Long id) {
        //保证每次只有一个可以往redis插入值，能够获得锁！！！！
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(RedisConstants.LOCK_SHOP_KEY + id, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);

    }
    public void unlock(Long id ) {
        redisTemplate.delete(RedisConstants.LOCK_SHOP_KEY + id);
    }

    //缓存预热
    public void saveShop2Redis(Long id, Long seconds) throws InterruptedException {
        Shop shop = getById(id);
        //下次开启了一个新线程然后在重建过程中因为没有释放锁，或有新的请求，来的时候因为没有锁就直接返回了旧的信息！！
        Thread.sleep(200);
        //封装过期时间：
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(seconds) );
        //写入redis中：
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String val = JSONUtil.toJsonStr(redisData);
        //永远不会过期！！！
        redisTemplate.opsForValue().set(key, val);


    }


    //保证原子性操作：
    //就通过更新来保证redis和数据库中的数据一致性！！！！！
    @Override
    @Transactional
    public Result update(Shop shop) {
        //先更新再删除：
        Long id = shop.getId();
        if(id == null) {
            return Result.fail("ID不能为空");
        }
        //更新：
        updateById(shop);
        //删除缓存：
        redisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();

    }
}
