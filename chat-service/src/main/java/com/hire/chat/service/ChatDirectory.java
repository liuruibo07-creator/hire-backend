package com.hire.chat.service;

import com.hire.api.clients.ApplicationClient;
import com.hire.api.clients.JobClient;
import com.hire.api.clients.UserClient;
import com.hire.chat.exception.ChatException;
import com.hire.common.domain.Result;
import com.hire.model.vo.ApplicationChatContextVO;
import com.hire.model.vo.JobInfoVO;
import com.hire.model.vo.UserInfoVO;
import feign.FeignException;
import org.springframework.stereotype.Service;
import java.util.function.Supplier;
import static com.hire.chat.exception.ChatException.require;

@Service
public class ChatDirectory {
    private final UserClient users;
    private final JobClient jobs;
    private final ApplicationClient applications;

    public ChatDirectory(UserClient users, JobClient jobs, ApplicationClient applications) {
        this.users = users; this.jobs = jobs; this.applications = applications;
    }

    public UserInfoVO activeUser(long id) {
        Result<UserInfoVO> response = call(() -> users.getUserInfo(id));
        require(response != null, 503, "用户服务暂不可用");
        require(!Integer.valueOf(404).equals(response.getCode()), 403, "账号不存在或已停用");
        require(Integer.valueOf(200).equals(response.getCode()) && response.getData() != null, 503, "用户信息查询失败");
        UserInfoVO user = response.getData();
        require(Long.valueOf(id).equals(user.getId()), 503, "用户信息不完整");
        require(Integer.valueOf(1).equals(user.getStatus()), 403, "账号已停用");
        require("seeker".equals(user.getRole()) || "employer".equals(user.getRole()), 403, "仅求职者和企业可使用私信");
        return user;
    }

    public void requireRole(long id, String role) {
        require(role.equals(activeUser(id).getRole()), 403, "用户角色不符合会话要求");
    }

    public JobInfoVO job(long id) {
        com.hire.common.Result<JobInfoVO> response = call(() -> jobs.getJobInfo(id));
        require(response != null && Integer.valueOf(1).equals(response.getCode()), 503, "职位服务暂不可用");
        require(response.getData() != null, 404, "职位不存在");
        JobInfoVO job = response.getData();
        require(Long.valueOf(id).equals(job.getId()) && job.getEmployerId() != null && job.getEmployerId() > 0,
                503, "职位归属信息不完整");
        return job;
    }

    public ApplicationChatContextVO application(long id, String token) {
        Result<ApplicationChatContextVO> response = call(() -> applications.getChatContext(id, "Bearer " + token));
        require(response != null && Integer.valueOf(200).equals(response.getCode()) && response.getData() != null,
                503, "投递服务暂不可用");
        ApplicationChatContextVO context = response.getData();
        require(Long.valueOf(id).equals(context.getApplicationId()) && context.getJobId() != null
                && context.getJobId() > 0 && context.getSeekerId() != null && context.getSeekerId() > 0
                && context.getEmployerId() != null && context.getEmployerId() > 0, 503, "投递关系信息不完整");
        return context;
    }

    private <T> T call(Supplier<T> call) {
        try { return call.get(); }
        catch (FeignException e) {
            if (e.status() == 401 || e.status() == 403 || e.status() == 404) {
                throw new ChatException(e.status(), "关联信息不存在或无权访问");
            }
            throw new ChatException(503, "依赖服务暂不可用，请稍后重试");
        }
    }
}
