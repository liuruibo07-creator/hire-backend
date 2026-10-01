package com.hire.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 投递成功事件：application-service 投递成功后发送到 RabbitMQ，
 * notification-service 消费后生成通知记录。
 *
 * 放在 cloud-model 中是为了让生产端与消费端使用同一个类，
 * 保证 JSON 序列化 / 反序列化（__TypeId__ 头）两边一致。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationSubmittedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息唯一标识，消费端用于幂等去重 */
    private String messageId;

    /** 投递记录ID */
    private Long applicationId;

    private Long jobId;

    private String jobTitle;

    /** 接收通知的人（职位发布者） */
    private Long employerId;

    /** 投递人 */
    private Long applicantId;

    private Long resumeId;

    private String coverLetter;
}
