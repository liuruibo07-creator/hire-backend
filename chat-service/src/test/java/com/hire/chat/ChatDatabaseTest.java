package com.hire.chat;

import com.hire.chat.mapper.ChatMapper;
import com.hire.model.dto.*;
import com.hire.model.entity.*;
import com.hire.chat.exception.ChatException;
import com.hire.chat.service.*;
import com.hire.chat.websocket.ChatSessions;
import com.hire.common.utils.RedisUtil;
import com.hire.model.vo.*;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Uses the deployment DDL. Optional CHAT_TEST_MYSQL_URL must point to a dedicated disposable database. */
class ChatDatabaseTest {
    static AnnotationConfigApplicationContext context;
    static DataSource dataSource;
    static ChatDirectory directory;
    static RabbitTemplate rabbit;
    ChatService service;
    ChatMapper mapper;
    final ChatPrincipal seeker=new ChatPrincipal(10,"seeker",Long.MAX_VALUE,"test");
    final ChatPrincipal employer=new ChatPrincipal(20,"employer",Long.MAX_VALUE,"test");

    @org.springframework.boot.test.context.TestConfiguration @EnableTransactionManagement
    static class DatabaseConfig {
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            SqlSessionFactoryBean factory=new SqlSessionFactoryBean(); factory.setDataSource(ds);
            org.apache.ibatis.session.Configuration config=new org.apache.ibatis.session.Configuration();
            config.setMapUnderscoreToCamelCase(true); factory.setConfiguration(config);
            return factory.getObject();
        }
        @Bean MapperFactoryBean<ChatMapper> mapper(SqlSessionFactory factory) {
            MapperFactoryBean<ChatMapper> bean=new MapperFactoryBean<>(ChatMapper.class); bean.setSqlSessionFactory(factory); return bean;
        }
        @Bean PlatformTransactionManager transactions(DataSource ds) { return new DataSourceTransactionManager(ds); }
    }

    @BeforeAll static void open() {
        String url=System.getenv("CHAT_TEST_MYSQL_URL");
        dataSource=url==null ? new DriverManagerDataSource("jdbc:h2:mem:chat;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","sa","")
                : new DriverManagerDataSource(url,System.getenv().getOrDefault("CHAT_TEST_MYSQL_USER","root"),System.getenv().getOrDefault("CHAT_TEST_MYSQL_PASSWORD",""));
        new ResourceDatabasePopulator(new FileSystemResource(Path.of("../sql/chat.sql"))).execute(dataSource);
        directory=mock(ChatDirectory.class); rabbit=mock(RabbitTemplate.class);
        context=new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class,()->dataSource);
        context.registerBean(ChatDirectory.class,()->directory);
        context.registerBean(RabbitTemplate.class,()->rabbit);
        context.registerBean(RedisUtil.class,()-> {
            RedisUtil redis = mock(RedisUtil.class);
            when(redis.getLong(anyString())).thenReturn(null);
            return redis;
        });
        context.registerBean(ChatSessions.class,()->mock(ChatSessions.class));
        context.registerBean(com.fasterxml.jackson.databind.ObjectMapper.class,
                () -> new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
        context.register(DatabaseConfig.class, ChatService.class, ChatEventRelay.class); context.refresh();
    }
    @AfterAll static void close() { if(context!=null) context.close(); }
    @BeforeEach void setup() {
        JdbcTemplate jdbc=new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM t_chat_message"); jdbc.update("DELETE FROM t_chat_conversation");
        reset(directory,rabbit);
        service=context.getBean(ChatService.class); mapper=context.getBean(ChatMapper.class);
        when(directory.job(30L)).thenReturn(JobInfoVO.builder().id(30L).employerId(20L).status(1).title("工程师").build());
        when(directory.application(40L,"test")).thenReturn(new ApplicationChatContextVO(40L,30L,10L,20L));
    }
    ChatConversation start() { return service.start(seeker,new ChatRequests.Start(30L,null)); }
    ChatMessage send(ChatPrincipal actor,long id,String text) { return service.send(actor,id,new ChatRequests.Send(text)); }
    void error(int code,org.junit.jupiter.api.function.Executable action) { assertEquals(code,assertThrows(ChatException.class,action).getCode()); }

    @Test void bothRolesReuseSameConversationAndDifferentJobsStaySeparate() {
        ChatConversation first=start();
        assertEquals(first.getId(),service.start(employer,new ChatRequests.Start(null,40L)).getId());
        when(directory.job(31L)).thenReturn(JobInfoVO.builder().id(31L).employerId(20L).status(1).title("另一职位").build());
        assertNotEquals(first.getId(),service.start(seeker,new ChatRequests.Start(31L,null)).getId());
        assertEquals(2,service.list(seeker,1,20).total());
    }
    @Test void concurrentCreationProducesOneConversation() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(8);
        try {
            CountDownLatch start=new CountDownLatch(1);
            List<Future<Long>> results=new ArrayList<>();
            for(int i=0;i<16;i++) results.add(pool.submit(()->{start.await(); return start().getId();}));
            start.countDown(); Set<Long> ids=new HashSet<>();
            for(Future<Long> result:results) ids.add(result.get(20,TimeUnit.SECONDS));
            assertEquals(1,ids.size()); assertEquals(1,mapper.count(10));
        } finally { pool.shutdownNow(); }
    }
    @Test void concurrentMessagesStayOrderedAndReadCursorDoesNotRegress() throws Exception {
        long id=start().getId(); ExecutorService pool=Executors.newFixedThreadPool(8);
        try {
            List<Future<ChatMessage>> results=new ArrayList<>();
            for(int i=0;i<24;i++) { int n=i; results.add(pool.submit(()->send(seeker,id,"message-"+n))); }
            Set<Long> ids=new TreeSet<>();
            for(Future<ChatMessage> result:results) ids.add(result.get(20,TimeUnit.SECONDS).getId());
            assertEquals(24,ids.size()); assertEquals(24,service.unread(employer)); assertEquals(0,service.unread(seeker));
            ChatViews.Messages history=service.history(employer,id,null,null,100);
            assertEquals(new ArrayList<>(ids),history.list().stream().map(ChatMessage::getId).toList());
            assertEquals(Collections.max(ids),mapper.find(id).getLastMessageId());
            long first=Collections.min(ids),last=Collections.max(ids);
            service.read(employer,id,new ChatRequests.Read(last));
            service.read(employer,id,new ChatRequests.Read(first));
            assertEquals(last,mapper.find(id).getEmployerReadId()); assertEquals(0,service.unread(employer));
            send(seeker,id,"new"); assertEquals(1,service.unread(employer));
            assertEquals(1,service.list(employer,1,20).list().get(0).getUnreadCount());
        } finally { pool.shutdownNow(); }
    }
    @Test void historyAndReconnectPaginationNeitherSkipNorDuplicate() {
        long id=start().getId(); List<Long> expected=new ArrayList<>();
        for(int i=0;i<7;i++) expected.add(send(seeker,id,"text-"+i).getId());
        ChatViews.Messages newest=service.history(employer,id,null,null,3);
        assertTrue(newest.hasMore()); assertEquals(expected.subList(4,7),newest.list().stream().map(ChatMessage::getId).toList());
        ChatViews.Messages older=service.history(employer,id,Long.valueOf(newest.nextBeforeId()),null,3);
        assertEquals(expected.subList(1,4),older.list().stream().map(ChatMessage::getId).toList());
        List<Long> recovered=new ArrayList<>(); long after=0;
        while(true) {
            ChatViews.Messages page=service.history(employer,id,null,after,2);
            recovered.addAll(page.list().stream().map(ChatMessage::getId).toList());
            if(!page.hasMore()) break;
            after=Long.parseLong(page.nextAfterId());
        }
        assertEquals(expected,recovered);
    }
    @Test void rejectsOtherParticipantsAndForeignReadPositions() {
        long id=start().getId(); long messageId=send(seeker,id,"private").getId();
        ChatPrincipal outsider=new ChatPrincipal(11,"seeker",Long.MAX_VALUE,"test");
        error(403,()->service.history(outsider,id,null,null,20));
        error(403,()->send(outsider,id,"attack"));
        error(403,()->service.read(outsider,id,new ChatRequests.Read(messageId)));
        assertEquals(0,service.list(outsider,1,20).total());
        when(directory.job(31L)).thenReturn(JobInfoVO.builder().id(31L).employerId(20L).status(1).title("other").build());
        long other=service.start(seeker,new ChatRequests.Start(31L,null)).getId();
        long foreign=send(seeker,other,"different").getId();
        error(400,()->service.read(employer,id,new ChatRequests.Read(foreign)));
        error(400,()->service.history(employer,id,1L,1L,20));
    }
    @Test void offlineJobAllowsExistingChatButPreventsNewSeekerChat() {
        long id=start().getId();
        when(directory.job(30L)).thenReturn(JobInfoVO.builder().id(30L).employerId(20L).status(0).title("closed").build());
        assertEquals(id,start().getId()); assertNotNull(send(seeker,id,"follow up"));
        error(409,()->service.start(new ChatPrincipal(11,"seeker",Long.MAX_VALUE,"test"),new ChatRequests.Start(30L,null)));
    }
    @Test void verifiesApplicationOwnershipAndJobOwner() {
        when(directory.application(40L,"test")).thenReturn(new ApplicationChatContextVO(40L,30L,10L,21L));
        error(403,()->service.start(employer,new ChatRequests.Start(null,40L)));
        when(directory.application(40L,"test")).thenReturn(new ApplicationChatContextVO(40L,30L,10L,20L));
        when(directory.job(30L)).thenReturn(JobInfoVO.builder().id(30L).employerId(21L).status(1).title("wrong").build());
        error(403,()->service.start(employer,new ChatRequests.Start(null,40L)));
        error(400,()->service.start(employer,new ChatRequests.Start(30L,null)));
    }
    @Test void validatesTextAndDisabledRecipient() {
        long id=start().getId();
        error(400,()->send(seeker,id," \n "));
        error(400,()->send(seeker,id,"a".repeat(2001)));
        assertEquals(2000,send(seeker,id,"a".repeat(2000)).getContent().length());
        doThrow(new ChatException(403,"disabled")).when(directory).requireRole(20,"employer");
        error(403,()->send(seeker,id,"cannot send"));
    }
    @Test void rollbackDoesNotPublishAndBrokerFailureDoesNotUndoCommittedMessage() {
        long id=start().getId(); clearInvocations(rabbit);
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).execute(status->{
            send(seeker,id,"rollback");
            verifyNoInteractions(rabbit);
            status.setRollbackOnly(); return null;
        });
        assertTrue(service.history(employer,id,null,null,20).list().isEmpty()); verifyNoInteractions(rabbit);
        doThrow(new AmqpConnectException(new java.net.ConnectException("test"))).when(rabbit).convertAndSend(anyString(),anyString(),any(Object.class));
        ChatMessage saved=send(seeker,id,"persisted");
        assertEquals(saved.getId(),service.history(employer,id,null,null,20).list().get(0).getId());
        verify(rabbit).convertAndSend(eq("hire.chat.events"),eq(""),any(ChatBroadcast.class));
    }
}
