package com.hire.notification.service.impl;

import com.hire.common.utils.RedisUtil;
import com.hire.model.dto.ApplicationSubmittedEvent;
import com.hire.model.dto.NotificationQueryDTO;
import com.hire.model.entity.Notification;
import com.hire.model.vo.PageVO;
import com.hire.notification.mapper.NotificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private ApplicationSubmittedEvent event;

    @BeforeEach
    void setUp() {
        event = ApplicationSubmittedEvent.builder()
                .messageId("msg-001")
                .applicationId(100L)
                .jobId(200L)
                .jobTitle("Java开发工程师")
                .employerId(1L)
                .applicantId(2L)
                .resumeId(300L)
                .coverLetter("您好，我对该职位很感兴趣")
                .build();
    }

    // ========== handleApplicationSubmitted ==========

    @Test
    void handleApplicationSubmitted_success() {
        when(notificationMapper.insert(any(Notification.class))).thenReturn(1);

        notificationService.handleApplicationSubmitted(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(captor.capture());

        Notification n = captor.getValue();
        assertEquals(1L, n.getUserId());
        assertEquals("application_submitted", n.getType());
        assertEquals("收到新的简历投递", n.getTitle());
        assertTrue(n.getContent().contains("Java开发工程师"));
        assertEquals(100L, n.getRelatedId());
        assertEquals(0, n.getIsRead());
        assertEquals(0, n.getDeleted());
        assertNotNull(n.getId());
    }

    @Test
    void handleApplicationSubmitted_nullEvent_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            notificationService.handleApplicationSubmitted(null);
        });
        verify(notificationMapper, never()).insert(any());
    }

    @Test
    void handleApplicationSubmitted_nullEmployerId_throwsException() {
        event.setEmployerId(null);
        assertThrows(IllegalArgumentException.class, () -> {
            notificationService.handleApplicationSubmitted(event);
        });
        verify(notificationMapper, never()).insert(any());
    }

    @Test
    void handleApplicationSubmitted_blankMessageId_throwsException() {
        event.setMessageId("");
        assertThrows(IllegalArgumentException.class, () -> {
            notificationService.handleApplicationSubmitted(event);
        });
        verify(notificationMapper, never()).insert(any());
    }

    @Test
    void handleApplicationSubmitted_blankJobTitle_usesEmptyString() {
        event.setJobTitle("");
        when(notificationMapper.insert(any(Notification.class))).thenReturn(1);

        notificationService.handleApplicationSubmitted(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(captor.capture());
        assertTrue(captor.getValue().getContent().contains("《》"));
    }

    // ========== pageNotifications ==========

    @Test
    void pageNotifications_firstPage_correctOffset() {
        NotificationQueryDTO dto = new NotificationQueryDTO();
        dto.setUserId(1L);
        dto.setPage(1);
        dto.setSize(10);

        when(notificationMapper.countList(eq(1L), eq(null), eq(null))).thenReturn(2L);
        List<Notification> expected = Arrays.asList(
                Notification.builder().id(1L).title("通知1").build(),
                Notification.builder().id(2L).title("通知2").build());
        when(notificationMapper.selectList(eq(1L), eq(null), eq(null), eq(0), eq(10)))
                .thenReturn(expected);

        PageVO<Notification> result = notificationService.pageNotifications(dto);

        assertEquals(2L, result.getTotal());
        assertEquals(2, result.getList().size());
        assertEquals("通知1", result.getList().get(0).getTitle());
    }

    @Test
    void pageNotifications_secondPage_correctOffset() {
        NotificationQueryDTO dto = new NotificationQueryDTO();
        dto.setUserId(1L);
        dto.setPage(2);
        dto.setSize(5);

        when(notificationMapper.countList(eq(1L), eq(null), eq(null))).thenReturn(6L);
        when(notificationMapper.selectList(eq(1L), eq(null), eq(null), eq(5), eq(5)))
                .thenReturn(Collections.emptyList());

        PageVO<Notification> result = notificationService.pageNotifications(dto);

        assertEquals(6L, result.getTotal());
        assertTrue(result.getList().isEmpty());
        verify(notificationMapper).selectList(eq(1L), eq(null), eq(null), eq(5), eq(5));
    }

    @Test
    void pageNotifications_zeroTotal_skipsListQuery() {
        NotificationQueryDTO dto = new NotificationQueryDTO();
        dto.setUserId(1L);

        when(notificationMapper.countList(eq(1L), eq(null), eq(null))).thenReturn(0L);

        PageVO<Notification> result = notificationService.pageNotifications(dto);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
        // total 为 0 时不应再查列表，省一次 SQL
        verify(notificationMapper, never()).selectList(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void pageNotifications_withConditions_passesAllParams() {
        NotificationQueryDTO dto = new NotificationQueryDTO();
        dto.setUserId(1L);
        dto.setType("application_submitted");
        dto.setIsRead(0);
        dto.setPage(1);
        dto.setSize(10);

        when(notificationMapper.countList(eq(1L), eq("application_submitted"), eq(0))).thenReturn(1L);
        when(notificationMapper.selectList(eq(1L), eq("application_submitted"), eq(0), eq(0), eq(10)))
                .thenReturn(Collections.singletonList(Notification.builder().id(9L).build()));

        notificationService.pageNotifications(dto);

        verify(notificationMapper).countList(eq(1L), eq("application_submitted"), eq(0));
        verify(notificationMapper).selectList(eq(1L), eq("application_submitted"), eq(0), eq(0), eq(10));
    }

    @Test
    void pageNotifications_invalidPageAndSize_fallsBackToDefault() {
        NotificationQueryDTO dto = new NotificationQueryDTO();
        dto.setUserId(1L);
        dto.setPage(0);
        dto.setSize(-5);

        when(notificationMapper.countList(eq(1L), eq(null), eq(null))).thenReturn(1L);
        when(notificationMapper.selectList(eq(1L), eq(null), eq(null), eq(0), eq(10)))
                .thenReturn(Collections.singletonList(Notification.builder().id(1L).build()));

        notificationService.pageNotifications(dto);

        // page/size 非法时兜底为 1/10，offset=0
        verify(notificationMapper).selectList(eq(1L), eq(null), eq(null), eq(0), eq(10));
    }

    // ========== countUnread ==========

    @Test
    void countUnread_normal() {
        when(redisUtil.getLong("notify:unread:1")).thenReturn(null);
        when(notificationMapper.countUnread(1L)).thenReturn(5);
        assertEquals(5, notificationService.countUnread(1L));
    }

    @Test
    void countUnread_zero() {
        when(redisUtil.getLong("notify:unread:1")).thenReturn(null);
        when(notificationMapper.countUnread(1L)).thenReturn(0);
        assertEquals(0, notificationService.countUnread(1L));
    }

    // ========== markAsRead ==========

    @Test
    void markAsRead_passesIdAndUserId() {
        notificationService.markAsRead(100L, 1L);
        verify(notificationMapper).markAsRead(100L, 1L);
    }

    // ========== markAllAsRead ==========

    @Test
    void markAllAsRead() {
        notificationService.markAllAsRead(1L);
        verify(notificationMapper).markAllAsRead(1L);
    }
}
