package com.hire.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 职位审核结果(API设计文档 3.11)
 */
@Data
public class JobReviewVO {

    /** 职位ID */
    private Long id;

    /** 审核后状态:1-招聘中(通过) / 4-审核拒绝 */
    private Integer status;

    /** 审核意见 */
    private String reviewRemark;

    /** 审核管理员ID */
    private Long reviewedBy;

    /** 审核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime reviewedAt;
}