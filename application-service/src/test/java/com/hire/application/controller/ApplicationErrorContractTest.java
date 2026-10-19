package com.hire.application.controller;

import com.hire.application.handler.GlobalExceptionHandler;
import com.hire.application.service.ApplicationsService;
import com.hire.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApplicationErrorContractTest {
    private ApplicationsService service;
    private MockMvc mvc;

    @BeforeEach void setup() {
        service = mock(ApplicationsService.class);
        ApplicationsController controller = new ApplicationsController();
        ReflectionTestUtils.setField(controller, "applicationsService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void publicBusinessErrorUsesMessageField() throws Exception {
        when(service.submitApplication(any())).thenThrow(new BusinessException("已投递过该职位，请勿重复投递"));
        mvc.perform(post("/applications").contentType(MediaType.APPLICATION_JSON).content("{\"jobId\":1,\"resumeId\":2}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("已投递过该职位，请勿重复投递"))
                .andExpect(jsonPath("$.msg").doesNotExist());
    }

    @Test void wrappedBusinessErrorPreservesMessage() throws Exception {
        when(service.getApplicationDetail(1L)).thenThrow(new RuntimeException(new BusinessException("无权查看该投递详情")));
        mvc.perform(get("/applications/1")).andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("无权查看该投递详情"));
    }

    @Test void duplicateAndMalformedInputUsePublicContract() throws Exception {
        when(service.getApplicationDetail(1L)).thenThrow(new DuplicateKeyException("private database detail"));
        mvc.perform(get("/applications/1")).andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("记录已存在，请勿重复操作"));
        mvc.perform(get("/applications/not-a-number")).andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求参数不合法"));
        mvc.perform(post("/applications").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求参数不合法"));
    }
}
