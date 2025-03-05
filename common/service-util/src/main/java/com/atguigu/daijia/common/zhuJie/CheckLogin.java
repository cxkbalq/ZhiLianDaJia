package com.atguigu.daijia.common.zhuJie;

import java.lang.annotation.*;

@Documented //使用该注解的地方会被工具（如 javadoc）生成 API 文档时包
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckLogin {

}
