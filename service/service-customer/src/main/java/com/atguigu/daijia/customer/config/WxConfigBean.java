package com.atguigu.daijia.customer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Data
//关联到相应的配置
@ConfigurationProperties(prefix = "wx.miniapp")
public class WxConfigBean {
    private String appId;
    private String secret;
}
