package com.hire.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hire.application.mapper.OutboxMapper;
import com.hire.common.constant.MqConstants;
import io.seata.core.context.RootContext;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationOutboxTransactionTest {
    JdbcTemplate jdbc;
    TransactionTemplate transactions;
    ApplicationOutbox outbox;

    @BeforeEach void setup() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE business_record (id INT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE t_application_outbox (message_id VARCHAR(64) PRIMARY KEY, "
                + "routing_key VARCHAR(128), payload TEXT, global_xid VARCHAR(128))");
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean(); factory.setDataSource(source);
        SqlSessionFactory sessions = factory.getObject(); sessions.getConfiguration().addMapper(OutboxMapper.class);
        outbox = new ApplicationOutbox(new SqlSessionTemplate(sessions).getMapper(OutboxMapper.class), new ObjectMapper());
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    @Test void rollbackRemovesBusinessChangeAndEventTogether() {
        assertThrows(IllegalStateException.class, () -> transactions.execute(status -> {
            jdbc.update("INSERT INTO business_record VALUES (1)");
            outbox.enqueue("event-1", MqConstants.ROUTING_KEY_APPLICATION_SUBMITTED, Map.of("messageId", "event-1"));
            throw new IllegalStateException("later business failure");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM business_record", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM t_application_outbox", Integer.class));
    }

    @Test void eventInsertFailureRollsBackBusinessChange() {
        transactions.execute(status -> { outbox.enqueue("event-1", "route", Map.of()); return null; });
        assertThrows(RuntimeException.class, () -> transactions.execute(status -> {
            jdbc.update("INSERT INTO business_record VALUES (1)");
            outbox.enqueue("event-1", "route", Map.of());
            return null;
        }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM business_record", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM t_application_outbox", Integer.class));
    }

    @Test void capturesGlobalTransactionIdForCommitGate() {
        RootContext.bind("test-global-xid");
        try {
            transactions.execute(status -> { outbox.enqueue("event-1", "route", Map.of()); return null; });
        } finally { RootContext.unbind(); }
        assertEquals("test-global-xid", jdbc.queryForObject("SELECT global_xid FROM t_application_outbox", String.class));
    }
}
