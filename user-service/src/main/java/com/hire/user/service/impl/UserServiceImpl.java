package com.hire.user.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.hire.common.constant.JwtConstant;
import com.hire.common.context.UserContext;
import com.hire.common.utils.JsonUtils;
import com.hire.common.utils.JwtUtil;
import com.hire.common.utils.RedisUtil;
import com.hire.model.dto.*;
import com.hire.user.client.*;
import com.hire.user.exception.UserApiException;
import com.hire.user.mapper.UserMapper;
import com.hire.user.model.*;
import com.hire.user.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** Redis 键前缀:用户信息缓存(user:info:{id}) */
    private static final String KEY_USER_INFO = "user:info:";
    /** Redis 键前缀:管理端统计缓存(user:admin:stats:{days}) */
    private static final String KEY_ADMIN_STATS = "user:admin:stats:";
    /** 用户信息缓存 TTL:60 分钟+随机抖动 */
    private static final long USER_INFO_TTL_MINUTES = 60;
    /** 统计类缓存 TTL:60 秒 */
    private static final long STATS_TTL_SECONDS = 60;
    private final UserMapper mapper;
    private final JobStatisticsClient jobs;
    private final ApplicationStatisticsClient applications;
    private final RedisUtil redis;
    public UserServiceImpl(UserMapper mapper, JobStatisticsClient jobs, ApplicationStatisticsClient applications, RedisUtil redis) {
        this.mapper=mapper; this.jobs=jobs; this.applications=applications; this.redis=redis;
    }
    private static void check(boolean valid, int code, String message) {
        if (!valid) throw new UserApiException(code, message);
    }
    private static LocalDateTime now() { return LocalDateTime.now(ZONE); }
    private static void password(String value) {
        check(StrUtil.isNotBlank(value) && value.length()>=6 && value.length()<=20,400,"密码长度必须为6-20个字符");
    }
    private static void email(String value) {
        check(StrUtil.isNotBlank(value) && value.length()<=100 && value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"),400,"邮箱格式不正确");
    }
    private static void length(String value,int max,String name) {
        check(value==null || value.length()<=max,400,name+"长度超出限制");
    }
    private UserRecord requireUser(Long id) {
        check(id!=null && id>0,400,"用户ID不合法");
        UserRecord user=mapper.findById(id);
        check(user!=null,404,"用户不存在");
        return user;
    }
    private UserRecord current() {
        Long id=UserContext.getCurrentUserId();
        check(id!=null,401,"请先登录");
        UserRecord user=mapper.findById(id);
        check(user!=null,401,"当前用户不存在");
        check(Integer.valueOf(1).equals(user.getStatus()),403,"账户已禁用");
        return user;
    }
    private void admin() { check("admin".equals(current().getRole()),403,"需要管理员权限"); }

    @Override @Transactional
    public Map<String,Object> login(UserLoginDTO dto) {
        check(dto!=null && StrUtil.isNotBlank(dto.getUsername()) && StrUtil.isNotBlank(dto.getPassword()),400,"用户名和密码不能为空");
        UserRecord user=mapper.findByAccount(dto.getUsername());
        check(user!=null && DigestUtil.sha256Hex(dto.getPassword()).equals(user.getPassword()),401,"用户名或密码错误");
        check(Integer.valueOf(1).equals(user.getStatus()),403,"账户已禁用");
        check(mapper.updateLoginTime(user.getId(),now())==1,403,"账户不可用");
        Map<String,Object> claims=new HashMap<>();
        claims.put(JwtConstant.USER_ID,user.getId()); claims.put(JwtConstant.ROLE,user.getRole());
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("token",JwtUtil.createJWT(JwtConstant.SECRET_KEY,JwtConstant.TTL_MILLIS,claims));
        result.put("userId",user.getId()); result.put("username",user.getUsername());
        result.put("role",user.getRole()); result.put("realName",user.getRealName());
        return result;
    }
    @Override @Transactional
    public Long register(UserRequests.Register dto) {
        check(dto!=null,400,"请求不能为空");
        check(StrUtil.isNotBlank(dto.getUsername()) && dto.getUsername().length()>=3 && dto.getUsername().length()<=50,400,"用户名长度必须为3-50个字符");
        password(dto.getPassword()); email(dto.getEmail());
        check("seeker".equals(dto.getRole()) || "employer".equals(dto.getRole()),400,"角色必须为seeker或employer");
        length(dto.getRealName(),50,"姓名"); length(dto.getPhone(),20,"手机号");
        check(mapper.countByUsername(dto.getUsername())==0,409,"用户名已存在");
        check(mapper.countByEmail(dto.getEmail(),null)==0,409,"邮箱已存在");
        UserRecord user=new UserRecord();
        user.setId(IdUtil.getSnowflakeNextId()); user.setUsername(dto.getUsername());
        user.setPassword(DigestUtil.sha256Hex(dto.getPassword())); user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone()); user.setRealName(dto.getRealName()); user.setRole(dto.getRole());
        user.setStatus(1); user.setCreateTime(now()); user.setUpdateTime(user.getCreateTime());
        mapper.insert(user);
        return user.getId();
    }
    private Map<String,Object> info(UserRecord u) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("id",u.getId()); result.put("username",u.getUsername()); result.put("realName",u.getRealName());
        result.put("role",u.getRole()); result.put("status",u.getStatus()); result.put("avatar",u.getAvatar());
        return result;
    }
    private Map<String,Object> profile(UserRecord u,boolean detail) {
        Map<String,Object> result=info(u);
        result.put("email",u.getEmail()); result.put("phone",u.getPhone());
        result.put("lastLoginTime",format(u.getLastLoginTime())); result.put("createTime",format(u.getCreateTime()));
        if(detail) result.put("updateTime",format(u.getUpdateTime()));
        return result;
    }
    private String format(LocalDateTime time) { return time==null?null:TIME.format(time); }
    @Override public Map<String,Object> getById(Long id) {
        // 用户信息缓存(Cache Aside):job-service 查企业名等场景高频调用,命中免查库;
        // requireUser 对不存在的用户直接抛 404,无需空值缓存;Redis 异常时 RedisUtil 已降级
        String key = KEY_USER_INFO + id;
        String cached = redis.get(key);
        if (cached != null) {
            return JsonUtils.jsonToObject(cached, Map.class);
        }
        Map<String,Object> result = info(requireUser(id));
        redis.set(key, JsonUtils.toJson(result), Duration.ofMinutes(USER_INFO_TTL_MINUTES + RandomUtil.randomInt(0, 10)));
        return result;
    }
    @Override public Map<String,Object> getCurrentUser() { return profile(current(),false); }
    @Override @Transactional
    public void updateCurrentUser(UserUpdateDTO dto) {
        UserRecord existing=current();
        check(dto!=null,400,"请求不能为空");
        length(dto.getRealName(),50,"姓名"); length(dto.getPhone(),20,"手机号"); length(dto.getAvatar(),500,"头像地址");
        if(dto.getEmail()!=null) {
            email(dto.getEmail());
            check(mapper.countByEmail(dto.getEmail(),existing.getId())==0,409,"邮箱已存在");
        }
        UserRecord update=new UserRecord();
        update.setId(existing.getId()); update.setRealName(dto.getRealName()); update.setEmail(dto.getEmail());
        update.setPhone(dto.getPhone()); update.setAvatar(dto.getAvatar()); update.setUpdateTime(now());
        check(mapper.updateUser(update)==1,409,"用户状态已变化，请重试");
        // 资料已变化,删除用户信息缓存,避免下游(job-service 企业名等)拿到旧值
        redis.delete(KEY_USER_INFO + existing.getId());
    }
    @Override @Transactional
    public void updatePassword(PasswordUpdateDTO dto) {
        UserRecord user=current();
        check(dto!=null && StrUtil.isNotBlank(dto.getOldPassword()),400,"原密码不能为空");
        password(dto.getNewPassword());
        check(DigestUtil.sha256Hex(dto.getOldPassword()).equals(user.getPassword()),400,"原密码错误");
        check(mapper.updatePassword(user.getId(),user.getPassword(),DigestUtil.sha256Hex(dto.getNewPassword()),now())==1,409,"用户信息已变化，请重试");
    }
    @Override public Map<String,Object> listUsers(int page,int size,String role,Integer status,String keyword) {
        admin(); check(page>=1 && size>=1,400,"分页参数不合法");
        check(role==null || Arrays.asList("seeker","employer","admin").contains(role),400,"角色不合法");
        check(status==null || status==0 || status==1,400,"状态不合法");
        String search=StrUtil.isBlank(keyword)?null:keyword;
        List<Map<String,Object>> list=mapper.listUsers(role,status,search,((long)page-1)*size,size).stream().map(u->{
            Map<String,Object> item=profile(u,false);
            item.remove("avatar"); item.remove("lastLoginTime"); return item;
        }).collect(Collectors.toList());
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("total",mapper.countUsers(role,status,search)); result.put("list",list); return result;
    }
    @Override public Map<String,Object> adminDetail(Long id) { admin(); return profile(requireUser(id),true); }
    @Override @Transactional public Map<String,Object> updateStatus(Long id,Integer status) {
        admin(); check(status!=null && (status==0 || status==1),400,"状态必须为0或1");
        requireUser(id);
        check(mapper.updateStatus(id,status,now())==1,404,"用户不存在");
        // 禁用/启用会改变缓存中的 status 字段,必须删除缓存
        redis.delete(KEY_USER_INFO + id);
        Map<String,Object> result=new LinkedHashMap<>(); result.put("id",id); result.put("status",status); return result;
    }
    @Override public Map<String,Object> notificationRecipients(long afterId,int size) {
        check(afterId>=0 && size>=1 && size<=1000,400,"游标或批量大小不合法");
        List<Long> ids=mapper.notificationRecipients(afterId,size);
        Map<String,Object> result=new LinkedHashMap<>(); result.put("ids",ids);
        result.put("nextAfterId",ids.isEmpty()?afterId:ids.get(ids.size()-1)); return result;
    }
    @Override public Map<String,Object> statistics(int days) {
        admin(); check(days==7 || days==30 || days==90,400,"days仅支持7、30、90");
        // 统计缓存(Cache Aside):管理端对分钟级延迟可接受,60 秒自然过期,无需手动失效
        String statsKey = KEY_ADMIN_STATS + days;
        String cached = redis.get(statsKey);
        if (cached != null) {
            return JsonUtils.jsonToObject(cached, Map.class);
        }
        LocalDate end=LocalDate.now(ZONE),start=end.minusDays(days-1);
        JsonNode jobData,applicationData;
        Map<String,Long> jobTrend,applicationTrend;
        try {
            jobData=dependency(jobs.statistics(days),"jobTotal","dailyNewJobs");
            applicationData=dependency(applications.statistics(days),"applicationTotal","dailyNewApplications");
            jobTrend=trend(jobData.get("dailyNewJobs"),start,days);
            applicationTrend=trend(applicationData.get("dailyNewApplications"),start,days);
        } catch(Exception e) {
            throw new UserApiException(500,"依赖服务统计失败");
        }
        Map<String,Map<String,Object>> userTrend=new HashMap<>();
        for(Map<String,Object> row:mapper.dailyUsers(start.atStartOfDay(),end.plusDays(1).atStartOfDay()))
            userTrend.put(row.get("date").toString(),row);
        Map<String,Object> overview=new LinkedHashMap<>();
        overview.put("registeredUserTotal",mapper.registeredUserTotal()); overview.put("enterpriseTotal",mapper.enterpriseTotal());
        overview.put("jobTotal",jobData.get("jobTotal").longValue()); overview.put("applicationTotal",applicationData.get("applicationTotal").longValue());
        List<Map<String,Object>> daily=new ArrayList<>();
        for(int i=0;i<days;i++) {
            String date=start.plusDays(i).toString();
            Map<String,Object> row=userTrend.get(date),item=new LinkedHashMap<>();
            item.put("date",date); item.put("newRegisteredUsers",row==null?0L:((Number)row.get("registered")).longValue());
            item.put("newEnterprises",row==null?0L:((Number)row.get("enterprises")).longValue());
            item.put("newJobs",jobTrend.get(date)); item.put("newApplications",applicationTrend.get(date)); daily.add(item);
        }
        Map<String,Object> result=new LinkedHashMap<>(); result.put("overview",overview); result.put("dailyTrend",daily);
        redis.set(statsKey, JsonUtils.toJson(result), Duration.ofSeconds(STATS_TTL_SECONDS));
        return result;
    }
    private JsonNode dependency(ApiResult<JsonNode> response,String total,String daily) {
        check(response!=null && Integer.valueOf(200).equals(response.getCode()) && response.getData()!=null,500,"依赖服务统计失败");
        JsonNode data=response.getData(),count=data.get(total);
        check(count!=null && count.isIntegralNumber() && count.canConvertToLong() && count.longValue()>=0
                && data.has(daily) && data.get(daily).isArray(),500,"依赖服务统计数据不完整");
        return data;
    }
    private Map<String,Long> trend(JsonNode rows,LocalDate start,int days) {
        Map<String,Long> result=new HashMap<>();
        for(JsonNode row:rows) {
            JsonNode date=row.get("date"),count=row.get("count");
            check(date!=null && date.isTextual() && count!=null && count.isIntegralNumber()
                    && count.canConvertToLong() && count.longValue()>=0,500,"依赖服务统计数据不完整");
            check(result.put(date.textValue(),count.longValue())==null,500,"依赖服务统计日期重复");
        }
        check(result.size()==days,500,"依赖服务统计数据不完整");
        for(int i=0;i<days;i++) check(result.containsKey(start.plusDays(i).toString()),500,"依赖服务统计日期不完整");
        return result;
    }
}
