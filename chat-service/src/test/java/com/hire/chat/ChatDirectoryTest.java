package com.hire.chat;

import com.hire.api.clients.ApplicationClient;
import com.hire.api.clients.JobClient;
import com.hire.api.clients.UserClient;
import com.hire.chat.exception.ChatException;
import com.hire.chat.service.ChatDirectory;
import com.hire.common.domain.Result;
import com.hire.model.vo.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatDirectoryTest {
    UserClient users=mock(UserClient.class);
    JobClient jobs=mock(JobClient.class);
    ApplicationClient applications=mock(ApplicationClient.class);
    ChatDirectory directory=new ChatDirectory(users,jobs,applications);
    UserInfoVO user(int status,String role) {
        UserInfoVO u=new UserInfoVO(); u.setId(10L); u.setStatus(status); u.setRole(role); return u;
    }
    @Test void usesActualUserAndJobResponseCodes() {
        when(users.getUserInfo(10L)).thenReturn(Result.success(user(1,"seeker")));
        assertEquals("seeker",directory.activeUser(10).getRole());
        when(jobs.getJobInfo(30L)).thenReturn(com.hire.common.Result.success(JobInfoVO.builder().id(30L).employerId(20L).status(1).build()));
        assertEquals(20L,directory.job(30).getEmployerId());
        when(applications.getChatContext(40L,"Bearer jwt")).thenReturn(Result.success(new ApplicationChatContextVO(40L,30L,10L,20L)));
        assertEquals(10L,directory.application(40,"jwt").getSeekerId());
    }
    @Test void rejectsDisabledDeletedWrongRoleAndMissingDependencyData() {
        when(users.getUserInfo(10L)).thenReturn(Result.success(user(0,"seeker")));
        assertEquals(403,assertThrows(ChatException.class,()->directory.activeUser(10)).getCode());
        when(users.getUserInfo(10L)).thenReturn(Result.error(404,"deleted"));
        assertEquals(403,assertThrows(ChatException.class,()->directory.activeUser(10)).getCode());
        when(users.getUserInfo(10L)).thenReturn(Result.success(user(1,"admin")));
        assertEquals(403,assertThrows(ChatException.class,()->directory.activeUser(10)).getCode());
        when(users.getUserInfo(10L)).thenReturn(Result.success(user(1,"employer")));
        assertEquals(403,assertThrows(ChatException.class,()->directory.requireRole(10,"seeker")).getCode());
        when(users.getUserInfo(10L)).thenReturn(null);
        assertEquals(503,assertThrows(ChatException.class,()->directory.activeUser(10)).getCode());
    }
}
