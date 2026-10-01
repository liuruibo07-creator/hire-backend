package com.hire.model.dto;

import lombok.Data;
/*
* 通知查询DTO
 */
@Data
public class NotificationQueryDTO {
    private Long userId;
    private String type;
    private Integer isRead;
    private Integer page = 1;
    private Integer size = 10;
}
