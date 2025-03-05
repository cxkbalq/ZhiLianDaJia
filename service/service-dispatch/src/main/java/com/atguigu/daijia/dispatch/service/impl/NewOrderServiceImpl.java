package com.atguigu.daijia.dispatch.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.dispatch.mapper.OrderJobMapper;
import com.atguigu.daijia.dispatch.service.NewOrderService;
import com.atguigu.daijia.dispatch.xxlJob.XxlJobClient;
import com.atguigu.daijia.map.client.LocationFeignClient;
import com.atguigu.daijia.model.entity.dispatch.OrderJob;
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.form.map.SearchNearByDriverForm;
import com.atguigu.daijia.model.vo.dispatch.NewOrderTaskVo;
import com.atguigu.daijia.model.vo.map.NearByDriverVo;
import com.atguigu.daijia.model.vo.order.NewOrderDataVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class NewOrderServiceImpl implements NewOrderService {
    @Autowired
    private OrderJobMapper orderJobMapper;
    @Autowired
    private XxlJobClient xxlJobClient;
    @Autowired
    private OrderInfoFeignClient orderInfoFeignClient;
    @Autowired
    private LocationFeignClient locationFeignClient;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 开启任务调度
     *
     * @param newOrderTaskVo
     * @return
     */
    @Override
    public Long addAndStartTask(NewOrderTaskVo newOrderTaskVo) {
        log.info( "开启任务调度addAndStartTask:"+newOrderTaskVo.toString());
        //查询现在是否存在订单
        LambdaQueryWrapper<OrderJob> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(OrderJob::getOrderId, newOrderTaskVo.getOrderId());
        OrderJob orderJob = orderJobMapper.selectOne(lambdaQueryWrapper);
        Long id=0l;
        //如果订单为空创建
        if (orderJob == null) {
            OrderJob orderJob1 =new OrderJob();
            //参数解释 bean名字， ，执行间隔时间，备注
            Long jobId = xxlJobClient.
                    addAndStart("newOrderTaskHandler", "", "0 0/1 * * * ?", "新订单任务,订单id：" + newOrderTaskVo.getOrderId());
            orderJob1.setOrderId(newOrderTaskVo.getOrderId());
            log.info(jobId.toString());
            orderJob1.setJobId(jobId);
            orderJob1.setParameter(JSONObject.toJSONString(newOrderTaskVo));
            orderJobMapper.insert(orderJob1);
            id=orderJob1.getJobId();
        }

        return id;
    }

    /**
     * 任务调度开始的方法，每隔一分钟搜索附近的司机
     *
     * @param jobId
     * @return
     */
    @Override
    public Boolean executeTask(long jobId) {
        //获取任务参数
        OrderJob orderJob = orderJobMapper.selectOne(new LambdaQueryWrapper<OrderJob>().eq(OrderJob::getJobId, jobId));
        //没有直接结束
        if (null == orderJob) {
            log.info("任务参数为空！");
            return true;
        }
        NewOrderTaskVo newOrderTaskVo = JSONObject.parseObject(orderJob.getParameter(), NewOrderTaskVo.class);
        //查询订单状态，如果该订单还在接单状态，继续执行；如果不在接单状态，则停止定时调度\
        Integer orderStatus = orderInfoFeignClient.getOrderStatus(newOrderTaskVo.getOrderId()).getData();
        //WAITING_ACCEPT(1, "等待接单"),如果不是
        if (orderStatus.intValue() != OrderStatus.WAITING_ACCEPT.getStatus().intValue()) {
            //停止任务
            xxlJobClient.stopJob(jobId);
            log.info("停止任务调度: {}", JSON.toJSONString(newOrderTaskVo));
            return true;
        }
        //搜索附近满足条件的司机
        SearchNearByDriverForm searchNearByDriverForm = new SearchNearByDriverForm();
        searchNearByDriverForm.setLongitude(newOrderTaskVo.getStartPointLongitude());
        searchNearByDriverForm.setLatitude(newOrderTaskVo.getStartPointLatitude());
        searchNearByDriverForm.setMileageDistance(newOrderTaskVo.getExpectDistance());
        List<NearByDriverVo> nearByDriverVoList = locationFeignClient.searchNearByDriver(searchNearByDriverForm).getData();
        //遍历集合，添加到司机的临时队列里
        for (NearByDriverVo driver : nearByDriverVoList) {
            //记录司机id，防止重复推送订单信息   driver:order:repeat:list:   DRIVER_ORDER_REPEAT_LIST
            String repeatKey = RedisConstant.DRIVER_ORDER_REPEAT_LIST + newOrderTaskVo.getOrderId();
            boolean isMember = redisTemplate.opsForSet().isMember(repeatKey, driver.getDriverId());
            //如果value存在于key所对应的Redis集合中，返回true；否则返回false。
            if (!isMember) {
                log.info("当前司机没有推送：");
                //记录该订单已放入司机临时容器
                redisTemplate.opsForSet().add(repeatKey, driver.getDriverId());
                //过期时间：15分钟，新订单15分钟没人接单自动取消    DRIVER_ORDER_REPEAT_LIST_EXPIRES_TIME  16,过期时间
                redisTemplate.expire(repeatKey, RedisConstant.DRIVER_ORDER_REPEAT_LIST_EXPIRES_TIME, TimeUnit.MINUTES);

                NewOrderDataVo newOrderDataVo = new NewOrderDataVo();
                newOrderDataVo.setOrderId(newOrderTaskVo.getOrderId());
                newOrderDataVo.setStartLocation(newOrderTaskVo.getStartLocation());
                newOrderDataVo.setEndLocation(newOrderTaskVo.getEndLocation());
                newOrderDataVo.setExpectAmount(newOrderTaskVo.getExpectAmount());
                newOrderDataVo.setExpectDistance(newOrderTaskVo.getExpectDistance());
                newOrderDataVo.setExpectTime(newOrderTaskVo.getExpectTime());
                newOrderDataVo.setFavourFee(newOrderTaskVo.getFavourFee());
                newOrderDataVo.setDistance(driver.getDistance());
                newOrderDataVo.setCreateTime(newOrderTaskVo.getCreateTime());

                //将消息保存到司机的临时队列里面，司机接单了会定时轮询到他的临时队列获取订单消息
                //  DRIVER_ORDER_TEMP_LIST =driver:order:temp:list:
                String key = RedisConstant.DRIVER_ORDER_TEMP_LIST + driver.getDriverId();
                redisTemplate.opsForList().leftPush(key, JSONObject.toJSONString(newOrderDataVo));
                //过期时间：1分钟，1分钟未消费，自动过期
                //注：司机端开启接单，前端每5秒（远小于1分钟）拉取1次“司机临时队列”里面的新订单消息
                redisTemplate.expire(key, RedisConstant.DRIVER_ORDER_TEMP_LIST_EXPIRES_TIME, TimeUnit.MINUTES);
                log.info("该新订单信息已放入司机临时队列: {}", JSON.toJSONString(newOrderDataVo));
            }
            log.info("当前以及推送啦！");
        }

        return true;
    }

    /**
     * 司机端查看系新的订单先看新的在看旧的
     *
     * @param driverId
     * @return
     */
    @Override
    public List<NewOrderDataVo> findNewOrderQueueData(Long driverId) {
        List<NewOrderDataVo> list = new ArrayList<>();
        String key = RedisConstant.DRIVER_ORDER_TEMP_LIST + driverId;
        long size = redisTemplate.opsForList().size(key);
        log.info("当前司机订单数量："+String.valueOf(size));
        if (size > 0) {
            //遍历集合，安装顺序添加
            for (int i = 0; i < size; i++) {
                String content = (String) redisTemplate.opsForList().leftPop(key);
                NewOrderDataVo newOrderDataVo = JSONObject.parseObject(content, NewOrderDataVo.class);
                list.add(newOrderDataVo);
            }
        }
        return list;
    }

    /**
     * 清理订单（下线的情况）
     * @param driverId
     * @return
     */

    @Override
    public Boolean clearNewOrderQueueData(Long driverId) {
        String key = RedisConstant.DRIVER_ORDER_TEMP_LIST + driverId;
        //直接删除，司机开启服务后，有新订单会自动创建容器
        redisTemplate.delete(key);
        return true;
    }
}
