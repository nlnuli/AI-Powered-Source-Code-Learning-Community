package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import io.netty.util.internal.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    public Result queryById(Long id) {
        //1. Redis 查询缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String val = redisTemplate.opsForValue().get(key);
        //命中直接返回
        if(StringUtils.isNotBlank(val)) {
            //转换：
            Shop shop = JSONUtil.toBean(val, Shop.class);
            return Result.ok(shop);
        }
        if(val != null) {
            return Result.fail("店铺不存在");
        }
        //不命中需要查询数据库：
        Shop shop = getById(id);
        if(shop == null) {
            //不存在返回404
            redisTemplate.opsForValue().set(key,"", 2L, TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }
        //存在：
        val = JSONUtil.toJsonStr(shop);
        //写会Redis中，然后返回商铺信息
        redisTemplate.opsForValue().set(key, val, 30L, TimeUnit.MINUTES);
        return Result.ok(shop);
    }

    //保证原子性操作：
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
