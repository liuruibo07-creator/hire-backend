package com.hire.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 平台职位统计(API设计文档 3.13 内部接口返回)
 */
@Data
public class JobStatisticsVO {

    /** 未逻辑删除的全部职位数 */
    private Long jobTotal;

    /** 每日新增职位,日期升序,无数据补0 */
    private List<DayCount> dailyNewJobs;

    /**
     * 单日统计
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayCount {

        /** 日期,格式 yyyy-MM-dd */
        private String date;

        /** 当日新增数 */
        private Long count;
    }
}