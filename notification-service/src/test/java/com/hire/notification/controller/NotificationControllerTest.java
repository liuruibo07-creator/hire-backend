package com.hire.notification.controller;

import com.hire.common.context.UserContext;
import com.hire.model.dto.NotificationQueryDTO;
import com.hire.model.entity.Notification;
import com.hire.model.vo.PageVO;
import com.hire.notification.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 通知控制器测试（接口文档第 5 章路径 + {code,message,data} 响应结构）
 */
@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(notificationController).build();
        // 模拟网关透传 userId 请求头经拦截器写入的登录态
        UserContext.setCurrentUserId(1L);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeCurrentUserId();
    }

    // ========== 5.1 list ==========

    @Test
    void list_returnsPage() throws Exception {
        PageVO<Notification> page = new PageVO<>(2L, Arrays.asList(
                Notification.builder().id(1L).title("通知1").build(),
                Notification.builder().id(2L).title("通知2").build()));
        when(notificationService.pageNotifications(any(NotificationQueryDTO.class))).thenReturn(page);

        mockMvc.perform(get("/notifications").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list[0].title").value("通知1"))
                .andExpect(jsonPath("$.data.list[1].title").value("通知2"));
    }

    @Test
    void list_emptyPage() throws Exception {
        when(notificationService.pageNotifications(any(NotificationQueryDTO.class)))
                .thenReturn(new PageVO<>(0L, Collections.emptyList()));

        mockMvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.list").isEmpty());
    }

    @Test
    void list_withFilter_passesUserIdFromContext() throws Exception {
        when(notificationService.pageNotifications(any(NotificationQueryDTO.class)))
                .thenReturn(new PageVO<>(0L, Collections.emptyList()));

        mockMvc.perform(get("/notifications")
                        .param("type", "application_submitted")
                        .param("isRead", "0"))
                .andExpect(status().isOk());

        // userId 必须来自登录态(UserContext)，而不是前端传参
        verify(notificationService).pageNotifications(org.mockito.ArgumentMatchers.argThat(dto ->
                Long.valueOf(1L).equals(dto.getUserId())
                        && "application_submitted".equals(dto.getType())
                        && Integer.valueOf(0).equals(dto.getIsRead())));
    }

    @Test
    void list_notLogin_returns401() throws Exception {
        UserContext.removeCurrentUserId();

        mockMvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(notificationService);
    }

    // ========== 5.2 unread-count ==========

    @Test
    void unreadCount_returnsCount() throws Exception {
        when(notificationService.countUnread(1L)).thenReturn(3);

        mockMvc.perform(get("/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(3));
    }

    @Test
    void unreadCount_notLogin_returns401() throws Exception {
        UserContext.removeCurrentUserId();

        mockMvc.perform(get("/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(notificationService);
    }

    // ========== 5.3 mark read ==========

    @Test
    void markRead_success() throws Exception {
        mockMvc.perform(put("/notifications/1001/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 标记已读必须限定本人 userId，防止越权
        verify(notificationService).markAsRead(1001L, 1L);
    }

    @Test
    void markRead_notLogin_returns401() throws Exception {
        UserContext.removeCurrentUserId();

        mockMvc.perform(put("/notifications/1001/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        verify(notificationService, never()).markAsRead(any(), any());
    }

    // ========== 5.4 read-all ==========

    @Test
    void markAllRead_success() throws Exception {
        mockMvc.perform(put("/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(notificationService).markAllAsRead(eq(1L));
    }
}
