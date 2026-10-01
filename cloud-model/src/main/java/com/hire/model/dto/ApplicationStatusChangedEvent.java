package com.hire.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 投递状态变更事件：application-service 更新状态后发送到 RabbitMQ，
 * notification-service 消费后给求职者生成通知。
 *
 * 与 ApplicationSubmittedEvent 放在同一个包中，
 * 保证生产端与消费端使用同一个类，JSON 序列化结构一致。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationStatusChangedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息唯一标识，消费端用于幂等去重 */
    private String messageId;

    /** 投递记录ID */
    private Long applicationId;

    /** 职位ID */
    private Long jobId;

    /** 职位名称，用于拼装通知内容 */
    private String jobTitle;

    /** 操作方（职位发布企业）用户ID */
    private Long employerId;

    /** 通知接收人：投递该职位的求职者ID */
    private Long applicantId;

    /** 使用的简历ID */
    private Long resumeId;

    /** 变更后的状态：1-已查看，2-面试邀请，3-已录用，4-已拒绝 */
    private Integer status;

    /** 企业备注，选填 */
    private String remark;
}
