package com.aftersales.workorder.vo;

import com.aftersales.workorder.entity.WorkOrder;
import com.aftersales.workorder.entity.WorkOrderDetail;
import com.aftersales.workorder.entity.WorkOrderLog;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 工单详情视图：主表 + 详情 + 操作日志
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderDetailVO {

    private WorkOrder workOrder;

    private WorkOrderDetail detail;

    private List<WorkOrderLog> logs;
}
