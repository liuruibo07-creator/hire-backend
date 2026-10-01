package com.hire.gateway.filter;

import com.hire.common.constant.JwtConstant;
import com.hire.common.utils.JwtUtil;
import com.hire.gateway.props.CloudProperties;
import org.junit.jupiter.api.*;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class UserLoginGlobalFilterTest {
    UserLoginGlobalFilter filter;
    AtomicReference<ServerWebExchange> forwarded;
    @BeforeEach void setup() {
        filter = new UserLoginGlobalFilter(); forwarded = new AtomicReference<>();
        CloudProperties properties = new CloudProperties(); properties.setWhitePaths(List.of(
                "/api/user/users/login", "/api/user/users/register", "/api/job/jobs/search",
                "/api/job/jobs/{id:[0-9]+}", "/api/job/categories"));
        ReflectionTestUtils.setField(filter, "cloudProperties", properties);
    }
    String token(Map<String,Object> claims, long ttl) { return JwtUtil.createJWT(JwtConstant.SECRET_KEY, ttl, new HashMap<>(claims)); }
    MockServerWebExchange run(MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, value -> { forwarded.set(value); return Mono.empty(); }).block();
        return exchange;
    }
    @Test void removesForgedHeadersEvenForWhitelistedRequests() {
        run(MockServerHttpRequest.get("/api/job/jobs/search").header("userId","900").header("role","admin")
                .header("X-User-Id","901").build());
        assertNull(forwarded.get().getRequest().getHeaders().getFirst("userId"));
        assertNull(forwarded.get().getRequest().getHeaders().getFirst("role"));
        assertNull(forwarded.get().getRequest().getHeaders().getFirst("X-User-Id"));
    }
    @Test void publicJobRoutesAreNarrowlyWhitelisted() {
        run(MockServerHttpRequest.get("/api/job/jobs/1234567890123456789").build());
        assertNotNull(forwarded.get());
        forwarded.set(null);
        assertEquals(401, run(MockServerHttpRequest.get("/api/job/jobs/not-an-id").build()).getResponse().getRawStatusCode());
        assertNull(forwarded.get());
        assertEquals(401, run(MockServerHttpRequest.get("/api/job/jobs/my").build()).getResponse().getRawStatusCode());
        assertNull(forwarded.get());
        assertEquals(401, run(MockServerHttpRequest.get("/api/job/admin/jobs").build()).getResponse().getRawStatusCode());
        assertNull(forwarded.get());
    }
    @Test void overwritesIdentityAndClearsForgedRoleWhenClaimMissing() {
        run(MockServerHttpRequest.get("/api/chat/conversations").header("Authorization", "Bearer " + token(Map.of("userId",7),60000))
                .header("userId","900").header("role","admin").header("X-User-Id","901").build());
        assertEquals(List.of("7"), forwarded.get().getRequest().getHeaders().get("userId"));
        assertNull(forwarded.get().getRequest().getHeaders().getFirst("role"));
        assertNull(forwarded.get().getRequest().getHeaders().getFirst("X-User-Id"));
    }
    @Test void websocketProtocolAuthIsRestrictedToChatUpgrade() {
        String protocol="chat.v1, bearer." + token(Map.of("userId",7,"role","seeker"),60000);
        run(MockServerHttpRequest.get("/api/chat/ws").header("Upgrade","websocket").header("Sec-WebSocket-Protocol",protocol).build());
        assertEquals("7",forwarded.get().getRequest().getHeaders().getFirst("userId"));
        forwarded.set(null);
        assertEquals(401,run(MockServerHttpRequest.get("/api/chat/conversations").header("Sec-WebSocket-Protocol",protocol).build()).getResponse().getRawStatusCode());
        assertNull(forwarded.get());
    }
    @Test void rejectsExpiredInvalidAndQueryTokens() {
        String expired=token(Map.of("userId",7,"role","seeker"),-1000);
        assertEquals(401,run(MockServerHttpRequest.get("/api/chat/ws").header("Upgrade","websocket")
                .header("Sec-WebSocket-Protocol","bearer."+expired).build()).getResponse().getRawStatusCode());
        assertEquals(401,run(MockServerHttpRequest.get("/api/chat/conversations").header("Authorization","invalid").build()).getResponse().getRawStatusCode());
        assertEquals(401,run(MockServerHttpRequest.get("/api/chat/ws?token=ignored").header("Upgrade","websocket").build()).getResponse().getRawStatusCode());
        assertNull(forwarded.get());
    }
}
