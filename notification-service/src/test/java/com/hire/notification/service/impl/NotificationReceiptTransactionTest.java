package com.hire.notification.service.impl;

import com.hire.model.dto.ApplicationSubmittedEvent;
import com.hire.model.dto.ApplicationStatusChangedEvent;
import com.hire.notification.mapper.NotificationMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NotificationReceiptTransactionTest {
    JdbcTemplate jdbc;
    TransactionTemplate transactions;
    NotificationServiceImpl service;
    ApplicationSubmittedEvent event;

    @BeforeEach void setup() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE t_notification_event (message_id VARCHAR(64) PRIMARY KEY)");
        jdbc.execute("CREATE TABLE t_notification (id BIGINT PRIMARY KEY,user_id BIGINT,title VARCHAR(200),"
                + "content VARCHAR(3000),type VARCHAR(100),related_id BIGINT,is_read INT,deleted INT,"
                + "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP)");
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean(); factory.setDataSource(source);
        factory.setMapperLocations(new ClassPathResource("mapper/NotificationMapper.xml"));
        SqlSessionFactory sessions = factory.getObject();
        service = new NotificationServiceImpl();
        ReflectionTestUtils.setField(service,"notificationMapper",new SqlSessionTemplate(sessions).getMapper(NotificationMapper.class));
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        event = ApplicationSubmittedEvent.builder().messageId("event-1").applicationId(10L).employerId(1L).build();
    }

    private void consume() {
        transactions.execute(status -> { service.handleApplicationSubmitted(event); return null; });
    }

    @Test void redeliveryProducesOneNotificationAndAccurateUnreadCount() {
        consume(); consume();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM t_notification", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM t_notification_event", Integer.class));
        assertEquals(1, service.countUnread(1L));
        service.markAllAsRead(1L);
        assertEquals(0, service.countUnread(1L));
        event.setMessageId("event-2"); consume();
        assertEquals(1, service.countUnread(1L));
    }

    @Test void failedTransactionReleasesReceiptForRedelivery() {
        assertThrows(IllegalStateException.class, () -> transactions.execute(status -> {
            service.handleApplicationSubmitted(event);
            throw new IllegalStateException("simulate transaction failure");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM t_notification_event", Integer.class));
        assertEquals(0, service.countUnread(1L));
        consume();
        assertEquals(1, service.countUnread(1L));
    }

    @Test void statusEventIsAlsoIdempotent() {
        ApplicationStatusChangedEvent changed = ApplicationStatusChangedEvent.builder()
                .messageId("status-1").applicantId(2L).applicationId(10L).status(2).build();
        for (int i = 0; i < 2; i++) transactions.execute(status -> {
            service.handleApplicationStatusChanged(changed); return null;
        });
        assertEquals(1, service.countUnread(2L));
    }
}
