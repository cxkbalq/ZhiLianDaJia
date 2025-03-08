package com.atguigu.daijia.driver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolConfig {
    @Bean
    public ThreadPoolExecutor threadPoolExecutor() {
        // 动态获取服务器的CPU核心数，用于合理配置线程池参数
        int processors = Runtime.getRuntime().availableProcessors();

        // 创建自定义线程池（以下参数需根据具体业务场景调整）
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                // 核心线程数 = CPU核心数 + 1
                // - 适用于CPU密集型任务（公式：n+1，n为CPU核心数）
                // - 如果是IO密集型任务，建议设置为 2*n（例如：processors * 2）
                processors + 1,

                // 最大线程数 = 核心线程数（此处与核心线程数相同）
                // - 此配置下线程池不会扩容，队列满后直接触发拒绝策略
                // - 若需处理突发流量，可设置为更大的值（如 processors * 2）
                processors + 1,

                // 非核心线程的空闲存活时间（单位：秒）
                // - 若允许线程数超过核心线程数，建议设置为60~120秒
                60,
                TimeUnit.SECONDS,

                // 任务队列：使用有界队列（容量3）
                // - 队列容量较小，容易触发拒绝策略
                // - 建议根据业务吞吐量设置合理容量（例如100~1000）
                new ArrayBlockingQueue<>(3),

                // 线程工厂：使用默认工厂（生成的线程名为pool-N-thread-M格式）
                // - 建议自定义线程工厂，添加业务标识前缀，便于日志排查
                Executors.defaultThreadFactory(),

                // 拒绝策略：直接抛出RejectedExecutionException
                // - 高并发场景下可能导致任务丢失
                // - 建议根据业务需求选择：
                //   - CallerRunsPolicy：由提交任务的线程执行该任务（避免丢失）
                //   - 自定义策略（如记录日志后降级处理）
                new ThreadPoolExecutor.AbortPolicy()
        );

        // 返回线程池实例
        return threadPoolExecutor;
    }

}
