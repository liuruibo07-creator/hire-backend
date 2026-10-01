package com.hire.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 职位投递统计 VO（管理员接口）。
 *
 * applicationTotal 为各状态数量之和；某状态没有投递时对应数量为 0。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobApplicationStatisticsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long jobId;

    private String jobTitle;

    /** 投递总数（各状态之和，不含已删除记录） */
    private Long applicationTotal;

    /** 待处理（status=0）数量 */
    private Long pendingCount;

    /** 已查看（status=1）数量 */
    private Long viewedCount;

    /** 面试邀请（status=2）数量 */
    private Long interviewCount;

    /** 已录用（status=3）数量 */
    private Long offeredCount;

    /** 已拒绝（status=4）数量 */
    private Long rejectedCount;
}
