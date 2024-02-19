package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Autowired
    private StringRedisTemplate redisTemplate;



    /**
     * 发送手机验证码
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        //验证手机号
        boolean flag = RegexUtils.isPhoneInvalid(phone);
        if(flag) {
            //不行， 滚
            return Result.fail("手机验证出错");
        }
        //可以：生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存代session中用于验证，因为马上就要验证了，所以用session1

        //存进Redis中
        redisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //session.setAttribute("code", code);
        //发送验证码
        log.debug("发送成功，手机号是:{}", code);
        return Result.ok();
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        //首先自动根据注解完成了json到对象的转换
        //1. 校验是否正确
        boolean flag = RegexUtils.isPhoneInvalid(loginForm.getPhone());
        if(flag) {
            //不行， 滚
            return Result.fail("手机号验证出错");
        }
        //1. 验证验证码
        String code = loginForm.getCode();
        String phone = loginForm.getPhone();
        //默认时object类型的
        //String bufferCode = (String) session.getAttribute("code");
        String bufferCode = (String) redisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if(code == null || !code.equals(bufferCode)) {
            return Result.fail("手机验证出错");
        }
        //2. 验证成功：根据手机号查询用户
        LambdaQueryWrapper<User> userLambdaQueryWrapper = new LambdaQueryWrapper<>();
        userLambdaQueryWrapper.eq(User::getPhone, loginForm.getPhone());
        User user = userService.getOne(userLambdaQueryWrapper);
        if(user == null) {
            //注册：
            User newUser = new User();
            newUser.setPhone(loginForm.getPhone());
            newUser.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
            //保存直接调用service.save方法
            userService.save(newUser);
            user = newUser;
        }
        //3. 保存用户信息到session中
        //保存用户信息到Redis中
        // 随机生成Token
        String token = UUID.randomUUID().toString(true);
        //将User转为Hash存储
        UserDTO u = new UserDTO();
        BeanUtils.copyProperties(user,u);
        Map<String, String> stringObjectMap = new HashMap<>();
        stringObjectMap.put("id",String.valueOf(u.getId()));
        stringObjectMap.put("nickName",u.getNickName());
        stringObjectMap.put("icon",u.getIcon());

        String tokenKey = LOGIN_USER_KEY + token;
        redisTemplate.opsForHash().putAll(tokenKey,stringObjectMap);
        redisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        //session.setAttribute("user", u);
        //  返回给客户端
        return Result.ok(token);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        // TODO 实现登出功能
        return Result.fail("功能未完成");
    }

    @GetMapping("/me")
    public Result me(){
        // TODO 获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }
}
