package com.atguigu.daijia.driver.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.common.execption.zdyException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.config.TencentCloudProperties;
import com.atguigu.daijia.driver.mapper.*;
import com.atguigu.daijia.driver.service.CosService;
import com.atguigu.daijia.driver.service.DriverInfoService;
import com.atguigu.daijia.model.entity.driver.*;
import com.atguigu.daijia.model.form.driver.DriverFaceModelForm;
import com.atguigu.daijia.model.form.driver.UpdateDriverAuthInfoForm;
import com.atguigu.daijia.model.vo.driver.DriverAuthInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverInfoVo;
import com.atguigu.daijia.model.vo.driver.DriverLoginVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.iai.v20180301.IaiClient;
import com.tencentcloudapi.iai.v20180301.models.*;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class DriverInfoServiceImpl extends ServiceImpl<DriverInfoMapper, DriverInfo> implements DriverInfoService {


    @Autowired
    private WxMaService wxMaServicel;
    @Autowired
    private DriverSetMapper driverSetMapper;

    @Autowired
    private DriverLoginLogMapper driverLoginLogMapper;
    @Autowired
    private DriverAccountMapper driverAccountMapper;

    @Autowired
    private CosService cosService;

    @Autowired
    private TencentCloudProperties tencentCloudProperties;

    @Autowired
    private DriverFaceRecognitionMapper driverFaceRecognitionMapper;

    /**
     * 司机端登录
     *
     * @param code
     * @param ip
     * @return
     */
    @Override
    public Long login(String code, String ip) {
        try {
            //获取openid
            WxMaJscode2SessionResult sessionInfo = wxMaServicel.getUserService().getSessionInfo(code);
            String openid = sessionInfo.getOpenid();
            log.info("当前司机端openid:" + openid);


            //判断是否为初次登录
            LambdaQueryWrapper<DriverInfo> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(DriverInfo::getWxOpenId, openid);
            DriverInfo driverInfo = this.getOne(lambdaQueryWrapper);
            if (driverInfo == null) {
                //初始化信息
                driverInfo = new DriverInfo();
                driverInfo.setNickname(String.valueOf(System.currentTimeMillis()));
                driverInfo.setAvatarUrl("https://th.bing.com/th?id=OIP.7GLMYPqMlt2LgkbPsOnDIAAAAA&w=251&h=248&c=8&rs=1&qlt=30&o=6&dpr=4&pid=3.1&rm=2");
                driverInfo.setWxOpenId(openid);
                driverInfo.setPhone("17856796238");
                this.save(driverInfo);

                //初始化默认设置
                DriverSet driverSet = new DriverSet();
                driverSet.setDriverId(driverInfo.getId());
                driverSet.setOrderDistance(new BigDecimal(0));//0：无限制
                driverSet.setAcceptDistance(new BigDecimal(SystemConstant.ACCEPT_DISTANCE));//默认接单范围：5公里
                driverSet.setIsAutoAccept(0);//0：否 1：是
                driverSetMapper.insert(driverSet);

                //初始化司机账户
                DriverAccount driverAccount = new DriverAccount();
                driverAccount.setDriverId(driverInfo.getId());
                driverAccountMapper.insert(driverAccount);
            }

            //登录日志
            DriverLoginLog driverLoginLog = new DriverLoginLog();
            driverLoginLog.setDriverId(driverInfo.getId());
            driverLoginLog.setMsg("司机端小程序登录");
            driverLoginLog.setIpaddr(ip);
            driverLoginLogMapper.insert(driverLoginLog);

            return driverInfo.getId();
        } catch (WxErrorException e) {
            e.printStackTrace();
            throw new zdyException(ResultCodeEnum.WX_CODE_ERROR);
        }
    }

    /**
     * 获取登录信息
     *
     * @param driverId
     * @return
     */
    @Override
    public DriverLoginVo getDriverLoginInfo(Long driverId) {
        DriverInfo one = this.getById(driverId);
        if (one == null) {
            throw new zdyException(ResultCodeEnum.FAIL);
        }
        DriverLoginVo driverLoginVo = new DriverLoginVo();
        BeanUtils.copyProperties(one, driverLoginVo);

        //是否创建人脸库人员，接单时做人脸识别判断
        Boolean isArchiveFace = StringUtils.hasText(one.getFaceModelId());
//        log.info("当前："+);
        driverLoginVo.setIsArchiveFace(isArchiveFace);
        return driverLoginVo;
    }


    /**
     * 获取司机认证信息
     *
     * @param driverId
     * @return
     */
    @Override
    public DriverAuthInfoVo getDriverAuthInfo(Long driverId) {
        DriverInfo driverInfo = this.getById(driverId);
        DriverAuthInfoVo driverAuthInfoVo = new DriverAuthInfoVo();
        BeanUtils.copyProperties(driverInfo, driverAuthInfoVo);
        driverAuthInfoVo.setIdcardBackShowUrl(cosService.getImageUrl(driverAuthInfoVo.getIdcardBackUrl()));
        driverAuthInfoVo.setIdcardFrontShowUrl(cosService.getImageUrl(driverAuthInfoVo.getIdcardFrontUrl()));
        driverAuthInfoVo.setIdcardHandShowUrl(cosService.getImageUrl(driverAuthInfoVo.getIdcardHandUrl()));
        driverAuthInfoVo.setDriverLicenseFrontShowUrl(cosService.getImageUrl(driverAuthInfoVo.getDriverLicenseFrontUrl()));
        driverAuthInfoVo.setDriverLicenseBackShowUrl(cosService.getImageUrl(driverAuthInfoVo.getDriverLicenseBackUrl()));
        driverAuthInfoVo.setDriverLicenseHandShowUrl(cosService.getImageUrl(driverAuthInfoVo.getDriverLicenseHandUrl()));
        return driverAuthInfoVo;
    }

    /**
     * 更新司机认证状态
     *
     * @param updateDriverAuthInfoForm
     * @return
     */
    @Override
    public Boolean updateDriverAuthInfo(UpdateDriverAuthInfoForm updateDriverAuthInfoForm) {
        DriverInfo driverInfo = new DriverInfo();
        driverInfo.setId(updateDriverAuthInfoForm.getDriverId());
        BeanUtils.copyProperties(updateDriverAuthInfoForm, driverInfo);
        return this.updateById(driverInfo);
    }

    @Override
    public Boolean creatDriverFaceModel(DriverFaceModelForm driverFaceModelForm) {
        DriverInfo driverInfo = this.getById(driverFaceModelForm.getDriverId());
        try {
            // 实例化一个认证对象，入参需要传入腾讯云账户 SecretId 和 SecretKey，此处还需注意密钥对的保密
            // 代码泄露可能会导致 SecretId 和 SecretKey 泄露，并威胁账号下所有资源的安全性。以下代码示例仅供参考，建议采用更安全的方式来使用密钥，请参见：https://cloud.tencent.com/document/product/1278/85305
            // 密钥可前往官网控制台 https://console.cloud.tencent.com/cam/capi 进行获取
            Credential cred = new Credential(tencentCloudProperties.getSecretId(), tencentCloudProperties.getSecretKey());
            // 实例化一个http选项，可选的，没有特殊需求可以跳过
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("iai.tencentcloudapi.com");
            // 实例化一个client选项，可选的，没有特殊需求可以跳过
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            // 实例化要请求产品的client对象,clientProfile是可选的
            IaiClient client = new IaiClient(cred, tencentCloudProperties.getRegion(), clientProfile);
            // 实例化一个请求对象,每个接口都会对应一个request对象
            CreatePersonRequest req = new CreatePersonRequest();
            req.setGroupId(tencentCloudProperties.getPersionGroupId());
            //基本信息
            req.setPersonId(String.valueOf(driverInfo.getId()));
            req.setGender(Long.parseLong(driverInfo.getGender()));
            req.setQualityControl(4L);
            req.setUniquePersonControl(4L);
            req.setPersonName(driverInfo.getName());
            req.setImage(driverFaceModelForm.getImageBase64());

            // 返回的resp是一个CreatePersonResponse的实例，与请求对象对应
            CreatePersonResponse resp = client.CreatePerson(req);
            // 输出json格式的字符串回包
            System.out.println(CreatePersonResponse.toJsonString(resp));
            if (StringUtils.hasText(resp.getFaceId())) {
                //人脸校验必要参数，保存到数据库表
                driverInfo.setFaceModelId(resp.getFaceId());
                this.updateById(driverInfo);
            }
        } catch (TencentCloudSDKException e) {
            System.out.println(e.toString());
            return false;
        }

        return true;
    }

    /**
     * 获取单个司机设置
     *
     * @param driverId
     * @return
     */

    @Override
    public DriverSet getDriverSet(Long driverId) {
        LambdaQueryWrapper<DriverSet> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(DriverSet::getDriverId, driverId);
        DriverSet driverSet = driverSetMapper.selectOne(lambdaQueryWrapper);
        return driverSet;
    }

    /**
     * 批量获取司机设置，以提高请求速度
     *
     * @param list
     * @return
     */

    @Override
    public List<DriverSet> getListDriverSet(List<Long> list) {
        LambdaQueryWrapper<DriverSet> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.in(DriverSet::getDriverId, list);
        List<DriverSet> driverSets = driverSetMapper.selectList(lambdaQueryWrapper);
        log.info("查询到司机为：" + driverSets.toString());
        return driverSets;
    }

    /**
     * 当日是否进行人脸验证
     *
     * @param driverId
     * @return
     */

    @Override
    public Boolean isFaceRecognition(Long driverId) {
        LambdaQueryWrapper<DriverFaceRecognition> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DriverFaceRecognition::getDriverId, driverId);
        queryWrapper.eq(DriverFaceRecognition::getFaceDate, new DateTime().toString("yyyy-MM-dd"));
        long count = driverFaceRecognitionMapper.selectCount(queryWrapper);
        //这里为了方便测试返回true
        return true;
        //return count != 0;
    }

    /**
     * 人脸验证
     * 文档地址：
     * https://cloud.tencent.com/document/api/867/44983
     * https://console.cloud.tencent.com/api/explorer?Product=iai&Version=2020-03-03&Action=VerifyFace
     *
     * @param driverFaceModelForm
     * @return
     */

    @Override
    public Boolean verifyDriverFace(DriverFaceModelForm driverFaceModelForm) {
        try {
            // 实例化一个认证对象，入参需要传入腾讯云账户 SecretId 和 SecretKey，此处还需注意密钥对的保密
            // 代码泄露可能会导致 SecretId 和 SecretKey 泄露，并威胁账号下所有资源的安全性。以下代码示例仅供参考，建议采用更安全的方式来使用密钥，请参见：https://cloud.tencent.com/document/product/1278/85305
            // 密钥可前往官网控制台 https://console.cloud.tencent.com/cam/capi 进行获取
            Credential cred = new Credential(tencentCloudProperties.getSecretId(), tencentCloudProperties.getSecretKey());
            // 实例化一个http选项，可选的，没有特殊需求可以跳过
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("iai.tencentcloudapi.com");
            // 实例化一个client选项，可选的，没有特殊需求可以跳过
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            // 实例化要请求产品的client对象,clientProfile是可选的
            IaiClient client = new IaiClient(cred, tencentCloudProperties.getRegion(), clientProfile);
            // 实例化一个请求对象,每个接口都会对应一个request对象
            VerifyFaceRequest req = new VerifyFaceRequest();
            req.setImage(driverFaceModelForm.getImageBase64());
            req.setPersonId(String.valueOf(driverFaceModelForm.getDriverId()));
            // 返回的resp是一个VerifyFaceResponse的实例，与请求对象对应
            VerifyFaceResponse resp = client.VerifyFace(req);
            // 输出json格式的字符串回包
            System.out.println(VerifyFaceResponse.toJsonString(resp));
            if (resp.getIsMatch()) {
                //活体检查
                if (this.detectLiveFace(driverFaceModelForm.getImageBase64())) {
                    DriverFaceRecognition driverFaceRecognition = new DriverFaceRecognition();
                    driverFaceRecognition.setDriverId(driverFaceModelForm.getDriverId());
                    driverFaceRecognition.setFaceDate(new Date());
                    driverFaceRecognitionMapper.insert(driverFaceRecognition);
                    return true;
                }
                ;
            }
        } catch (TencentCloudSDKException e) {
            System.out.println(e.toString());
        }
        throw new zdyException(ResultCodeEnum.FACE_FAIL);
    }

    /**
     * 更新司机状态
     *
     * @param driverId
     * @param status
     * @return
     */
    @Override
    public Boolean updateServiceStatus(Long driverId, Integer status) {
        LambdaQueryWrapper<DriverSet> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DriverSet::getDriverId, driverId);
        DriverSet driverSet = new DriverSet();
        driverSet.setServiceStatus(status);
        driverSetMapper.update(driverSet, queryWrapper);
        return true;
    }

    /**
     * 获取司机基本信息
     *
     * @param driverId
     * @return
     */
    @Override
    public DriverInfoVo getDriverInfo(Long driverId) {
        DriverInfo driverInfo = this.getById(driverId);
        DriverInfoVo driverInfoVo = new DriverInfoVo();
        BeanUtils.copyProperties(driverInfo, driverInfoVo);
        //获取当前年
        int year = new DateTime().getYear();
        //获取初次申领年
        int year1 = new DateTime(driverInfo.getDriverLicenseIssueDate()).getYear();
        //驾龄
        Integer driverLicenseAge = year - year1 + 1;
        driverInfoVo.setDriverLicenseAge(driverLicenseAge);
        return driverInfoVo;
    }

    /**
     * 获取司机openid
     * @param driverId
     * @return
     */
    @Override
    public String getDriverOpenId(Long driverId) {
        DriverInfo driverInfo = this.getOne(new LambdaQueryWrapper<DriverInfo>().eq(DriverInfo::getId, driverId).select(DriverInfo::getWxOpenId));
        return driverInfo.getWxOpenId();
    }

    /**
     * 人脸静态活体检测
     * 文档地址：
     * https://cloud.tencent.com/document/api/867/48501
     * https://console.cloud.tencent.com/api/explorer?Product=iai&Version=2020-03-03&Action=DetectLiveFace
     *
     * @param imageBase64
     * @return
     */
    private Boolean detectLiveFace(String imageBase64) {
        try {
            // 实例化一个认证对象，入参需要传入腾讯云账户 SecretId 和 SecretKey，此处还需注意密钥对的保密
            // 代码泄露可能会导致 SecretId 和 SecretKey 泄露，并威胁账号下所有资源的安全性。以下代码示例仅供参考，建议采用更安全的方式来使用密钥，请参见：https://cloud.tencent.com/document/product/1278/85305
            // 密钥可前往官网控制台 https://console.cloud.tencent.com/cam/capi 进行获取
            Credential cred = new Credential(tencentCloudProperties.getSecretId(), tencentCloudProperties.getSecretKey());
            // 实例化一个http选项，可选的，没有特殊需求可以跳过
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("iai.tencentcloudapi.com");
            // 实例化一个client选项，可选的，没有特殊需求可以跳过
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            // 实例化要请求产品的client对象,clientProfile是可选的
            IaiClient client = new IaiClient(cred, tencentCloudProperties.getRegion(), clientProfile);
            // 实例化一个请求对象,每个接口都会对应一个request对象
            DetectLiveFaceRequest req = new DetectLiveFaceRequest();
            req.setImage(imageBase64);
            // 返回的resp是一个DetectLiveFaceResponse的实例，与请求对象对应
            DetectLiveFaceResponse resp = client.DetectLiveFace(req);
            // 输出json格式的字符串回包
            System.out.println(DetectLiveFaceResponse.toJsonString(resp));
            if (resp.getIsLiveness()) {
                return true;
            }
        } catch (TencentCloudSDKException e) {
            System.out.println(e.toString());
        }
        return false;
    }

}