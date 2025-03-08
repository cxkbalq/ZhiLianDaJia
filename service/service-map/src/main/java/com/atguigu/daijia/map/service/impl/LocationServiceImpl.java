package com.atguigu.daijia.map.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.common.execption.zdyException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.common.util.LocationUtil;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.map.service.LocationService;
import com.atguigu.daijia.model.entity.driver.DriverSet;
import com.atguigu.daijia.model.entity.map.OrderServiceLocation;
import com.atguigu.daijia.model.form.map.OrderServiceLocationForm;
import com.atguigu.daijia.model.form.map.SearchNearByDriverForm;
import com.atguigu.daijia.model.form.map.UpdateDriverLocationForm;
import com.atguigu.daijia.model.form.map.UpdateOrderLocationForm;
import com.atguigu.daijia.model.vo.map.NearByDriverVo;
import com.atguigu.daijia.model.vo.map.OrderLocationVo;
import com.atguigu.daijia.model.vo.map.OrderServiceLastLocationVo;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class LocationServiceImpl implements LocationService {
    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private DriverInfoFeignClient driverInfoFeignClient;

    @Autowired
    private MongoRepository mongoRepository;

    //mongoTemplater如何无法注入，一定是包导错了
    @Autowired
    private MongoTemplate mongoTemplate;


    /**
     * 更新司机坐标
     *
     * @param updateDriverLocationForm
     * @return
     */

    @Override
    public Boolean updateDriverLocation(UpdateDriverLocationForm updateDriverLocationForm) {
        Point point = new Point(updateDriverLocationForm.getLongitude().doubleValue(), updateDriverLocationForm.getLatitude().doubleValue());
        redisTemplate.opsForGeo().add(RedisConstant.DRIVER_GEO_LOCATION, point, updateDriverLocationForm.getDriverId().toString());
        return true;
    }

    /**
     * 删除司机坐标
     *
     * @param driverId
     * @return
     */

    @Override
    public Boolean removeDriverLocation(Long driverId) {
        redisTemplate.opsForGeo().remove(RedisConstant.DRIVER_GEO_LOCATION, driverId.toString());
        return true;
    }

    /**
     * 搜索可用司机
     *
     * @param searchNearByDriverForm
     * @return
     */
    @Override
    public List<NearByDriverVo> searchNearByDriver(SearchNearByDriverForm searchNearByDriverForm) {

        //构建经纬度
        Point point = new Point(searchNearByDriverForm.getLongitude().doubleValue(), searchNearByDriverForm.getLatitude().doubleValue());
        //定义距离：5公里(系统配置)  距离/单位
        Distance distance = new Distance(SystemConstant.NEARBY_DRIVER_RADIUS, RedisGeoCommands.DistanceUnit.KILOMETERS);
        Circle circle = new Circle(point, distance);

//        includeDistance()
//        该方法指示Redis返回每个查询到的位置的距离信息，即查询结果中的每个地理位置都会包含其距离中心点的距离。
//
//        includeCoordinates()
//        该方法指示Redis返回每个查询到的位置的坐标（经度和纬度）。这样，你将能获取到每个位置的经纬度信息。
//
//        sortAscending()
//        该方法指示Redis按照距离从近到远的升序排序返回结果。

        //定义GEO参数
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance() //包含距离
                .includeCoordinates() //包含坐标
                .sortAscending(); //排序：升序


        //搜索附近的司机
        GeoResults<RedisGeoCommands.GeoLocation<String>> result = redisTemplate.opsForGeo().
                radius(RedisConstant.DRIVER_GEO_LOCATION, circle, args);
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = result.getContent();

        //获得附近的司机，构建集合，发送请求，提高响应速度
        List<Long> driverId = new ArrayList<>();
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> item : content) {
            driverId.add(Long.parseLong(item.getContent().getName()));
            log.info("当前搜索到司机为id为：" + item.getContent().getName());
        }
        List<NearByDriverVo> list = new ArrayList();

        if (driverId == null) {
            return list;
        }
        //发起远程调用
        List<DriverSet> data = driverInfoFeignClient.getListDriverSet(driverId).getData();

        //返回计算后的信息
        if (data != null && data.size() == 0) {
            return list;
        }
        int flag = 0;
        //遍历司机，去除不符合的条件
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> item : content) {
            // 对每个 geoResult 进行处理
            RedisGeoCommands.GeoLocation<String> location = item.getContent();
            // 可以在这里访问 location 的信息，比如经纬度等
            //当前距离
            BigDecimal currentDistance = new BigDecimal(item.getDistance().getValue()).setScale(2, RoundingMode.HALF_UP);
            log.info("司机：{}，距离：{}", Long.parseLong(item.getContent().getName()), item.getDistance().getValue());

            //获取司机设置信息，
            DriverSet driverSet = getDriverSetById(Long.parseLong(item.getContent().getName()), data);
            //接单里程判断，acceptDistance==0：不限制，
            if (driverSet.getAcceptDistance().doubleValue() != 0 && driverSet.getAcceptDistance().subtract(currentDistance).doubleValue() < 0) {
                continue;
            }
            //订单里程判断，orderDistance==0：不限制
            if (driverSet.getOrderDistance().doubleValue() != 0 && driverSet.getOrderDistance().subtract(searchNearByDriverForm.getMileageDistance()).doubleValue() < 0) {
                continue;
            }

            //满足条件的附近司机信息
            NearByDriverVo nearByDriverVo = new NearByDriverVo();
            nearByDriverVo.setDriverId(Long.parseLong(item.getContent().getName()));
            nearByDriverVo.setDistance(currentDistance);
            list.add(nearByDriverVo);
        }

        return list;
    }

    /**
     * 更新司机实时位置
     *
     * @param updateOrderLocationForm
     * @return
     */
    @Override
    public Boolean updateOrderLocationToCache(UpdateOrderLocationForm updateOrderLocationForm) {
        OrderLocationVo orderLocationVo = new OrderLocationVo();
        orderLocationVo.setLongitude(updateOrderLocationForm.getLongitude());
        orderLocationVo.setLatitude(updateOrderLocationForm.getLatitude());
        //"update:order:location:"; UPDATE_ORDER_LOCATION
        redisTemplate.opsForValue().set(RedisConstant.UPDATE_ORDER_LOCATION + updateOrderLocationForm.getOrderId(), orderLocationVo);

        /**
         * // 立即获取刚设置的值,这里经过测试，如果没有缓存成功，重新登录就可以，
         * 而且没有缓存成功不影响接单，但是会使后面功能报错，只需重新登录就能缓存成功，暂时并不知道为什么
         */
        OrderLocationVo cachedVo = (OrderLocationVo) redisTemplate.opsForValue().get(RedisConstant.UPDATE_ORDER_LOCATION + updateOrderLocationForm.getOrderId());
        if(cachedVo==null){
            //抛异常，让其重新登录
            new zdyException(ResultCodeEnum.WX_CODE_ERROR);
        }
        return true;
    }

    /**
     * 获取订单位置
     *
     * @param orderId
     * @return
     */
    @Override
    public OrderLocationVo getCacheOrderLocation(Long orderId) {
        OrderLocationVo orderLocationVo = (OrderLocationVo) redisTemplate.opsForValue().get(RedisConstant.UPDATE_ORDER_LOCATION + orderId);
        return orderLocationVo;
    }

    /**
     * 批量保存代驾服务订单位置
     *
     * @param orderLocationServiceFormList
     * @return
     */
    @Override
    public Boolean saveOrderServiceLocation(List<OrderServiceLocationForm> orderLocationServiceFormList) {
        List<OrderServiceLocation> list = new ArrayList<>();
        orderLocationServiceFormList.forEach(OrderServiceLocationForm -> {
            OrderServiceLocation orderServiceLocation = new OrderServiceLocation();
            BeanUtils.copyProperties(OrderServiceLocationForm, orderServiceLocation);
            orderServiceLocation.setCreateTime(new Date());
            orderServiceLocation.setId(ObjectId.get().toString());
            list.add(orderServiceLocation);
        });
        //包含所有保存实体的 List<T>包括生成的id
        List list1 = mongoRepository.saveAll(list);
        if (list1.size() == list.size()) {
            return true;
        }
        throw new zdyException(201, "批量保存代驾服务订单位置失败");
    }

    /**
     * 代驾服务：获取订单服务最后一个位置信息
     *
     * @param orderId
     * @return
     */
    @Override
    public OrderServiceLastLocationVo getOrderServiceLastLocation(Long orderId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("orderId").is(orderId));
        query.with(Sort.by(Sort.Order.desc("createTime")));
        query.limit(1);
        OrderServiceLocation one = mongoTemplate.findOne(query, OrderServiceLocation.class);
        OrderServiceLastLocationVo orderServiceLastLocationVo = new OrderServiceLastLocationVo();
        BeanUtils.copyProperties(one, orderServiceLastLocationVo);
        return orderServiceLastLocationVo;
    }

    /**
     * 计算实际路程
     *
     * @param orderId
     * @return
     */
    @Override
    public BigDecimal calculateOrderRealDistance(Long orderId) {
        OrderServiceLocation orderServiceLocation = new OrderServiceLocation();
        orderServiceLocation.setOrderId(orderId);
        Example example = Example.of(orderServiceLocation);
        Sort sort = Sort.by(Sort.Direction.ASC, "createTime");
        List<OrderServiceLocation> all = mongoRepository.findAll(example, sort);
        //实际距离
        double realDistance = 0;
        if (all != null && all.size() != 0) {
            for (int i = 0; i < all.size() - 1; i++) {
                OrderServiceLocation location1 = all.get(i);
                OrderServiceLocation location2 = all.get(i + 1);

                //计算位置距离
                double distance = LocationUtil.getDistance(location1.getLatitude().doubleValue(),
                        location1.getLongitude().doubleValue(),
                        location2.getLatitude().doubleValue(),
                        location2.getLongitude().doubleValue());

                realDistance += distance;
            }
        }
        return new BigDecimal(realDistance);
    }


    //获取设置信息
    public DriverSet getDriverSetById(Long id, List<DriverSet> data) {
        // 遍历列表查找匹配的DriverSet对象
        for (DriverSet driverSet : data) {
            if (driverSet.getId().equals(id)) {
                return driverSet; // 返回匹配的DriverSet对象
            }
        }
        // 如果没有找到匹配的DriverSet，则返回null或其他默认值
        return null;
    }
}
