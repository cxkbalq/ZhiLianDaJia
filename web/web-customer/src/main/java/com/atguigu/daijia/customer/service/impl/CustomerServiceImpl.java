package com.atguigu.daijia.customer.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.zdyException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.customer.client.CustomerInfoFeignClient;
import com.atguigu.daijia.customer.service.CustomerService;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class CustomerServiceImpl implements CustomerService {
    @Autowired
    private CustomerInfoFeignClient client;
    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public CustomerLoginVo getCustomerLoginInfo(String token){
        //获取customerId
        String customerId =(String) redisTemplate.opsForValue().get(RedisConstant.USER_LOGIN_KEY_PREFIX + token);
        //发起远程调用
        Result<CustomerLoginVo> customerLoginInfo = client.getCustomerLoginInfo(Long.parseLong(customerId));
        if (customerLoginInfo.getCode().intValue() != 200) {
            throw new zdyException(customerLoginInfo.getCode(), customerLoginInfo.getMessage());
        }
        if (null == customerLoginInfo.getData()) {
            throw new zdyException(ResultCodeEnum.DATA_ERROR);
        }

        return customerLoginInfo.getData();
    }


    /**
     * 登录
     * @param code
     * @return
     */

    @Override
    public String login(String code) {
        //发起远程调用，获取openid
        Result<Long> login = client.login(code);
        if (login.getCode().intValue() != 200) {
            throw new zdyException(login.getCode(), login.getMessage());
        }

        Long customerId = login.getData();
        if (null == customerId) {
            throw new zdyException(ResultCodeEnum.DATA_ERROR);
        }

        String token = UUID.randomUUID().toString().replaceAll("-", "");
        redisTemplate.opsForValue().set(RedisConstant.USER_LOGIN_KEY_PREFIX + token, customerId.toString(), 30, TimeUnit.MINUTES);
        return token;
    }
}
