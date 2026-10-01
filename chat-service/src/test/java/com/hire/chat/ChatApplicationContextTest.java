package com.hire.chat;

import com.hire.api.clients.UserClient;
import com.hire.chat.mapper.ChatMapper;
import com.hire.chat.service.ChatEventRelay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real application wiring, including Feign proxies, MVC, WebSocket, MyBatis and Rabbit beans. */
@SpringBootTest(classes=ChatApplication.class,properties={
        "spring.cloud.bootstrap.enabled=false","spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false","spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.url=jdbc:h2:mem:context;MODE=MySQL",
        "spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class ChatApplicationContextTest {
    @Autowired MockMvc mvc;
    @Autowired UserClient users;
    @Autowired ChatMapper mapper;
    @Autowired ChatEventRelay relay;

    @Test void realApplicationStartsAndRejectsUnauthenticatedRequestsBeforeRemoteCalls() throws Exception {
        assertNotNull(users); assertNotNull(mapper); assertNotNull(relay);
        mvc.perform(get("/conversations").header("userId","10").header("role","seeker"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }
}
