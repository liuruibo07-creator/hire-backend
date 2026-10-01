package com.hire.model.vo;

import lombok.Data;
import java.time.LocalDateTime;
/*
 * 通知VO
 */
@Data
public class NotificationVO {
    private Long id;
    private String title;
    private String content;
    private String type;
    private Long relatedId;
    private Integer isRead;
    private LocalDateTime createTime;
}
