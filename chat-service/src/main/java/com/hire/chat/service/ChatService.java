package com.hire.chat.service;

import com.hire.chat.mapper.ChatMapper;
import com.hire.common.utils.RedisUtil;
import com.hire.model.dto.*;
import com.hire.model.entity.*;
import com.hire.model.vo.ChatViews;
import com.hire.model.vo.ApplicationChatContextVO;
import com.hire.model.vo.JobInfoVO;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import static com.hire.chat.exception.ChatException.require;

@Service
public class ChatService {
    /** Redis 键前缀:用户未读私信总数(chat:unread:{userId}) */
    private static final String KEY_UNREAD = "chat:unread:";
    /** 未读数缓存 TTL:30 分钟兜底,正常由"推进已读位置"主动删除 */
    private static final Duration UNREAD_TTL = Duration.ofMinutes(30);

    private final ChatMapper mapper;
    private final ChatDirectory directory;
    private final ApplicationEventPublisher events;
    private final RedisUtil redis;

    public ChatService(ChatMapper mapper, ChatDirectory directory, ApplicationEventPublisher events, RedisUtil redis) {
        this.mapper = mapper; this.directory = directory; this.events = events; this.redis = redis;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ChatConversation start(ChatPrincipal actor, ChatRequests.Start request) {
        require(request != null, 400, "请求不能为空");
        long jobId, seekerId, employerId;
        JobInfoVO job;
        if ("seeker".equals(actor.role())) {
            positive(request.jobId());
            require(request.applicationId() == null, 400, "求职者建聊仅传jobId");
            job = directory.job(request.jobId());
            jobId = job.getId(); seekerId = actor.userId(); employerId = job.getEmployerId();
        } else {
            require("employer".equals(actor.role()), 403, "仅求职者和企业可使用私信");
            positive(request.applicationId());
            require(request.jobId() == null, 400, "企业建聊仅传applicationId");
            ApplicationChatContextVO application = directory.application(request.applicationId(), actor.token());
            require(Long.valueOf(actor.userId()).equals(application.getEmployerId()), 403, "投递不属于当前企业");
            jobId = application.getJobId(); seekerId = application.getSeekerId(); employerId = actor.userId();
            job = directory.job(jobId);
            require(Long.valueOf(employerId).equals(job.getEmployerId()), 403, "职位与投递的企业归属不一致");
        }
        require(seekerId != employerId, 403, "不能与自己建立会话");
        directory.requireRole(seekerId, "seeker");
        directory.requireRole(employerId, "employer");
        ChatConversation existing = mapper.findByKey(jobId, seekerId, employerId);
        if (existing != null) return mapper.view(existing.getId(), actor.userId());
        if ("seeker".equals(actor.role())) require(Integer.valueOf(1).equals(job.getStatus()), 409, "职位当前不在招聘中");
        ChatConversation conversation = new ChatConversation();
        conversation.setJobId(jobId); conversation.setSeekerId(seekerId); conversation.setEmployerId(employerId);
        conversation.setJobTitle(job.getTitle() == null ? "" : job.getTitle());
        conversation.setCreateTime(now()); conversation.setUpdateTime(conversation.getCreateTime());
        mapper.createIfAbsent(conversation);
        ChatConversation saved = mapper.lockByKey(jobId, seekerId, employerId);
        return mapper.view(saved.getId(), actor.userId());
    }

    public ChatViews.Conversations list(ChatPrincipal actor, int page, int size) {
        require(page >= 1, 400, "page必须大于0"); size(size);
        return new ChatViews.Conversations(mapper.count(actor.userId()),
                mapper.list(actor.userId(), ((long) page - 1) * size, size));
    }

    public ChatViews.Messages history(ChatPrincipal actor, Long conversationId, Long beforeId, Long afterId, int size) {
        positive(conversationId); size(size);
        member(actor, mapper.find(conversationId));
        require(beforeId == null || beforeId > 0, 400, "beforeId不合法");
        require(afterId == null || afterId >= 0, 400, "afterId不合法");
        require(beforeId == null || afterId == null, 400, "beforeId和afterId不能同时传入");
        List<ChatMessage> rows = new ArrayList<>(afterId == null
                ? mapper.history(conversationId, beforeId, size + 1) : mapper.since(conversationId, afterId, size + 1));
        boolean hasMore = rows.size() > size;
        if (hasMore) rows.remove(rows.size() - 1);
        if (afterId == null) Collections.reverse(rows);
        return new ChatViews.Messages(rows, hasMore, rows.isEmpty() ? null : rows.get(0).getId().toString(),
                rows.isEmpty() ? null : rows.get(rows.size() - 1).getId().toString());
    }

    @Transactional
    public ChatMessage send(ChatPrincipal actor, Long conversationId, ChatRequests.Send request) {
        positive(conversationId);
        require(request != null && request.content() != null && !request.content().isBlank(), 400, "消息不能为空");
        String content = request.content().strip();
        require(content.codePointCount(0, content.length()) <= 2000, 400, "消息不能超过2000字");
        // Acquire the row lock BEFORE generating the message ID. Read cursors then follow commit order.
        ChatConversation conversation = mapper.lock(conversationId);
        member(actor, conversation);
        long peerId = actor.userId() == conversation.getSeekerId() ? conversation.getEmployerId() : conversation.getSeekerId();
        directory.requireRole(peerId, "seeker".equals(actor.role()) ? "employer" : "seeker");
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId); message.setSenderId(actor.userId());
        message.setContent(content); message.setCreateTime(now());
        mapper.insertMessage(message);
        mapper.updateLastMessage(conversationId, message.getId(), message.getCreateTime());
        emit(conversation, "message.created", message);
        // 未读计数:接收方 +1(Redis 异常时 RedisUtil 自动降级,不影响发消息)
        Long unread = redis.increment(KEY_UNREAD + peerId);
        if (unread != null && unread == 1L) {
            redis.expire(KEY_UNREAD + peerId, UNREAD_TTL);
        }
        return message;
    }

