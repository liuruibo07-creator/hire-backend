package com.hire.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 投递记录实体：与 t_applications 表字段一一对应（application-service 使用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Applications implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 投递人用户ID */
    private Long userId;

    /** 职位ID（跨库逻辑外键，指向 job-service 的 t_job.id） */
    private Long jobId;

    /** 使用的简历ID */
    private Long resumeId;

    /** 职位发布者用户ID（冗余，避免每条投递都远程查询 job-service） */
    private Long employerId;

    /** 求职附言 */
    private String coverLetter;

    /** 投递状态：0 待处理，1 已查看，2 面试邀请，3 已录用，4 已拒绝 */
    private Integer status;

    /** 企业备注（更新状态时选填） */
    private String remark;

    /** 是否删除：0 未删除，1 已删除 */
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
