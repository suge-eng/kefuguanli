package com.aftersales.dispatch.service;

import com.aftersales.common.exception.BizException;
import com.aftersales.common.result.PageResult;
import com.aftersales.dispatch.entity.CustomerService;
import com.aftersales.dispatch.mapper.CustomerServiceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 客服管理服务
 */
@Service
@RequiredArgsConstructor
public class CustomerServiceService {

    private final CustomerServiceMapper customerServiceMapper;

    public PageResult<CustomerService> pageQuery(Long pageNum, Long pageSize, String onlineStatus) {
        Page<CustomerService> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<CustomerService> wrapper = new LambdaQueryWrapper<CustomerService>()
                .eq(StringUtils.hasText(onlineStatus), CustomerService::getOnlineStatus, onlineStatus)
                .orderByAsc(CustomerService::getId);
        Page<CustomerService> result = customerServiceMapper.selectPage(page, wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    public CustomerService create(CustomerService customerService) {
        customerServiceMapper.insert(customerService);
        return customerService;
    }

    public void update(CustomerService customerService) {
        if (customerService.getId() == null) {
            throw new BizException("客服ID不能为空");
        }
        customerServiceMapper.updateById(customerService);
    }

    /** 上下线只改状态字段，避免误改其他信息 */
    public void changeOnlineStatus(Long id, String onlineStatus) {
        CustomerService cs = customerServiceMapper.selectById(id);
        if (cs == null) {
            throw new BizException("客服不存在: " + id);
        }
        cs.setOnlineStatus(onlineStatus);
        customerServiceMapper.updateById(cs);
    }
}
