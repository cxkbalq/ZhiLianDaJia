package com.atguigu.daijia.common.zhuJie;

import java.lang.annotation.*;

@Documented //使用该注解的地方会被工具（如 javadoc）生成 API 文档时包
@Retention(RetentionPolicy.RUNTIME)  //什么情况有效果
@Target(ElementType.METHOD)  //对什么生效，方法
public @interface checkLogin {

}
