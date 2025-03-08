package com.atguigu.daijia.driver.service.impl;

import com.atguigu.daijia.common.execption.zdyException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.driver.client.CiFeignClient;
import com.atguigu.daijia.driver.service.FileService;
import com.atguigu.daijia.driver.service.MonitorService;
import com.atguigu.daijia.model.entity.order.OrderMonitorRecord;
import com.atguigu.daijia.model.form.order.OrderMonitorForm;
import com.atguigu.daijia.model.vo.order.TextAuditingVo;
import com.atguigu.daijia.order.client.OrderMonitorFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class MonitorServiceImpl implements MonitorService {
    @Autowired
    private FileService fileService;
    @Autowired
    private OrderMonitorFeignClient orderMonitorFeignClient;
    @Autowired
    private CiFeignClient ciFeignClient;
    /**
     * 司机端上传录音接口
     *
     * @param file
     * @param orderMonitorForm
     * @return
     */
    @Override
    public Boolean upload(MultipartFile file, OrderMonitorForm orderMonitorForm) {
        //上传文件
        String url = fileService.upload(file);

        OrderMonitorRecord orderMonitorRecord = new OrderMonitorRecord();
        orderMonitorRecord.setOrderId(orderMonitorForm.getOrderId());
        orderMonitorRecord.setFileUrl(url);
        orderMonitorRecord.setContent(orderMonitorForm.getContent());
        //增加文本审核
        TextAuditingVo textAuditingVo =
                ciFeignClient.textAuditing(orderMonitorForm.getContent()).getData();
        orderMonitorRecord.setResult(textAuditingVo.getResult());
        orderMonitorRecord.setKeywords(textAuditingVo.getKeywords());

        orderMonitorFeignClient.saveMonitorRecord(orderMonitorRecord);
        Integer code = orderMonitorFeignClient.saveMonitorRecord(orderMonitorRecord).getCode();
        if (code.equals(200)) {
            return true;
        }
        throw new zdyException(ResultCodeEnum.FEIGN_FAIL);
    }
}
