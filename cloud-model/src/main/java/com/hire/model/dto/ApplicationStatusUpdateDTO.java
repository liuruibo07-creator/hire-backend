package com.hire.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 企业更新投递状态请求参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationStatusUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标状态：1-已查看，2-面试邀请，3-已录用，4-已拒绝 */
    private Integer status;

    /** 备注，选填 */
    private String remark;
}
