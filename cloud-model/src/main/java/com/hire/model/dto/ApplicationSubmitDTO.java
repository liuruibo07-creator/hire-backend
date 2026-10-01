package com.hire.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 投递简历请求参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationSubmitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 职位ID */
    private Long jobId;

    /** 简历ID */
    private Long resumeId;

    /** 求职附言，选填 */
    private String coverLetter;
}
