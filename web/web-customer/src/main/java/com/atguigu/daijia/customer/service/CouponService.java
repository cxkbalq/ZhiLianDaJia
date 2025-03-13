package com.atguigu.daijia.customer.service;

import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.coupon.NoReceiveCouponVo;
import com.atguigu.daijia.model.vo.coupon.NoUseCouponVo;
import com.atguigu.daijia.model.vo.coupon.UsedCouponVo;

public interface CouponService  {


    PageVo<NoReceiveCouponVo> findNoReceivePage(Long customerId, Long page, Long limit);

    PageVo<UsedCouponVo> findUsedPage(Long customerId, Long page, Long limit);

    PageVo<NoUseCouponVo> findNoUsePage(Long customerId, Long page, Long limit);
}
