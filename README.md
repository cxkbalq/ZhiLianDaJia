# 项目概述（智联代驾）

## 1 背景和功能

* 这个是一个，微服务架构的，微信小程序的代驾项目，以b站乐享代驾为基础，进行开发和修改，对原有代码进行了修改以提高并发量，完全适应本地运行，解决了原文档里错误的代码，供学习和交流

* 采用微信小程序方式

## 2 技术概述

- **SpringBoot**：简化Spring应用的初始搭建以及开发过程
- **SpringCloud**：基于Spring Boot实现的云原生应用开发工具，SpringCloud使用的技术：（Spring Cloud Gateway、Spring Cloud Task和Spring Cloud Feign等）
- **SpringBoot+SpringCloudAlibaba(Nacos，Sentinel) + OpenFeign + Gateway**
- MyBatis-Plus：持久层框架，也依赖mybatis
- Redis：内存做缓存
- Redisson：基于redis的Java驻内存数据网格 - 框架；操作redis的框架
- MongoDB: 分布式文件存储的数据库
- RabbitMQ：消息中间件；大型分布式项目是标配；分布式事务最终一致性
- Seata：分布式事务
- Drools：规则引擎，计算预估费用、取消费用等等
- GEO：GPS分区定位计算
- ThreadPoolExecutor+CompletableFuture：异步编排，线程池来实现异步操作，提高效率
- XXL-JOB: 分布式定时任务调用中心
- MinIO（私有化对象存储集群）：分布式文件存储 类似于OSS（公有）
- MySQL：关系型数据库 {shardingSphere-jdbc 进行读写分离; 分库，分表}
- Docker：容器化技术;  生产环境Redis（运维人员）；快速搭建环境Docker run

## 3 项目流程

项目演示视频：

https://www.bilibili.com/video/BV1VkQtYzEro/

状态图：

![](https://i.postimg.cc/D0pj5n3w/jyshare-markmap-1.png)
