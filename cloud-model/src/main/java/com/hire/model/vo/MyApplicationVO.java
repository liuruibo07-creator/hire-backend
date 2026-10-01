package com.hire.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 我的投递记录 VO：投递信息 + 职位基本信息（application-service 通过 Feign 从 job-service 获取）+ 简历标题。
 * 完整职位/简历详情由前端点开详情时单独调 GET /jobs/{id} 和 GET /application/resume/{id} 获取。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyApplicationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 投递记录ID */
    private Long id;

    /** 职位ID */
    private Long jobId;

    /** 职位名称（Feign 实时获取） */
    private String jobTitle;

    /** 工作城市（Feign 实时获取） */
    private String jobCity;

    /** 使用的简历ID */
    private Long resumeId;

    /** 简历标题 */
    private String resumeTitle;

    /** 投递状态：0 待处理，1 已查看，2 面试，3 录用，4 拒绝 */
    private Integer status;

    /** 求职附言 */
    private String coverLetter;

    /** 投递时间 */
    private LocalDateTime createTime;
}
