package com.hire.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 职位简要信息：job-service 通过 JobClient 返回给 application-service。
 *
 * 只传递投递流程必需的字段，不返回 description / requirement 等大字段，
 * 减少 Feign 传输的数据量。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    /** 职位发布者用户ID，投递记录需要冗余保存，用于给雇主发通知和查询收到的投递 */
    private Long employerId;

    /** 职位状态：0 已下线，1 招聘中 */
    private Integer status;

    private String city;
}
