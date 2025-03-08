package com.atguigu.daijia.common.zhuJie;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.zdyException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.common.util.AuthContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect //声明是一个切片类
@Order(100)  // 通过 @Order 设置顺序，数字越小优先级越高,不设置会报错
@Component
//@Lazy
//@Order(Ordered.LOWEST_PRECEDENCE)
//@AutoConfigureBefore(SentinelAutoConfiguration.class)
//@Configuration
public class checkLoginAspect {

    @Autowired
    private RedisTemplate redisTemplate;
    //注意@annotation(checklogin)这个里面CheckLogin checklogin，要填入第二个，不然出现奇怪问题

    @Around("execution(* com.atguigu.daijia.*.controller.*.*(..)) && @annotation(checklogin)")
    public Object login(ProceedingJoinPoint joinPoint, CheckLogin checklogin) throws Throwable {
        //log.info("进入 checkLogin 切面");
        try {
            // 当前线程相关的请求上下文信息
            RequestAttributes ra = RequestContextHolder.getRequestAttributes();
            ServletRequestAttributes sra = (ServletRequestAttributes) ra;
            HttpServletRequest request = sra.getRequest();
            String token = request.getHeader("token");

            if (!StringUtils.hasText(token)) {
                throw new zdyException(ResultCodeEnum.LOGIN_AUTH);
            }
            //user:login
            String userId = (String) redisTemplate.opsForValue().get(RedisConstant.USER_LOGIN_KEY_PREFIX + token);
           // log.info("当前登录用户id：" + userId);
            if (StringUtils.hasText(userId)) {
                AuthContextHolder.setUserId(Long.parseLong(userId));
            }

            return joinPoint.proceed();
        } catch (Exception e) {
            log.error("出现错误", e);
            throw e;
        }
    }


}
