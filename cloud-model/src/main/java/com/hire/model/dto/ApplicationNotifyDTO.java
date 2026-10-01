package com.hire.model.dto;

import lombok.Data;

/*
 *投递/状态变更 MQ 消息体
 */
@Data
public class ApplicationNotifyDTO {
    private Long userId;
    private Long employerId;
    private Long jobId;
    private String jobTitle;
    private String seekerName;
    private String action; // apply, status_change
    private Integer status;
}