    @Transactional
    public ChatViews.ReadPosition read(ChatPrincipal actor, Long conversationId, ChatRequests.Read request) {
        positive(conversationId);
        require(request != null, 400, "请求不能为空"); positive(request.throughMessageId());
        ChatConversation conversation = mapper.lock(conversationId);
        member(actor, conversation);
        require(mapper.message(conversationId, request.throughMessageId()) != null, 400, "已读位置不属于当前会话");
        boolean seeker = actor.userId() == conversation.getSeekerId();
        long previous = seeker ? conversation.getSeekerReadId() : conversation.getEmployerReadId();
        long through = Math.max(previous, request.throughMessageId());
        ChatViews.ReadPosition result = new ChatViews.ReadPosition(Long.toString(actor.userId()), Long.toString(through));
        if (through > previous) {
            if (seeker) mapper.readSeeker(conversationId, actor.userId(), through);
            else mapper.readEmployer(conversationId, actor.userId(), through);
            // 已读位置推进,删除未读缓存,下次查询回源重算
            redis.delete(KEY_UNREAD + actor.userId());
            emit(conversation, "conversation.read", result);
        }
        return result;
    }

    public long unread(ChatPrincipal actor) {
        // 未读数缓存(Cache Aside):命中直接返回,未命中回源并回填;Redis 异常时 RedisUtil 已降级
        String key = KEY_UNREAD + actor.userId();
        Long cached = redis.getLong(key);
        if (cached != null) {
            return cached;
        }
        long count = mapper.unread(actor.userId());
        redis.set(key, String.valueOf(count), UNREAD_TTL);
        return count;
    }

    private void emit(ChatConversation conversation, String type, Object data) {
        events.publishEvent(new ChatBroadcast(conversation.getSeekerId(), conversation.getEmployerId(),
                new ChatEvent(UUID.randomUUID().toString(), type, conversation.getId().toString(), data)));
    }

    private void member(ChatPrincipal actor, ChatConversation conversation) {
        require(conversation != null, 404, "会话不存在");
        boolean seeker = Long.valueOf(actor.userId()).equals(conversation.getSeekerId()) && "seeker".equals(actor.role());
        boolean employer = Long.valueOf(actor.userId()).equals(conversation.getEmployerId()) && "employer".equals(actor.role());
        require(seeker || employer, 403, "无权访问该会话");
    }

    private static void positive(Long id) { require(id != null && id > 0, 400, "ID必须为正整数"); }
    private static void size(int size) { require(size >= 1 && size <= 100, 400, "size必须为1至100"); }
    private static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")); }
}
