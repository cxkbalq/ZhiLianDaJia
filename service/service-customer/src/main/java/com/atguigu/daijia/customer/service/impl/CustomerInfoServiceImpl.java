package com.atguigu.daijia.customer.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.atguigu.daijia.common.execption.zdyException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.customer.mapper.CustomerInfoMapper;
import com.atguigu.daijia.customer.service.CustomerInfoService;
import com.atguigu.daijia.customer.service.CustomerLoginLogService;
import com.atguigu.daijia.model.entity.customer.CustomerInfo;
import com.atguigu.daijia.model.entity.customer.CustomerLoginLog;
import com.atguigu.daijia.model.vo.customer.CustomerLoginVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class CustomerInfoServiceImpl extends ServiceImpl<CustomerInfoMapper, CustomerInfo> implements CustomerInfoService {
    @Autowired
    private WxMaService wxMaService;
    @Autowired
    private CustomerLoginLogService customerLoginLogService;

    /**
     * 获取用户登录信息
     * @param customerId
     * @return
     */
    @Override
    public CustomerLoginVo getCustomerLoginInfo(Long customerId){
        //查询数据，获取数据
        CustomerInfo customerInfo = this.getById(customerId);
        CustomerLoginVo customerLoginVo =new CustomerLoginVo();
        BeanUtils.copyProperties(customerInfo,customerLoginVo);

        //如果没有绑定手机号,发起绑定
        boolean hasText = StringUtils.hasText(customerInfo.getPhone());
        customerLoginVo.setIsBindPhone(hasText);
        return customerLoginVo;
    }

    /**
     * 获取用户openid
     * @param customerId
     * @return
     */
    @Override
    public String getCustomerOpenId(Long customerId) {
        return null;
    }

    /**
     * 登录获取唯一openid
     * @param code
     * @param ip
     * @return
     */
    @Override
    public Long login(String code,String ip) {
        String openId = null;
        //获得唯一openid
        try {
            WxMaJscode2SessionResult wxMaJscode2SessionResult = wxMaService.getUserService().getSessionInfo(code);
            openId = wxMaJscode2SessionResult.getOpenid();
            log.info("当前登录微信id为: " + openId);
        } catch (WxErrorException e) {
            e.printStackTrace();
            throw new zdyException(ResultCodeEnum.SERVICE_ERROR);
        }
        //查询数据库，检验登录
        LambdaQueryWrapper<CustomerInfo> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(CustomerInfo::getWxOpenId, openId);
        CustomerInfo customerInfo = this.getOne(lambdaQueryWrapper);
        //不存在账号
        if (customerInfo == null) {
            customerInfo = new CustomerInfo();
            customerInfo.setNickname(String.valueOf(System.currentTimeMillis()));
            customerInfo.setAvatarUrl("https://oss.aliyuncs.com/aliyun_id_photo_bucket/default_handsome.jpg");
            customerInfo.setWxOpenId(openId);
            this.save(customerInfo);
        }
        //登录日志
        CustomerLoginLog customerLoginLog = new CustomerLoginLog();
        customerLoginLog.setCustomerId(customerInfo.getId());
        customerLoginLog.setMsg("小程序登录");
        customerLoginLog.setIpaddr(ip);
        customerLoginLogService.save(customerLoginLog);
        return customerInfo.getId();
    }
}
