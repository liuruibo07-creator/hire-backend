package com.hire.chat.mapper;

import com.hire.model.entity.ChatConversation;
import com.hire.model.entity.ChatMessage;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMapper {
    String PARTICIPANT = "(c.seeker_id=#{userId} OR c.employer_id=#{userId})";
    String UNREAD = "(SELECT COUNT(*) FROM t_chat_message um WHERE um.conversation_id=c.id"
            + " AND um.sender_id != #{userId} AND um.id > CASE WHEN c.seeker_id=#{userId}"
            + " THEN c.seeker_read_id ELSE c.employer_read_id END)";
    String VIEW = "SELECT c.*, lm.content AS last_message_content, " + UNREAD + " AS unread_count"
            + " FROM t_chat_conversation c LEFT JOIN t_chat_message lm ON lm.id=c.last_message_id";

    @Insert("INSERT INTO t_chat_conversation(job_id,seeker_id,employer_id,job_title,create_time,update_time)"
            + " VALUES(#{jobId},#{seekerId},#{employerId},#{jobTitle},#{createTime},#{updateTime})"
            + " ON DUPLICATE KEY UPDATE id=id")
    void createIfAbsent(ChatConversation conversation);

    @Select("SELECT * FROM t_chat_conversation WHERE job_id=#{jobId} AND seeker_id=#{seekerId} AND employer_id=#{employerId}")
    ChatConversation findByKey(@Param("jobId") long jobId, @Param("seekerId") long seekerId, @Param("employerId") long employerId);

    // Current read after the upsert: concurrent creation must not be hidden by an older MySQL snapshot.
    @Select("SELECT * FROM t_chat_conversation WHERE job_id=#{jobId} AND seeker_id=#{seekerId} AND employer_id=#{employerId} FOR UPDATE")
    ChatConversation lockByKey(@Param("jobId") long jobId, @Param("seekerId") long seekerId, @Param("employerId") long employerId);

    @Select("SELECT * FROM t_chat_conversation WHERE id=#{id}")
    ChatConversation find(long id);

    @Select("SELECT * FROM t_chat_conversation WHERE id=#{id} FOR UPDATE")
    ChatConversation lock(long id);

    @Select(VIEW + " WHERE c.id=#{id} AND " + PARTICIPANT)
    ChatConversation view(@Param("id") long id, @Param("userId") long userId);

    @Select(VIEW + " WHERE " + PARTICIPANT + " ORDER BY c.update_time DESC,c.id DESC LIMIT #{size} OFFSET #{offset}")
    List<ChatConversation> list(@Param("userId") long userId, @Param("offset") long offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM t_chat_conversation c WHERE " + PARTICIPANT)
    long count(long userId);

    @Insert("INSERT INTO t_chat_message(conversation_id,sender_id,content,create_time)"
            + " VALUES(#{conversationId},#{senderId},#{content},#{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertMessage(ChatMessage message);

    @Update("UPDATE t_chat_conversation SET last_message_id=#{messageId}, update_time=#{now} WHERE id=#{id}")
    void updateLastMessage(@Param("id") long id, @Param("messageId") long messageId, @Param("now") LocalDateTime now);

    @Select("SELECT * FROM t_chat_message WHERE id=#{id} AND conversation_id=#{conversationId}")
    ChatMessage message(@Param("conversationId") long conversationId, @Param("id") long id);

    @Select("<script>SELECT * FROM t_chat_message WHERE conversation_id=#{conversationId}"
            + "<if test='beforeId != null'> AND id &lt; #{beforeId}</if>"
            + " ORDER BY id DESC LIMIT #{size}</script>")
    List<ChatMessage> history(@Param("conversationId") long conversationId, @Param("beforeId") Long beforeId, @Param("size") int size);

    @Select("SELECT * FROM t_chat_message WHERE conversation_id=#{conversationId} AND id > #{afterId} ORDER BY id ASC LIMIT #{size}")
    List<ChatMessage> since(@Param("conversationId") long conversationId, @Param("afterId") long afterId, @Param("size") int size);

    @Update("UPDATE t_chat_conversation SET seeker_read_id=GREATEST(seeker_read_id,#{messageId}) WHERE id=#{id} AND seeker_id=#{userId}")
    void readSeeker(@Param("id") long id, @Param("userId") long userId, @Param("messageId") long messageId);

    @Update("UPDATE t_chat_conversation SET employer_read_id=GREATEST(employer_read_id,#{messageId}) WHERE id=#{id} AND employer_id=#{userId}")
    void readEmployer(@Param("id") long id, @Param("userId") long userId, @Param("messageId") long messageId);

    @Select("SELECT COUNT(*) FROM t_chat_message m JOIN t_chat_conversation c ON c.id=m.conversation_id"
            + " WHERE " + PARTICIPANT + " AND m.sender_id != #{userId} AND m.id > CASE WHEN c.seeker_id=#{userId}"
            + " THEN c.seeker_read_id ELSE c.employer_read_id END")
    long unread(long userId);
}
