package com.hire.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Minimal application relationship, without resume or contact details. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationChatContextVO {
    private Long applicationId;
    private Long jobId;
    private Long seekerId;
    private Long employerId;
}
