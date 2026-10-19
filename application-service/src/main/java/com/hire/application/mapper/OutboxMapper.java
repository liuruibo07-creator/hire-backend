package com.hire.application.mapper;

import com.hire.application.outbox.OutboxMessage;
import org.apache.ibatis.annotations.*;

public interface OutboxMapper {
    @Insert("INSERT INTO t_application_outbox (message_id,routing_key,payload,global_xid) "
            + "VALUES (#{messageId},#{routingKey},#{payload},#{globalXid})")
    int insert(OutboxMessage message);

    // Seata AT keeps undo_log until the global commit; a rollback removes the outbox row.
    // Waiting for undo-log cleanup avoids publishing a merely locally committed global branch.
    @Select("SELECT message_id AS messageId,routing_key AS routingKey,payload,global_xid AS globalXid,attempts "
            + "FROM t_application_outbox o WHERE sent_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP "
            + "AND (global_xid IS NULL OR NOT EXISTS (SELECT 1 FROM undo_log u WHERE u.xid=o.global_xid)) "
            + "ORDER BY next_attempt_at,message_id LIMIT 1 FOR UPDATE SKIP LOCKED")
    OutboxMessage lockNext();

    @Update("UPDATE t_application_outbox SET sent_at=CURRENT_TIMESTAMP,last_error=NULL WHERE message_id=#{id}")
    int markSent(@Param("id") String id);

    @Update("UPDATE t_application_outbox SET attempts=attempts+1, "
            + "next_attempt_at=TIMESTAMPADD(SECOND,#{seconds},CURRENT_TIMESTAMP),last_error=#{error} "
            + "WHERE message_id=#{id}")
    int retry(@Param("id") String id, @Param("seconds") int seconds, @Param("error") String error);
}
