package com.hire.model.dto;

import lombok.Data;

/**
 * 按状态分组的计数结果，配合 GROUP BY status 使用（内部查询载体，不对外暴露）。
 */
@Data
public class StatusCountDTO {

    /** 投递状态：0 待处理，1 已查看，2 面试邀请，3 已录用，4 已拒绝 */
    private Integer status;

    /** 该状态下的投递数量 */
    private Long total;
}
