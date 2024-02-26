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
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
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
        //登陆的时候存储到redis中
        String tokenKey = LOGIN_USER_KEY + token;
        redisTemplate.opsForHash().putAll(tokenKey,stringObjectMap);
        redisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        //session.setAttribute("user", u);
        //  返回给客户端
        return Result.ok(token);
    }


    /***
     * 新增用户密码注册窗口
     * @param employee
     * @return
     */
    @PostMapping("register")
    public Result save( @RequestBody User employee) {
        log.info("新增员工，员工信息：{}", employee.toString());
        //传过来的时候可以自动封装的
        //使用了MD5来进行加载
        employee.setPassword(DigestUtils.md5DigestAsHex(employee.getPassword().getBytes()));
        userService.save(employee);
        return Result.ok("yes");
    }

    @PostMapping("/login2")
    public Result login( @RequestBody User employee){

        //1、将页面提交的密码password进行md5加密处理
        String password = employee.getPassword();
        password = DigestUtils.md5DigestAsHex(password.getBytes());

        //2、根据页面提交的用户名username查询数据库

        //查询数据库：
        LambdaQueryWrapper<User> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(User::getPhone, employee.getPhone());
        User user = userService.getOne(lambdaQueryWrapper);


        //3、如果没有查询到则返回登录失败结果
        if(user == null){
            return Result.fail("失败");
        }

        //4、密码比对，如果不一致则返回登录失败结果
        if(!user.getPassword().equals(password)){
            return Result.fail("失败");
        }

        //登陆成功，需要存入到redis中：
        // 随机生成Token   给客户端的
        String token = UUID.randomUUID().toString(true);
        //将User转为Hash存储
        UserDTO u = new UserDTO();
        BeanUtils.copyProperties(user,u);
        Map<String, String> stringObjectMap = new HashMap<>();
        stringObjectMap.put("id",String.valueOf(u.getId()));
        stringObjectMap.put("nickName",u.getNickName());
        stringObjectMap.put("icon",u.getIcon());
        //登陆的时候存储到redis中
        String tokenKey = LOGIN_USER_KEY + token;
        redisTemplate.opsForHash().putAll(tokenKey,stringObjectMap);
        redisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        //session.setAttribute("user", u);
        //  返回给客户端
        return Result.ok(token);
        //相当于给客户端返回了token，前端存储这个token，然后每一次请求都会携带这个token


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

    /***
     * 用户签到功能
     * @return
     */
    @GetMapping("/sign")
    public Result sign() {
        Long id = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + id + keySuffix;
        int dayOfMonth = now.getDayOfMonth();
        redisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }
    @GetMapping("/count")
    public Result signCount() {
        Long id = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + id + keySuffix;
        int dayOfMonth = now.getDayOfMonth();
        List<Long> result = redisTemplate.opsForValue().bitField(
                key, BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if(result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        //获得对应的数
        Long num = result.get(0);
        //进行位运算
        int count = 0;
        while(true) {
            if((num & 1) == 0) {
                break;
            }
            else {
                count ++;
            }
            num = num >>> 1;
        }
        return Result.ok(count);



    }
}

