package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.zdyException;
import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.dispatch.client.NewOrderFeignClient;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.driver.service.DriverService;
import com.atguigu.daijia.map.client.LocationFeignClient;
import com.atguigu.daijia.model.form.driver.DriverFaceModelForm;
import com.atguigu.daijia.model.form.driver.UpdateDriverAuthInfoForm;
import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class DriverServiceImpl implements DriverService {
    @Autowired
    private DriverInfoFeignClient driverInfoFeignClient;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private LocationFeignClient locationFeignClient;
    @Autowired
    private NewOrderFeignClient newOrderDispatchFeignClient;

    /**
     * 司机端登录
     *
     * @param code
     * @return
     */
    @Override
    @SneakyThrows //@SneakyThrows 使得方法可以抛出 checked exceptions，而不需要显示地用 throws 声明或捕获它们。
    public String login(String code) {
        //发起远程调用
        Result<Long> login = driverInfoFeignClient.login(code);
        Long id = login.getData();
        if (login.getCode().intValue() != 200 && id != null) {
            throw new zdyException(ResultCodeEnum.FAIL);
        }
        //生成token
        String token = UUID.randomUUID().toString().replaceAll("-", "");
        //储存在redis
        redisTemplate.opsForValue().set(RedisConstant.USER_LOGIN_KEY_PREFIX + token, id.toString(), 30, TimeUnit.MINUTES);
        return token;
    }

    /**
     * 获取司机的个人信息
     *
     * @param driverId
     * @return
     */
    @Override
    public DriverLoginVo getDriverLoginInfo(Long driverId) {

        Result<DriverLoginVo> driverLoginInfo = driverInfoFeignClient.getDriverLoginInfo(driverId);
        DriverLoginVo data = driverLoginInfo.getData();
        if (driverLoginInfo.getCode().intValue() != 200 && data != null) {
            throw new zdyException(ResultCodeEnum.FAIL);
        }
        return data;
    }

    /**
     * 获取司机认证信息
     *
     * @param driverId
     * @return
     */

    @Override
    public DriverAuthInfoVo getDriverAuthInfo(Long driverId) {
        Result<DriverAuthInfoVo> driverAuthInfo = driverInfoFeignClient.getDriverAuthInfo(driverId);
        DriverAuthInfoVo data = driverAuthInfo.getData();
        if (driverAuthInfo.getCode().intValue() != 200 && data != null) {
            throw new zdyException(ResultCodeEnum.FAIL);
        }
        return data;
    }

    /**
     * 更新司机认证信息
     *
     * @param updateDriverAuthInfoForm
     * @return
     */
    @Override
    public Boolean updateDriverAuthInfo(UpdateDriverAuthInfoForm updateDriverAuthInfoForm) {
        Result<Boolean> booleanResult = driverInfoFeignClient.UpdateDriverAuthInfo(updateDriverAuthInfoForm);
        if (booleanResult.getCode().intValue() != 200 && booleanResult.getData() != null) {
            throw new zdyException(ResultCodeEnum.FAIL);
        }
        return booleanResult.getData();
    }

    /**
     * 创建司机人脸模型
     *
     * @param driverFaceModelForm
     * @return
     */
    @Override
    public Boolean creatDriverFaceModel(DriverFaceModelForm driverFaceModelForm) {
        Result<Boolean> booleanResult = driverInfoFeignClient.creatDriverFaceModel(driverFaceModelForm);
        if (booleanResult.getCode().intValue() != 200 && booleanResult.getData() != null) {
            throw new zdyException(ResultCodeEnum.FAIL);
        }
        return booleanResult.getData();
    }

    /**
     * 验证当天是否登录
     *
     * @param driverId
     * @return
     */
    @Override
    public Boolean isFaceRecognition(Long driverId) {
        return driverInfoFeignClient.isFaceRecognition(driverId).getData();
    }

    /**
     * 检测人脸
     *
     * @param driverFaceModelForm
     * @return
     */

    @Override
    public Boolean verifyDriverFace(DriverFaceModelForm driverFaceModelForm) {
        return driverInfoFeignClient.verifyDriverFace(driverFaceModelForm).getData();
    }

    /***
     *开始接单
     * @param driverId
     * @return
     */

    @Override
    public Boolean startService(Long driverId) {
        //判断认证状态
        DriverLoginVo driverLoginVo = driverInfoFeignClient.getDriverLoginInfo(driverId).getData();
        if (driverLoginVo.getAuthStatus().intValue() != 2) {
            throw new zdyException(ResultCodeEnum.AUTH_ERROR);
        }

        //判断当日是否人脸识别
        Boolean isFaceRecognition = driverInfoFeignClient.isFaceRecognition(driverId).getData();
        if (!isFaceRecognition) {
            throw new zdyException(ResultCodeEnum.FACE_ERROR);
        }

        //更新司机接单状态
        driverInfoFeignClient.updateServiceStatus(driverId, 1);

        //删除司机位置信息
        locationFeignClient.removeDriverLocation(driverId);

        //清空司机新订单队列
        newOrderDispatchFeignClient.clearNewOrderQueueData(driverId);
        return true;
    }

    /**
     * 停止接单
     *
     * @param driverId
     * @return
     */
    @Override
    public Boolean stopService(Long driverId) {
        //更新司机接单状态
        driverInfoFeignClient.updateServiceStatus(driverId, 0);

        //删除司机位置信息
        locationFeignClient.removeDriverLocation(driverId);

        //清空司机新订单队列
        newOrderDispatchFeignClient.clearNewOrderQueueData(driverId);
        return true;
    }
}
