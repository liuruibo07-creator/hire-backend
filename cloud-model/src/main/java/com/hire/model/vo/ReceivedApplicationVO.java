package com.hire.model.vo;

import com.hire.model.entity.Resume;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 企业收到的投递记录 VO：投递信息 + 求职者姓名 + 简历信息。
 * 职位基本信息由 application-service 通过 Feign 从 job-service 实时获取；
 * 求职者姓名与简历信息在本库 t_resume 中查询。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceivedApplicationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 投递记录ID */
    private Long id;

    /** 职位ID */
    private Long jobId;

    /** 职位名称（Feign 实时获取） */
    private String jobTitle;

    /** 工作城市（Feign 实时获取） */
    private String jobCity;

    /** 求职者用户ID */
    private Long applicantId;

    /** 求职者姓名（取自投递所用简历中的姓名） */
    private String applicantName;

    /** 使用的简历ID */
    private Long resumeId;

    /** 简历标题 */
    private String resumeTitle;

    /** 简历信息（投递所用简历的完整内容，简历可能已被删除，此时为 null） */
    private Resume resume;

    /** 投递状态：0 待处理，1 已查看，2 面试，3 录用，4 拒绝 */
    private Integer status;

    /** 求职附言 */
    private String coverLetter;

    /** 投递时间 */
    private LocalDateTime createTime;
}
