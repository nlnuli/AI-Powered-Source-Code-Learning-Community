package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    private UserServiceImpl userService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Override
    public Blog queryBlog(Long id) {
        //每一个用户查询的时候都会生成blog对象
        //查询笔记：
        Blog blog = getById(id);
        if(blog == null) return null;
        Long userId = blog.getUserId();
        //根据ID进行查询：
        User user = userService.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
        //要在这里根据这个set去查看是否点赞,对每一个用户看是否点赞！
        UserDTO user1 = UserHolder.getUser();
        Long userId1 = user1.getId();
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Boolean member = redisTemplate.opsForSet().isMember(key, userId1.toString());
        blog.setIsLike(member);
        //true:

        return blog;

    }

    /***
     *  判断是否点过赞的逻辑
     * @param id Blog 的ID
     * @return
     */
    @Override
    public boolean likeBlog(Long id) {
        //set集合去进行判断：维护每个Blog的点赞列表！！
        //完成判断当前用户是否点过赞的功能
        //
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        //判断是否点过赞：判断是否在其中出现
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Boolean member = redisTemplate.opsForSet().isMember(key, userId.toString());
        if(BooleanUtil.isFalse(member)) {
            //不在集合中，没有点赞：
            boolean update = update().setSql("liked = liked + 1").eq("id", id).update();
            if(!update) return false;
            redisTemplate.opsForSet().add(key, userId.toString());
        }
        else {
            //在集合中，取消点赞：
            boolean update = update().setSql("liked = liked - 1").eq("id", id).update();
            if(!update) return false;
            redisTemplate.opsForSet().remove(key, userId.toString());

        }

        return true;
    }
}
