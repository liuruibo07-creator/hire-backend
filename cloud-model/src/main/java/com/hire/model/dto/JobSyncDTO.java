package com.hire.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 * 职位同步DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobSyncDTO {
    private long jobId;
    private String action;
}
