package com.aftersales.stats.feign;

import com.aftersales.common.result.PageResult;
import com.aftersales.common.result.Result;
import com.aftersales.stats.feign.dto.CustomerServiceView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 派单服务 Feign 客户端（看板读取客服列表）
 * 设计思路：客服基础信息归派单服务所有，统计服务不复制客服库，只按需远程读取；
 * 负载数值不逐个远程调用（N 次网络往返），直接读共享 Redis 的 dispatch:cs:load:* 计数
 */
@FeignClient(name = "dispatch-service", path = "/dispatch/cs",
        fallbackFactory = CustomerServiceClientFallbackFactory.class)
public interface CustomerServiceClient {

    @GetMapping("/page")
    Result<PageResult<CustomerServiceView>> page(@RequestParam("pageNum") Long pageNum,
                                                 @RequestParam("pageSize") Long pageSize);
}
