package com.aftersales.dispatch.controller;

import com.aftersales.common.result.PageResult;
import com.aftersales.common.result.Result;
import com.aftersales.dispatch.entity.CustomerService;
import com.aftersales.dispatch.service.CustomerServiceService;
import com.aftersales.dispatch.service.LoadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 客服管理接口
 */
@Tag(name = "客服管理")
@RestController
@RequestMapping("/dispatch/cs")
@RequiredArgsConstructor
public class CustomerServiceController {

    private final CustomerServiceService customerServiceService;
    private final LoadService loadService;

    @Operation(summary = "分页查询客服列表")
    @GetMapping("/page")
    public Result<PageResult<CustomerService>> page(@RequestParam(defaultValue = "1") Long pageNum,
                                                    @RequestParam(defaultValue = "10") Long pageSize,
                                                    @RequestParam(required = false) String onlineStatus) {
        return Result.success(customerServiceService.pageQuery(pageNum, pageSize, onlineStatus));
    }

    @Operation(summary = "新增客服")
    @PostMapping
    public Result<CustomerService> create(@RequestBody CustomerService customerService) {
        return Result.success(customerServiceService.create(customerService));
    }

    @Operation(summary = "修改客服信息")
    @PutMapping
    public Result<Void> update(@RequestBody CustomerService customerService) {
        customerServiceService.update(customerService);
        return Result.success();
    }

    @Operation(summary = "客服上线")
    @PutMapping("/{id}/online")
    public Result<Void> online(@PathVariable Long id) {
        customerServiceService.changeOnlineStatus(id, "ONLINE");
        return Result.success();
    }

    @Operation(summary = "客服下线")
    @PutMapping("/{id}/offline")
    public Result<Void> offline(@PathVariable Long id) {
        customerServiceService.changeOnlineStatus(id, "OFFLINE");
        return Result.success();
    }

    @Operation(summary = "查询客服实时负载（Redis）")
    @GetMapping("/{id}/load")
    public Result<Integer> load(@PathVariable Long id) {
        return Result.success(loadService.get(id));
    }
}
