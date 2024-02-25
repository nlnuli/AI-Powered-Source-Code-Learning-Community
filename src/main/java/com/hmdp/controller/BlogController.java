package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        blogService.save(blog);
        // 返回id
        return Result.ok(blog.getId());
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        // 修改点赞数量
        //updateByid只会自动修改根据id选出然后覆盖的值。
        boolean flag = blogService.likeBlog(id);
        if(flag) return Result.ok();
        else {
            return Result.fail("点赞操作数据库失败");
        }

    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            // 查询是否被点赞：
            UserDTO user1 = UserHolder.getUser();
            if(user1 == null) return ;
            Long userId1 = user1.getId();
            String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
            Boolean member = redisTemplate.opsForSet().isMember(key, userId1.toString());
            blog.setIsLike(member);
        });
        return Result.ok(records);
    }

    @GetMapping("/{id}")
    public Result queryBlog(@PathVariable("id") Long id) {
        Blog blog = blogService.queryBlog(id);
        return Result.ok(blog);

    }
    @GetMapping("/likes/{id}")
    public Result Likes(@PathVariable("id") Long id) {
        List<UserDTO> res = blogService.queryLikes(id);
        if(res == null) {
            return Result.ok(Collections.emptyList());
        }
        return Result.ok(res);
    }



}
