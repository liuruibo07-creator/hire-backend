package com.hire.model.vo;

import com.hire.model.entity.Resume;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 投递详情 VO：GET /application/applications/{id} 返回。
 *
 * 包含投递记录本身 + 简历详情（本库 t_resume 查询）+ 职位信息（Feign 从 job-service 获取）。
 * 仅投递人本人与职位发布企业可见。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 投递记录ID */
    private Long id;

    /** 职位ID */
    private Long jobId;

    /** 职位名称（Feign 实时获取，职位可能已被删除，此时为 null） */
    private String jobTitle;

    /** 工作城市（Feign 实时获取） */
    private String jobCity;

    /** 求职者用户ID */
    private Long applicantId;

    /** 求职者姓名（取自投递所用简历，简历可能已被删除，此时为 null） */
    private String applicantName;

    /** 使用的简历ID */
    private Long resumeId;

    /** 简历标题 */
    private String resumeTitle;

    /** 简历详情（完整简历信息，简历可能已被删除，此时为 null） */
    private Resume resume;

    /** 投递状态：0 待处理，1 已查看，2 面试邀请，3 已录用，4 已拒绝 */
    private Integer status;

    /** 求职附言 */
    private String coverLetter;

    /** 企业备注 */
    private String remark;

    /** 投递时间 */
    private LocalDateTime createTime;
}
