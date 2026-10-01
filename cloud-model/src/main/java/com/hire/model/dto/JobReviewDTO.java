package com.hire.model.dto;

import lombok.Data;

/**
 * 管理员审核职位请求参数(API设计文档 3.11)
 */
@Data
public class JobReviewDTO {

    /** 审核决定:approve-审核通过 / reject-审核拒绝 */
    private String decision;

    /** 审核意见,拒绝时必填,最长500字 */
    private String remark;
}