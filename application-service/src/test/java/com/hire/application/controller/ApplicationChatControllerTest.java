package com.hire.application.controller;

import com.hire.application.mapper.ApplicationsMapper;
import com.hire.common.constant.JwtConstant;
import com.hire.common.utils.JwtUtil;
import com.hire.model.entity.Applications;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApplicationChatControllerTest {
    ApplicationsMapper mapper;
    MockMvc mvc;
    @BeforeEach void setup() {
        mapper=mock(ApplicationsMapper.class);
        mvc=MockMvcBuilders.standaloneSetup(new ApplicationChatController(mapper)).build();
        when(mapper.selectById(3L)).thenReturn(Applications.builder().id(3L).userId(10L).employerId(20L).jobId(30L).build());
    }
    String auth(long id,String role) { return "Bearer "+JwtUtil.createJWT(JwtConstant.SECRET_KEY,60000,new java.util.HashMap<>(Map.of("userId",id,"role",role))); }
    @Test void returnsOnlyMinimalRelationshipToOwningEmployer() throws Exception {
        mvc.perform(get("/applications/3/chat-context/internal").header("Authorization",auth(20,"employer")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.seekerId").value(10)).andExpect(jsonPath("$.data.employerId").value(20))
                .andExpect(jsonPath("$.data.jobId").value(30)).andExpect(jsonPath("$.data.resume").doesNotExist());
    }
    @Test void deniesForgedHeadersWrongRoleAndOtherEmployer() throws Exception {
        mvc.perform(get("/applications/3/chat-context/internal").header("userId","20").header("role","employer"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/applications/3/chat-context/internal").header("Authorization",auth(10,"seeker")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/applications/3/chat-context/internal").header("Authorization",auth(21,"employer")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/applications/404/chat-context/internal").header("Authorization",auth(20,"employer")))
                .andExpect(status().isNotFound());
    }
}
