package com.hire.gateway.filter;

import com.hire.common.constant.JwtConstant;
import com.hire.common.utils.JwtUtil;
import com.hire.gateway.props.CloudProperties;
import io.netty.handler.codec.http.HttpHeaders;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.*;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.WebsocketClientSpec;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.WebsocketServerSpec;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=ChatWebSocketProxyTest.Gateway.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"spring.cloud.bootstrap.enabled=false","spring.cloud.nacos.config.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false","spring.main.web-application-type=reactive"})
class ChatWebSocketProxyTest {
    static final AtomicReference<HttpHeaders> forwarded=new AtomicReference<>();
    static final DisposableServer backend=HttpServer.create().host("127.0.0.1").port(0).route(routes->routes.get("/ws",(request,response)->{
        forwarded.set(request.requestHeaders().copy());
        return response.sendWebsocket((in,out)->out.sendString(Mono.just("connected")),
                WebsocketServerSpec.builder().protocols("chat.v1").build());
    })).bindNow();
    @Configuration @org.springframework.boot.test.context.TestComponent
    @EnableAutoConfiguration @Import(UserLoginGlobalFilter.class)
    static class Gateway {
        @Bean CloudProperties properties() { CloudProperties p=new CloudProperties(); p.setWhitePaths(List.of()); return p; }
        @Bean RouteLocator routes(RouteLocatorBuilder builder) {
            return builder.routes().route("chat-test",r->r.path("/api/chat/ws")
                    .filters(f->f.stripPrefix(2).preserveHostHeader()).uri("ws://127.0.0.1:"+backend.port())).build();
        }
    }
    @LocalServerPort int port;
    @AfterAll static void stop() { backend.disposeNow(); }
    @Test void forwardsBrowserSubprotocolAndAuthenticatedIdentityThroughGateway() {
        String token=JwtUtil.createJWT(JwtConstant.SECRET_KEY,60000,new HashMap<>(Map.of("userId",7,"role","seeker")));
        String message=HttpClient.create().headers(h->{h.set("userId","999");h.set("role","admin");h.set("X-User-Id","999");})
                .websocket(WebsocketClientSpec.builder().protocols("chat.v1,bearer."+token).build())
                .uri("ws://127.0.0.1:"+port+"/api/chat/ws")
                .handle((in,out)->in.receive().asString()).next().block(Duration.ofSeconds(10));
        assertEquals("connected",message);
        assertEquals("7",forwarded.get().get("userId")); assertEquals("seeker",forwarded.get().get("role"));
        assertNull(forwarded.get().get("X-User-Id"));
        assertTrue(forwarded.get().get("Sec-WebSocket-Protocol").contains("bearer."+token));
    }
}
