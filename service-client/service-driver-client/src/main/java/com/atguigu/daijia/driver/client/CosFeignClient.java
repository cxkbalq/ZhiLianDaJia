package com.atguigu.daijia.driver.client;

import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.model.vo.driver.CosUploadVo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(value = "service-driver")
public interface CosFeignClient {
    /**
     * 上传
     * @param file
     * @param path
     * @return
     * MediaType.MULTIPART_FORM_DATA_VALU指定为二进制上传
     * consumes 是用来指定请求的内容类型（Content-Type）的属性，它告诉服务器请求体的数据格式。
     */
    @PostMapping(value = "/cos/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Result<CosUploadVo> upload(@RequestPart("file") MultipartFile file, @RequestParam("path") String path);
}