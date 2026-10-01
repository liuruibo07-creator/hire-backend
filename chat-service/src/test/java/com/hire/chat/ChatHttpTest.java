package com.hire.chat;

import com.hire.chat.controller.ChatController;
import com.hire.chat.exception.*;
import com.hire.chat.security.*;
import com.hire.chat.service.*;
import com.hire.model.dto.*;
import com.hire.model.entity.ChatMessage;
import com.hire.model.vo.ChatViews;
import com.hire.common.constant.JwtConstant;
import com.hire.common.utils.JwtUtil;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChatHttpTest {
    ChatDirectory directory;
    ChatService service;
    MockMvc mvc;
    @BeforeEach void setup() {
        directory=mock(ChatDirectory.class); service=mock(ChatService.class);
        mvc=MockMvcBuilders.standaloneSetup(new ChatController(service)).setControllerAdvice(new ChatExceptionHandler())
                .addInterceptors(new ChatAuthInterceptor(new ChatAuthentication(directory))).build();
    }
    String auth(long userId,String role,long ttl) {
        return "Bearer "+JwtUtil.createJWT(JwtConstant.SECRET_KEY,ttl,new java.util.HashMap<>(Map.of("userId",userId,"role",role)));
    }
    @Test void rejectsMissingInvalidExpiredTokensAndForgedHeaders() throws Exception {
        mvc.perform(get("/conversations").header("userId","10").header("role","seeker")).andExpect(status().isUnauthorized());
        mvc.perform(get("/conversations").header("Authorization","invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get("/conversations").header("Authorization",auth(10,"seeker",-1000))).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void rejectsAdminAndDisabledAccount() throws Exception {
        mvc.perform(get("/conversations").header("Authorization",auth(10,"admin",60000))).andExpect(status().isForbidden());
        doThrow(new ChatException(403,"账号已停用")).when(directory).requireRole(10,"seeker");
        mvc.perform(get("/conversations").header("Authorization",auth(10,"seeker",60000)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        verifyNoInteractions(service);
    }
    @Test void derivesIdentityFromTokenAndKeepsIdsAsStrings() throws Exception {
        ChatMessage message=new ChatMessage(); message.setId(1L); message.setConversationId(2L);
        message.setSenderId(9007199254740993L); message.setContent("你好");
        when(service.send(any(),eq(2L),any())).thenReturn(message);
        mvc.perform(post("/conversations/2/messages").header("Authorization",auth(9007199254740993L,"seeker",60000))
                .header("userId","999").header("role","employer").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"你好\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").exists()).andExpect(jsonPath("$.msg").doesNotExist())
                .andExpect(jsonPath("$.data.id").value("1")).andExpect(jsonPath("$.data.senderId").value("9007199254740993"));
        verify(service).send(argThat(p->p.userId()==9007199254740993L && p.role().equals("seeker")),eq(2L),any());
    }
    @Test void invalidJsonAndServiceErrorsHaveDocumentedEnvelope() throws Exception {
        mvc.perform(post("/conversations").header("Authorization",auth(10,"seeker",60000)).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        when(service.unread(any())).thenThrow(new ChatException(503,"依赖服务暂不可用"));
        mvc.perform(get("/unread-count").header("Authorization",auth(10,"seeker",60000)))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(503));
    }
}
