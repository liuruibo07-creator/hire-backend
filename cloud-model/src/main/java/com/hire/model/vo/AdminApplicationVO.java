package com.hire.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理员全平台投递记录 VO：GET /application/admin/applications 返回的列表元素。
 *
 * 与企业侧 ReceivedApplicationVO 的区别：
 * 1. 不携带完整简历内容（管理员列表仅做巡检/排查，详情由前端单独调详情接口）
 * 2. 额外携带 employerId（职位发布企业用户ID，取自投递记录冗余字段），便于管理员按企业维度排查
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminApplicationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 投递记录ID */
    private Long id;

    /** 求职者用户ID */
    private Long userId;

    /** 求职者姓名（取自投递所用简历，简历可能已被删除，此时为 null） */
    private String applicantName;

    /** 职位ID */
    private Long jobId;

    /** 职位名称（Feign 实时获取，职位可能已被删除，此时为 null） */
    private String jobTitle;

    /** 工作城市（Feign 实时获取） */
    private String jobCity;

    /** 职位发布企业用户ID（投递记录冗余字段） */
    private Long employerId;

    /** 使用的简历ID */
    private Long resumeId;

    /** 简历标题（简历可能已被删除，此时为 null） */
    private String resumeTitle;

    /** 投递状态：0 待处理，1 已查看，2 面试邀请，3 已录用，4 已拒绝 */
    private Integer status;

    /** 求职附言 */
    private String coverLetter;

    /** 企业备注 */
    private String remark;

    /** 投递时间 */
    private LocalDateTime createTime;
}
