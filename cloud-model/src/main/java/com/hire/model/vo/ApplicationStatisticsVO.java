package com.hire.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 平台投递统计(API设计文档 4.14 内部接口返回)
 */
@Data
public class ApplicationStatisticsVO {

    /** 未逻辑删除的投递记录总数 */
    private Long applicationTotal;

    /** 每日新增投递,日期升序,无数据补0 */
    private List<DayCount> dailyNewApplications;

    /**
     * 单日统计
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayCount {

        /** 日期,格式 yyyy-MM-dd */
        private String date;

        /** 当日新增投递数 */
        private Long count;
    }
}
