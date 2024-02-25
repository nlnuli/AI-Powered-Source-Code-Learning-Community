package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    /***
     * 当前用户查询Blog，并判断用户是否点过赞的逻辑
     * @param id blog的ID
     * @return
     */
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
        //需要去查一次Redis才能确定知不知道
        Double score = redisTemplate.opsForZSet().score(key, userId1.toString());
        if(score != null)
        blog.setIsLike(true);
        //true:
        else {
            blog.setIsLike(false);
        }

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
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());
        if(score == null) {
            //不在集合中，没有点赞：
            boolean update = update().setSql("liked = liked + 1").eq("id", id).update();
            if(!update) return false;
            redisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
        }
        else {
            //在集合中，取消点赞：
            boolean update = update().setSql("liked = liked - 1").eq("id", id).update();
            if(!update) return false;
            redisTemplate.opsForSet().remove(key, userId.toString());

        }
        //没有操作Blog的isLiked字段，只是在set中添加了，并且加一！

        return true;
    }

    /***
     * 查询top5的用户
     * @param id 博客ID
     * @return
     */

    @Override
    public List<UserDTO> queryLikes(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Set<String> range = redisTemplate.opsForZSet().range(key, 0, 4);
        if(range == null || range.isEmpty()) {
            return null;
        }
        List<Long> ids = range.stream().map(Long::valueOf).collect(Collectors.toList());
        List<User> users = userService.listByIds(ids);
        //进行用户的敏感信息过滤：
        List<UserDTO> userDTOS= users.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return userDTOS;



    }
}
