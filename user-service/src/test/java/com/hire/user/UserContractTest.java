package com.hire.user;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.databind.*;
import com.hire.common.context.UserContext;
import com.hire.common.utils.RedisUtil;
import com.hire.model.dto.*;
import com.hire.user.client.*;
import com.hire.user.config.MvcConfig;
import com.hire.user.controller.*;
import com.hire.user.handler.GlobalExceptionHandler;
import com.hire.user.mapper.UserMapper;
import com.hire.user.model.*;
import com.hire.user.service.impl.UserServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.HandlerInterceptor;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserContractTest {
    UserMapper mapper;
    JobStatisticsClient jobs;
    ApplicationStatisticsClient applications;
    UserServiceImpl service;
    MockMvc mvc;
    ObjectMapper json = new ObjectMapper();
    UserRecord admin, seeker;
    static class Registry extends InterceptorRegistry {
        List<Object> items() { return getInterceptors(); }
    }
    @BeforeEach void setup() {
        mapper=mock(UserMapper.class); jobs=mock(JobStatisticsClient.class); applications=mock(ApplicationStatisticsClient.class);
        service=new UserServiceImpl(mapper,jobs,applications,mock(RedisUtil.class));
        Registry registry=new Registry(); new MvcConfig().addInterceptors(registry);
        mvc=MockMvcBuilders.standaloneSetup(new UserController(service),new AdminUserController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(registry.items().stream().map(x->(HandlerInterceptor)x).toArray(HandlerInterceptor[]::new)).build();
        admin=user(1L,"admin",1); seeker=user(2L,"seeker",1);
        when(mapper.findById(1L)).thenReturn(admin); when(mapper.findById(2L)).thenReturn(seeker);
    }
    @AfterEach void clean() { UserContext.removeCurrentUserId(); }
    UserRecord user(long id,String role,int status) {
        UserRecord user=new UserRecord();
        user.setId(id); user.setUsername("name"+id); user.setRole(role); user.setStatus(status);
        user.setPassword(DigestUtil.sha256Hex("123456")); user.setEmail("user"+id+"@example.com");
        user.setCreateTime(LocalDateTime.of(2026,9,20,10,0)); return user;
    }
    String registration() { return "{\"username\":\"alice\",\"password\":\"123456\",\"email\":\"a@example.com\",\"role\":\"seeker\"}"; }
    @Test void registerReturnsIdAndDocumentedEnvelope() throws Exception {
        mvc.perform(post("/api/user/users/register").contentType(MediaType.APPLICATION_JSON).content(registration()))
            .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.message").value("注册成功"))
            .andExpect(jsonPath("$.msg").doesNotExist()).andExpect(jsonPath("$.data").isNumber());
        verify(mapper).insert(argThat(u->u.getId()!=null && u.getPassword().equals(DigestUtil.sha256Hex("123456")) && u.getStatus()==1));
    }
    @Test void registerRequiresEmailAndRole() throws Exception {
        for(String body:Arrays.asList(registration().replace("\"seeker\"","\"admin\""),
                registration().replace("\"a@example.com\"","null"),registration().replace("\"seeker\"","null"))) {
            mvc.perform(post("/api/user/users/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(400));
        }
        verify(mapper,never()).insert(any());
    }
    @Test void duplicateUsernameAndEmailReturn409() throws Exception {
        when(mapper.countByUsername("alice")).thenReturn(1);
        mvc.perform(post("/api/user/users/register").contentType(MediaType.APPLICATION_JSON).content(registration()))
            .andExpect(jsonPath("$.code").value(409));
        when(mapper.countByUsername("alice")).thenReturn(0); when(mapper.countByEmail("a@example.com",null)).thenReturn(1);
        mvc.perform(post("/api/user/users/register").contentType(MediaType.APPLICATION_JSON).content(registration()))
            .andExpect(jsonPath("$.code").value(409));
    }
    @Test void clientCannotChooseIdOrStatus() throws Exception {
        mvc.perform(post("/api/user/users/register").contentType(MediaType.APPLICATION_JSON)
                .content(registration().replace("}",",\"id\":5,\"status\":0,\"deleted\":1}")))
            .andExpect(jsonPath("$.code").value(200));
        verify(mapper).insert(argThat(u->u.getId()!=5 && u.getStatus()==1));
    }
    @Test void loginReturnsUserIdAndTokenAndRecordsTime() throws Exception {
        when(mapper.findByAccount("name2")).thenReturn(seeker); when(mapper.updateLoginTime(eq(2L),any())).thenReturn(1);
        mvc.perform(post("/api/user/users/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"name2\",\"password\":\"123456\"}"))
            .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.userId").value(2))
            .andExpect(jsonPath("$.data.token").isString()).andExpect(jsonPath("$.data.password").doesNotExist());
        verify(mapper).updateLoginTime(eq(2L),any());
    }
    @Test void wrongPasswordAndDisabledAccount() throws Exception {
        when(mapper.findByAccount("name2")).thenReturn(seeker);
        mvc.perform(post("/api/user/users/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"name2\",\"password\":\"wrong\"}"))
            .andExpect(jsonPath("$.code").value(401));
        seeker.setStatus(0);
        mvc.perform(post("/api/user/users/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"name2\",\"password\":\"123456\"}"))
            .andExpect(jsonPath("$.code").value(403));
        verify(mapper,never()).updateLoginTime(any(),any());
    }
    @Test void meRequiresLoginAndOmitsPassword() throws Exception {
        mvc.perform(get("/api/user/users/me")).andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/api/user/users/me").header("X-User-Id","2")).andExpect(jsonPath("$.data.id").value(2))
            .andExpect(jsonPath("$.data.status").value(1)).andExpect(jsonPath("$.data.createTime").value("2026-09-20 10:00:00"))
            .andExpect(jsonPath("$.data.password").doesNotExist()).andExpect(jsonPath("$.data.updateTime").doesNotExist());
        assertNull(UserContext.getCurrentUserId());
    }
    @Test void malformedHeaderAndBodyReturnClientErrors() throws Exception {
        mvc.perform(get("/api/user/users/me").header("X-User-Id","bad")).andExpect(jsonPath("$.code").value(401));
        mvc.perform(post("/api/user/users/login").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/user/admin/users").header("X-User-Id","1").param("page","bad"))
            .andExpect(jsonPath("$.code").value(400));
    }
    @Test void updateChecksEmailUniquenessAndAllowsEmptyBody() throws Exception {
        when(mapper.countByEmail("taken@example.com",2L)).thenReturn(1);
        mvc.perform(put("/api/user/users/me").header("X-User-Id","2").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"taken@example.com\"}")).andExpect(jsonPath("$.code").value(409));
        when(mapper.updateUser(any())).thenReturn(1);
        mvc.perform(put("/api/user/users/me").header("X-User-Id","2").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(jsonPath("$.code").value(200));
    }
    @Test void passwordUsesStoredHashAndCurrentId() throws Exception {
        when(mapper.updatePassword(eq(2L),any(),any(),any())).thenReturn(1);
        mvc.perform(put("/api/user/users/me/password").header("X-User-Id","2").contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"123456\",\"newPassword\":\"abcdef\"}"))
            .andExpect(jsonPath("$.code").value(200));
        verify(mapper).updatePassword(eq(2L),eq(DigestUtil.sha256Hex("123456")),eq(DigestUtil.sha256Hex("abcdef")),any());
    }
    @Test void allAdminEndpointsCheckDatabaseRole() throws Exception {
        for(String path:Arrays.asList("/api/user/admin/users","/api/user/admin/users/2","/api/user/admin/statistics")) {
            mvc.perform(get(path).header("X-User-Id","2").header("role","admin")).andExpect(jsonPath("$.code").value(403));
        }
        mvc.perform(put("/api/user/admin/users/2/status").header("X-User-Id","2").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":0}")).andExpect(jsonPath("$.code").value(403));
    }
    @Test void listUsesFilteredTotalAndSafeFields() throws Exception {
        when(mapper.listUsers("seeker",1,"name",10L,10)).thenReturn(Collections.singletonList(seeker));
        when(mapper.countUsers("seeker",1,"name")).thenReturn(21L);
        mvc.perform(get("/api/user/admin/users").header("X-User-Id","1").param("page","2").param("role","seeker").param("status","1").param("keyword","name"))
            .andExpect(jsonPath("$.data.total").value(21)).andExpect(jsonPath("$.data.list[0].id").value(2))
            .andExpect(jsonPath("$.data.list[0].password").doesNotExist()).andExpect(jsonPath("$.data.list[0].avatar").doesNotExist());
    }
    @Test void statusAndDetailContracts() throws Exception {
        mvc.perform(get("/api/user/admin/users/99").header("X-User-Id","1")).andExpect(jsonPath("$.code").value(404));
        mvc.perform(get("/api/user/admin/users/2").header("X-User-Id","1")).andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.password").doesNotExist());
        mvc.perform(put("/api/user/admin/users/2/status").header("X-User-Id","1").contentType(MediaType.APPLICATION_JSON).content("{\"status\":5}"))
            .andExpect(jsonPath("$.code").value(400));
        when(mapper.updateStatus(eq(2L),eq(0),any())).thenReturn(1);
        mvc.perform(put("/api/user/admin/users/2/status").header("X-User-Id","1").contentType(MediaType.APPLICATION_JSON).content("{\"status\":0}"))
            .andExpect(jsonPath("$.data.id").value(2)).andExpect(jsonPath("$.data.status").value(0));
    }
    @Test void internalInfoHasOnlyBasicFields() throws Exception {
        mvc.perform(get("/api/user/users/2/info")).andExpect(jsonPath("$.data.status").value(1))
            .andExpect(jsonPath("$.data.email").doesNotExist()).andExpect(jsonPath("$.data.password").doesNotExist());
        mvc.perform(get("/api/user/users/99/info")).andExpect(jsonPath("$.code").value(404));
    }
    @Test void recipientsCursorAndLimits() throws Exception {
        when(mapper.notificationRecipients(0L,500)).thenReturn(Arrays.asList(2L,8L));
        mvc.perform(get("/api/user/users/notification-recipients/internal")).andExpect(jsonPath("$.data.nextAfterId").value(8))
            .andExpect(jsonPath("$.data.ids[0]").value(2));
        when(mapper.notificationRecipients(8L,500)).thenReturn(Collections.emptyList());
        mvc.perform(get("/api/user/users/notification-recipients/internal").param("afterId","8"))
            .andExpect(jsonPath("$.data.nextAfterId").value(8));
        mvc.perform(get("/api/user/users/notification-recipients/internal").param("size","1001"))
            .andExpect(jsonPath("$.code").value(400));
    }
    ApiResult<JsonNode> stats(String total,String field,int days) {
        com.fasterxml.jackson.databind.node.ObjectNode data=json.createObjectNode(); data.put(total,20L);
        com.fasterxml.jackson.databind.node.ArrayNode daily=data.putArray(field);
        LocalDate start=LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(days-1);
        for(int i=0;i<days;i++) daily.addObject().put("date",start.plusDays(i).toString()).put("count",i==0?3L:0L);
        return ApiResult.ok("查询成功",data);
    }
    @Test void statisticsFillsEveryDayAndCombinesServices() throws Exception {
        when(jobs.statistics(7)).thenReturn(stats("jobTotal","dailyNewJobs",7));
        when(applications.statistics(7)).thenReturn(stats("applicationTotal","dailyNewApplications",7));
        when(mapper.registeredUserTotal()).thenReturn(10L); when(mapper.enterpriseTotal()).thenReturn(2L);
        when(mapper.dailyUsers(any(),any())).thenReturn(Collections.emptyList());
        mvc.perform(get("/api/user/admin/statistics").header("X-User-Id","1"))
            .andExpect(jsonPath("$.data.overview.registeredUserTotal").value(10))
            .andExpect(jsonPath("$.data.dailyTrend.length()").value(7))
            .andExpect(jsonPath("$.data.dailyTrend[0].newJobs").value(3))
            .andExpect(jsonPath("$.data.dailyTrend[0].newRegisteredUsers").value(0));
    }
    @Test void dependencyFailureNeverReturnsPartialStatistics() throws Exception {
        when(jobs.statistics(7)).thenThrow(new RuntimeException("unavailable"));
        mvc.perform(get("/api/user/admin/statistics").header("X-User-Id","1")).andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.data").isEmpty());
        verify(mapper,never()).registeredUserTotal();
    }
    @Test void invalidDaysAndIncompleteDependencyAreRejected() throws Exception {
        mvc.perform(get("/api/user/admin/statistics").header("X-User-Id","1").param("days","8"))
            .andExpect(jsonPath("$.code").value(400));
        when(jobs.statistics(7)).thenReturn(stats("jobTotal","dailyNewJobs",6));
        when(applications.statistics(7)).thenReturn(stats("applicationTotal","dailyNewApplications",7));
        mvc.perform(get("/api/user/admin/statistics").header("X-User-Id","1")).andExpect(jsonPath("$.code").value(500));
    }
    @Test void mapperSqlParsesAndAlwaysFiltersDeletedRows() {
        Configuration config=new Configuration();
        new MapperAnnotationBuilder(config,UserMapper.class).parse();
        Map<String,Object> args=new HashMap<>(); args.put("role","seeker"); args.put("status",1); args.put("keyword","alice");
        args.put("size",10); args.put("offset",0L);
        String prefix=UserMapper.class.getName()+".";
        String list=config.getMappedStatement(prefix+"listUsers").getBoundSql(args).getSql();
        assertTrue(list.contains("deleted=0")); assertTrue(list.contains("email LIKE"));
        assertTrue(config.getMappedStatement(prefix+"findByAccount").getBoundSql("a").getSql().contains("deleted=0 AND ("));
        UserRecord empty=new UserRecord(); empty.setId(2L); empty.setUpdateTime(LocalDateTime.now());
        String update=config.getMappedStatement(prefix+"updateUser").getBoundSql(empty).getSql();
        assertTrue(update.contains("SET")); assertTrue(update.contains("update_time=")); assertFalse(update.contains("SET WHERE"));
        String recipients=config.getMappedStatement(prefix+"notificationRecipients").getBoundSql(new HashMap<>()).getSql();
        assertTrue(recipients.contains("status=1")); assertTrue(recipients.contains("ORDER BY id ASC"));
    }
}
