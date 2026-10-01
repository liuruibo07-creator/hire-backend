package com.hire.notification.listener;

import com.hire.model.dto.ApplicationSubmittedEvent;
import com.hire.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationSubmittedListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ApplicationSubmittedListener listener;

    private ApplicationSubmittedEvent buildEvent() {
        return ApplicationSubmittedEvent.builder()
                .messageId("msg-001")
                .applicationId(100L)
                .jobId(200L)
                .jobTitle("Java开发工程师")
                .employerId(1L)
                .applicantId(2L)
                .resumeId(300L)
                .coverLetter("您好")
                .build();
    }

    @Test
    void onApplicationSubmitted_success_callsService() {
        ApplicationSubmittedEvent event = buildEvent();

        listener.onApplicationSubmitted(event);

        verify(notificationService).handleApplicationSubmitted(event);
    }

    @Test
    void onApplicationSubmitted_serviceThrows_exceptionPropagates() {
        ApplicationSubmittedEvent event = buildEvent();
        doThrow(new RuntimeException("DB error")).when(notificationService).handleApplicationSubmitted(event);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            listener.onApplicationSubmitted(event);
        });
        assertEquals("DB error", ex.getMessage());
    }

    @Test
    void onApplicationSubmitted_eventFieldsPassedCorrectly() {
        ApplicationSubmittedEvent event = buildEvent();

        listener.onApplicationSubmitted(event);

        verify(notificationService).handleApplicationSubmitted(argThat(e ->
                "msg-001".equals(e.getMessageId())
                        && Long.valueOf(100L).equals(e.getApplicationId())
                        && Long.valueOf(1L).equals(e.getEmployerId())
                        && "Java开发工程师".equals(e.getJobTitle())
        ));
    }

    @Test
    void onApplicationSubmitted_nullEvent_throwsNPE() {
        assertThrows(NullPointerException.class, () -> {
            listener.onApplicationSubmitted(null);
        });
    }

    @Test
    void onApplicationSubmitted_serviceCalledExactlyOnce() {
        ApplicationSubmittedEvent event = buildEvent();

        listener.onApplicationSubmitted(event);

        verify(notificationService, times(1)).handleApplicationSubmitted(event);
    }
}
