package com.hire.model.dto;

import lombok.Data;

/**
 * 职位上下架请求参数(API设计文档 3.3)
 */
@Data
public class JobStatusDTO {

    /** 状态:0-下架 / 1-上架 */
    private Integer status;
}