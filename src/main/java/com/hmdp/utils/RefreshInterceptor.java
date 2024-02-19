package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

//实现HandlerInterceptor
public class RefreshInterceptor implements HandlerInterceptor {
    //自己写的类没有纳入管理
    private StringRedisTemplate redisTemplate;

    public RefreshInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    //每一个请求都会来进行过滤
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 新的请求，可以根据cookie 的sessionID 获得对应的session
        // 获取token
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)) {
            return true;
        }
        //HttpSession session = request.getSession();
        //拿用户
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        //Object user =  session.getAttribute("user");
        //不存在的话直接拦截
        if(entries.isEmpty()) {
            return true;
        }
        //MAP转USERDTO对象
        //保存到ThreadLocal中
        UserDTO userDTO = BeanUtil.fillBeanWithMap(entries, new UserDTO(), false);

        //：刷新
        redisTemplate.expire(LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES );

        UserHolder.saveUser(userDTO);

        return true;


    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
       // 避免线程复用的泄漏问题
        UserHolder.removeUser();
    }
}
