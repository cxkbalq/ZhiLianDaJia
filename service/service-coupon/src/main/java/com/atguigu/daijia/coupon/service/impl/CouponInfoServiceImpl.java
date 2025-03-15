package com.atguigu.daijia.coupon.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.zdyException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.coupon.mapper.CouponInfoMapper;
import com.atguigu.daijia.coupon.mapper.CustomerCouponMapper;
import com.atguigu.daijia.coupon.service.CouponInfoService;
import com.atguigu.daijia.model.entity.coupon.CouponInfo;
import com.atguigu.daijia.model.entity.coupon.CustomerCoupon;
import com.atguigu.daijia.model.form.coupon.UseCouponForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.coupon.AvailableCouponVo;
import com.atguigu.daijia.model.vo.coupon.NoReceiveCouponVo;
import com.atguigu.daijia.model.vo.coupon.NoUseCouponVo;
import com.atguigu.daijia.model.vo.coupon.UsedCouponVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class CouponInfoServiceImpl extends ServiceImpl<CouponInfoMapper, CouponInfo> implements CouponInfoService {
    @Autowired
    private CouponInfoMapper couponInfoMapper;
    @Autowired
    private CustomerCouponMapper customerCouponMapper;
    @Autowired
    private RedissonClient redissonClient;

    /**
     * 查找未领取的优惠卷
     *
     * @param pageParam
     * @param customerId
     * @return
     */
    @Override
    public PageVo<NoReceiveCouponVo> findNoReceivePage(Page<CouponInfo> pageParam, Long customerId) {
        IPage<NoReceiveCouponVo> pageInfo = couponInfoMapper.findNoReceivePage(pageParam, customerId);
        return new PageVo(pageInfo.getRecords(), pageInfo.getPages(), pageInfo.getTotal());
    }

    /**
     * 查询未使用优惠券分页列表
     *
     * @param pageParam
     * @param customerId
     * @return
     */
    @Override
    public PageVo<NoUseCouponVo> findNoUsePage(Page<CouponInfo> pageParam, Long customerId) {
        IPage<NoUseCouponVo> pageInfo = couponInfoMapper.findNoUsePage(pageParam, customerId);
        return new PageVo(pageInfo.getRecords(), pageInfo.getPages(), pageInfo.getTotal());
    }

    /**
     * 查询已使用优惠券分页列表
     *
     * @param pageParam
     * @param customerId
     * @return
     */
    @Override
    public PageVo<UsedCouponVo> findUsedPage(Page<CouponInfo> pageParam, Long customerId) {
        IPage<UsedCouponVo> pageInfo = couponInfoMapper.findUsedPage(pageParam, customerId);
        return new PageVo(pageInfo.getRecords(), pageInfo.getPages(), pageInfo.getTotal());
    }

    /**
     * 领取优惠卷
     *
     * @param customerId
     * @param couponId
     * @return
     */
    @Override
    @Transactional
    public Boolean receive(Long customerId, Long couponId) throws InterruptedException {
        //查询优惠卷
        CouponInfo couponInfo = couponInfoMapper.selectById(couponId);

        //判断是否过期
        if (couponInfo.getExpireTime().before(new Date())) {
            throw new zdyException(ResultCodeEnum.COUPON_EXPIRE);
        }

        //校验库存，优惠券领取数量判断
        if (couponInfo.getReceiveCount() >= couponInfo.getPublishCount()) {
            throw new zdyException(ResultCodeEnum.COUPON_LESS);
        }

        //校验每人限领数量
        Long count = customerCouponMapper.selectCount(new LambdaQueryWrapper<CustomerCoupon>()
                .eq(CustomerCoupon::getCouponId, couponId).eq(CustomerCoupon::getCustomerId, customerId));

        if (count >= couponInfo.getPerLimit()) {
            throw new zdyException(ResultCodeEnum.COUPON_USER_LIMIT);
        }

        RLock rLock = null;
        try {
            //获取锁,每人领取限制  与 优惠券发行总数 必须保证原子性，使用customerId减少锁的粒度，增加并发能力
            rLock = redissonClient.getLock(RedisConstant.COUPON_LOCK + customerId);
            boolean b = rLock.tryLock(RedisConstant.COUPON_LOCK_WAIT_TIME, RedisConstant.COUPON_LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (b) {
                //更新优惠券领取数量
                int row = couponInfoMapper.updateReceiveCount(couponId);
                if (row == 1) {
                    //保存领取记录
                    this.saveCustomerCoupon(customerId, couponId, couponInfo.getExpireTime());
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != rLock) {
                rLock.unlock();
            }
        }
        throw new zdyException(ResultCodeEnum.COUPON_LESS);
    }

    /**
     * 获取最佳未使用的优惠卷
     *
     * @param customerId
     * @param orderAmount
     * @return
     */
    @Override
    public List<AvailableCouponVo> findAvailableCoupon(Long customerId, BigDecimal orderAmount) {
        //获取已领取未使用的优惠卷
        List<NoUseCouponVo> noUseList = couponInfoMapper.findNoUseList(customerId);
        //获取现金卷集合
        List<NoUseCouponVo> list1 = noUseList.stream().filter(item -> item.getCouponType().intValue() == 1).collect(Collectors.toList());
        //获取折扣卷集合
        List<NoUseCouponVo> list2 = noUseList.stream().filter(item -> item.getCouponType().intValue() == 2).collect(Collectors.toList());

        //创建符合可用优惠卷集合
        List<AvailableCouponVo> availableCouponVoList = new ArrayList<>();
        //计算现金卷符合条件的集合
        for (NoUseCouponVo noUseCouponVo : list1) {
            //使用门槛判断
            //没门槛，订单金额必须大于优惠券减免金额
            //减免金额
            BigDecimal reduceAmount = noUseCouponVo.getAmount();
            if (noUseCouponVo.getConditionAmount().doubleValue() == 0 && orderAmount.subtract(reduceAmount).doubleValue() > 0) {
                availableCouponVoList.add(this.buildBestNoUseCouponVo(noUseCouponVo, reduceAmount));
            }
            //有门槛，订单金额大于优惠券门槛金额
            if (noUseCouponVo.getConditionAmount().doubleValue() > 0 && orderAmount.subtract(noUseCouponVo.getConditionAmount()).doubleValue() > 0) {
                availableCouponVoList.add(this.buildBestNoUseCouponVo(noUseCouponVo, reduceAmount));
            }
        }
        //计算折扣卷可用使用的集合
        for (NoUseCouponVo noUseCouponVo : list2) {
            //使用门槛判断
            //订单折扣后金额
            BigDecimal discountOrderAmount = orderAmount.multiply(noUseCouponVo.getDiscount()).divide(new BigDecimal("10")).setScale(2, RoundingMode.HALF_UP);
            //减免金额
            BigDecimal reduceAmount = orderAmount.subtract(discountOrderAmount);
            //订单优惠金额
            //没门槛
            if (noUseCouponVo.getConditionAmount().doubleValue() == 0) {
                availableCouponVoList.add(this.buildBestNoUseCouponVo(noUseCouponVo, reduceAmount));
            }
            //有门槛，订单折扣后金额大于优惠券门槛金额
            if (noUseCouponVo.getConditionAmount().doubleValue() > 0 && discountOrderAmount.subtract(noUseCouponVo.getConditionAmount()).doubleValue() > 0) {
                availableCouponVoList.add(this.buildBestNoUseCouponVo(noUseCouponVo, reduceAmount));
            }
        }

        //对结果进行排序  升序
        if (!CollectionUtils.isEmpty(availableCouponVoList)) {
            availableCouponVoList.sort(new Comparator<AvailableCouponVo>() {
                @Override
                public int compare(AvailableCouponVo o1, AvailableCouponVo o2) {
                    return o1.getReduceAmount().compareTo(o2.getReduceAmount());
                }
            });
        }

        return availableCouponVoList;
    }

    /**
     * 使用优惠卷
     *
     * @param useCouponForm
     * @return
     */
    @Override
    public BigDecimal useCoupon(UseCouponForm useCouponForm) {
        //获取乘客优惠券
        //这个用mybatis-plus查不到，未知错误
        CustomerCoupon customerCoupon = customerCouponMapper.selectCustomerCoupon(useCouponForm.getCustomerCouponId());
        if (null == customerCoupon) {
            throw new zdyException(ResultCodeEnum.ARGUMENT_VALID_ERROR);
        }
        //查询优惠卷相关信息
        CouponInfo couponInfo = couponInfoMapper.selectById(customerCoupon.getCouponId());
        if (couponInfo == null || couponInfo.getStatus().equals(-1) || couponInfo.getStatus().equals(0)) {
            throw new zdyException(ResultCodeEnum.DATA_ERROR);
        }

        //判断优惠卷类型
        //获取优惠券减免金额
        BigDecimal reduceAmount = null;
        //现金卷
        if (couponInfo.getCouponType().equals(1)) {
            //没门槛，订单金额必须大于优惠券减免金额
            if (couponInfo.getConditionAmount().doubleValue() == 0 && useCouponForm.getOrderAmount()
                    .subtract(couponInfo.getAmount()).doubleValue() > 0) {
                //减免金额
                reduceAmount = couponInfo.getAmount();
            }
            //有门槛，订单金额大于优惠券门槛金额
            if (couponInfo.getConditionAmount().doubleValue() > 0 && useCouponForm.getOrderAmount()
                    .subtract(couponInfo.getConditionAmount()).doubleValue() > 0) {
                //减免金额
                reduceAmount = couponInfo.getAmount();
            }
        }
        //折扣卷
        else {
            //使用门槛判断
            //订单折扣后金额
            BigDecimal discountOrderAmount = useCouponForm.getOrderAmount()
                    .multiply(couponInfo.getDiscount()).divide(new BigDecimal("10")).setScale(2, RoundingMode.HALF_UP);
            //订单优惠金额
            //没门槛
            if (couponInfo.getConditionAmount().doubleValue() == 0) {
                //减免金额
                reduceAmount = useCouponForm.getOrderAmount().subtract(discountOrderAmount);
            }
            //有门槛，订单折扣后金额大于优惠券门槛金额
            if (couponInfo.getConditionAmount().doubleValue() > 0 && discountOrderAmount.subtract(couponInfo.getConditionAmount()).doubleValue() > 0) {
                //减免金额
                reduceAmount = useCouponForm.getOrderAmount().subtract(discountOrderAmount);
            }
        }
        //更新使用次数
        if (reduceAmount.doubleValue() > 0) {
            //以进行并发处理，乐观锁
            int row = couponInfoMapper.updateUseCount(couponInfo.getId());
            if (row == 1) {
                CustomerCoupon updateCustomerCoupon = new CustomerCoupon();
                updateCustomerCoupon.setId(customerCoupon.getId());
                updateCustomerCoupon.setUsedTime(new Date());
                updateCustomerCoupon.setOrderId(useCouponForm.getOrderId());
                updateCustomerCoupon.setStatus(2);
                customerCouponMapper.updateById(updateCustomerCoupon);
                return reduceAmount;
            }

//            //更新优惠卷状态
//            int i = customerCouponMapper.updateUseState(useCouponForm.getCustomerCouponId());
//
        }

        throw new zdyException(ResultCodeEnum.DATA_ERROR);
    }

    private AvailableCouponVo buildBestNoUseCouponVo(NoUseCouponVo noUseCouponVo, BigDecimal reduceAmount) {
        AvailableCouponVo bestNoUseCouponVo = new AvailableCouponVo();
        BeanUtils.copyProperties(noUseCouponVo, bestNoUseCouponVo);
        bestNoUseCouponVo.setCouponId(noUseCouponVo.getId());
        bestNoUseCouponVo.setReduceAmount(reduceAmount);
        return bestNoUseCouponVo;
    }

    private void saveCustomerCoupon(Long customerId, Long couponId, Date expireTime) {
        CustomerCoupon customerCoupon = new CustomerCoupon();
        customerCoupon.setCustomerId(customerId);
        customerCoupon.setCouponId(couponId);
        customerCoupon.setStatus(1);
        customerCoupon.setReceiveTime(new Date());
        customerCoupon.setExpireTime(expireTime);
        customerCouponMapper.insert(customerCoupon);
    }
}
