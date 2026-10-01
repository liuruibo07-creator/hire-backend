package com.hire.job.constant;

/**
 * 职位服务常量
 */
public final class JobConstants {

    private JobConstants() {
    }

    /** Elasticsearch 职位索引名 */
    public static final String ES_INDEX = "job_index";

    /** Elasticsearch 深分页窗口上限(from+size 不得超过 index.max_result_window,默认 10000) */
    public static final int ES_MAX_RESULT_WINDOW = 10000;

    /** 职位状态:企业下架 */
    public static final int STATUS_OFFLINE = 0;

    /** 职位状态:招聘中(审核通过) */
    public static final int STATUS_ONLINE = 1;

    /** 职位状态:管理员强制下架 */
    public static final int STATUS_ADMIN_OFFLINE = 2;

    /** 职位状态:待审核 */
    public static final int STATUS_PENDING = 3;

    /** 职位状态:审核拒绝 */
    public static final int STATUS_REJECTED = 4;

    /** 审核决策:通过 */
    public static final String REVIEW_APPROVE = "approve";

    /** 审核决策:拒绝 */
    public static final String REVIEW_REJECT = "reject";

    /** 角色:企业 */
    public static final String ROLE_EMPLOYER = "employer";

    /** 角色:管理员 */
    public static final String ROLE_ADMIN = "admin";

    /** 统计天数:7/30/90 */
    public static final int STAT_DAYS_DEFAULT = 7;

    /** 排序:相关度(默认) */
    public static final String SORT_RELEVANCE = "relevance";

    /** 排序:最新发布 */
    public static final String SORT_LATEST = "latest";

    /** 排序:薪资从高到低 */
    public static final String SORT_SALARY_DESC = "salary_desc";

    /** 排序:薪资从低到高 */
    public static final String SORT_SALARY_ASC = "salary_asc";
}