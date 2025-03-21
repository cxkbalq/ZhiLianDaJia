package com.atguigu.daijia.rules.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.daijia.config.DroolsConfig;
import com.atguigu.daijia.model.form.rules.FeeRuleRequest;
import com.atguigu.daijia.model.form.rules.FeeRuleRequestForm;
import com.atguigu.daijia.model.vo.rules.FeeRuleResponse;
import com.atguigu.daijia.model.vo.rules.FeeRuleResponseVo;
import com.atguigu.daijia.rules.service.FeeRuleService;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.kie.api.runtime.KieSession;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class FeeRuleServiceImpl implements FeeRuleService {
    //路费规则
    private static final String RULES_CUSTOMER_RULES_DRL = "rules/FeeRule.drl";

    /**
     * 计算费用
     * @param feeRuleRequestForm
     * @return
     */
    @Override
    public FeeRuleResponseVo calculateOrderFee(FeeRuleRequestForm feeRuleRequestForm) {
        //创建所需对象
        FeeRuleRequest feeRuleRequest = new FeeRuleRequest();
        feeRuleRequest.setDistance(feeRuleRequestForm.getDistance());
        feeRuleRequest.setStartTime(new DateTime(feeRuleRequestForm.getStartTime()).toString("HH:mm:ss"));
        feeRuleRequest.setWaitMinute(feeRuleRequestForm.getWaitMinute());
        log.info("传入参数：{}", JSON.toJSONString(feeRuleRequest));
        //测试专用
       // feeRuleRequest.setDistance(new BigDecimal(6.7));

        //创建规则会话
        KieSession kieSession = DroolsConfig.loadForRule(RULES_CUSTOMER_RULES_DRL);
        //创建返回对象
        FeeRuleResponse feeRuleResponse = new FeeRuleResponse();
        //设计全局对象
        kieSession.setGlobal("feeRuleResponse", feeRuleResponse);  //需要返回的结果
        kieSession.insert(feeRuleRequest);//需要判断的类

        //触发规则
        kieSession.fireAllRules();
        // 中止会话
        kieSession.dispose();
        log.info("计算结果：{}", JSON.toJSONString(feeRuleResponse));
//        feeRuleResponse.setTotalAmount();
        //封装返回对象
        FeeRuleResponseVo feeRuleResponseVo = new FeeRuleResponseVo();
        BeanUtils.copyProperties(feeRuleResponse, feeRuleResponseVo);

        return feeRuleResponseVo;
    }
}
