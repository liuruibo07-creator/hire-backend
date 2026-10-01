package com.hire.application.service.impl;

import com.hire.api.clients.JobClient;
import com.hire.application.mapper.AdminApplicationMapper;
import com.hire.application.mapper.ResumeMapper;
import com.hire.common.Result;
import com.hire.common.context.UserContext;
import com.hire.common.exception.BusinessException;
import com.hire.model.entity.Applications;
import com.hire.model.entity.Resume;
import com.hire.model.vo.AdminApplicationVO;
import com.hire.model.vo.JobInfoVO;
import com.hire.model.vo.PageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminApplicationServiceImpl#listAllApplications（API 文档 4.13）单元测试。
 *
 * 覆盖：权限校验、参数校验（status/日期/分页）、正常分页组装、
 * Feign/简历查询失败降级、边界条件（total=0、size 上限、日期含当日）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("管理员查询全平台投递记录")
class AdminApplicationServiceImplTest {

    private static final Long ADMIN_ID = 100L;

    @Mock
    private AdminApplicationMapper adminApplicationMapper;

    @Mock
    private JobClient jobClient;

    @Mock
    private ResumeMapper resumeMapper;

    @InjectMocks
    private AdminApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        // 默认以管理员身份执行；非管理员用例内部覆盖
        UserContext.setCurrentUserId(ADMIN_ID);
        UserContext.setCurrentRole("admin");
    }

    @AfterEach
    void tearDown() {
        UserContext.removeCurrentUserId();
    }

    // ==================== 权限校验 ====================

    @Nested
    @DisplayName("权限校验")
    class AuthTests {

        @Test
        @DisplayName("未登录时抛出业务异常")
        void shouldRejectWhenNotLogin() {
            UserContext.removeCurrentUserId();
            BusinessException e = assertThrows(BusinessException.class,
                    () -> service.listAllApplications(null, null, null, null, null, null, null, null));
            assertEquals("用户未登录，请先登录", e.getMessage());
        }

        @Test
        @DisplayName("求职者角色被拒绝")
        void shouldRejectSeeker() {
            UserContext.setCurrentRole("seeker");
            BusinessException e = assertThrows(BusinessException.class,
                    () -> service.listAllApplications(null, null, null, null, null, null, null, null));
            assertEquals("仅管理员可查询全平台投递记录", e.getMessage());
            verify(adminApplicationMapper, never()).countAdminApplications(
                    any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("role 头缺失（网关未透传）时也不放行")
        void shouldRejectWhenRoleMissing() {
            UserContext.setCurrentRole(null);
            assertThrows(BusinessException.class,
                    () -> service.listAllApplications(null, null, null, null, null, null, null, null));
        }

        @Test
        @DisplayName("企业角色被拒绝")
        void shouldRejectEmployer() {
            UserContext.setCurrentRole("employer");
            assertThrows(BusinessException.class,
                    () -> service.listAllApplications(null, null, null, null, null, null, null, null));
        }
    }

    // ==================== 参数校验 ====================

    @Nested
    @DisplayName("参数校验")
    class ValidationTests {

        @Test
        @DisplayName("status 超出 0~4 时报错")
        void shouldRejectIllegalStatus() {
            BusinessException e = assertThrows(BusinessException.class,
                    () -> service.listAllApplications(null, null, null, null, null, 5, null, null));
            assertEquals("非法的投递状态：5，取值范围 0~4", e.getMessage());

            assertThrows(BusinessException.class,
                    () -> service.listAllApplications(null, null, null, null, null, -1, null, null));
        }

        @Test
        @DisplayName("status 边界值 0 和 4 合法")
        void shouldAcceptStatusBoundary() {
            when(adminApplicationMapper.countAdminApplications(
                    isNull(), isNull(), isNull(), eq(0), isNull(), isNull())).thenReturn(0L);
            when(adminApplicationMapper.countAdminApplications(
                    isNull(), isNull(), isNull(), eq(4), isNull(), isNull())).thenReturn(0L);

            service.listAllApplications(null, null, null, null, null, 0, null, null);
            service.listAllApplications(null, null, null, null, null, 4, null, null);
        }

        @Test
        @DisplayName("startDate 格式非法时报错并指明参数名")
        void shouldRejectBadStartDate() {
            BusinessException e = assertThrows(BusinessException.class,
                    () -> service.listAllApplications(null, null, null, null, null, null, "2026/09/28", null));
            assertEquals("参数 startDate 格式非法：2026/09/28，应为 YYYY-MM-DD", e.getMessage());
        }

        @Test
        @DisplayName("endDate 格式非法时报错并指明参数名")
        void shouldRejectBadEndDate() {
            BusinessException e = assertThrows(BusinessException.class,
                    () -> service.listAllApplications(null, null, null, null, null, null, null, "2026-9-1"));
            assertEquals("参数 endDate 格式非法：2026-9-1，应为 YYYY-MM-DD", e.getMessage());
        }

        @Test
        @DisplayName("startDate 晚于 endDate 时报错")
        void shouldRejectStartAfterEnd() {
            BusinessException e = assertThrows(BusinessException.class,
                    () -> service.listAllApplications(null, null, null, null, null, null,
                            "2026-09-10", "2026-09-01"));
            assertEquals("startDate 不能晚于 endDate", e.getMessage());
        }

        @Test
        @DisplayName("空白日期字符串视为未传，不报错")
        void shouldTreatBlankDateAsNull() {
            when(adminApplicationMapper.countAdminApplications(
                    isNull(), isNull(), isNull(), isNull(), isNull(), isNull())).thenReturn(0L);
            service.listAllApplications(null, null, null, null, null, null, "  ", "");
            verify(adminApplicationMapper).countAdminApplications(
                    isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
        }
    }

    // ==================== 分页参数兜底 ====================

    @Nested
    @DisplayName("分页参数兜底")
    class PaginationTests {

        @Test
        @DisplayName("page/size 缺省时使用 page=1, size=10")
        void shouldApplyDefaultPaging() {
            when(adminApplicationMapper.countAdminApplications(
                    any(), any(), any(), any(), any(), any())).thenReturn(15L);
            when(adminApplicationMapper.selectAdminPage(
                    any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Collections.emptyList());

            service.listAllApplications(null, null, null, null, null, null, null, null);

            verify(adminApplicationMapper).selectAdminPage(
                    isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(10));
        }

        @Test
        @DisplayName("page=0 时兜底为 1；size 超过上限 100 时截断为 100")
        void shouldClampPagingParams() {
            when(adminApplicationMapper.countAdminApplications(
                    any(), any(), any(), any(), any(), any())).thenReturn(1000L);
            when(adminApplicationMapper.selectAdminPage(
                    any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Collections.emptyList());

            PageResult<AdminApplicationVO> result = service.listAllApplications(
                    0, 500, null, null, null, null, null, null);

            assertEquals(1, result.getPage());
            assertEquals(100, result.getSize());
            // page 兜底为 1，offset = (1-1)*100 = 0
            verify(adminApplicationMapper).selectAdminPage(
                    isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(100));
        }

        @Test
        @DisplayName("第 3 页时 offset = 2 * size")
        void shouldComputeOffset() {
            when(adminApplicationMapper.countAdminApplications(
                    any(), any(), any(), any(), any(), any())).thenReturn(100L);
            when(adminApplicationMapper.selectAdminPage(
                    any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Collections.emptyList());

            service.listAllApplications(3, 20, null, null, null, null, null, null);

            verify(adminApplicationMapper).selectAdminPage(
                    isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(40), eq(20));
        }
    }

    // ==================== 日期区间边界 ====================

    @Nested
    @DisplayName("日期区间边界")
    class DateRangeTests {

        @Test
        @DisplayName("endDate 上界转为次日 0 点（半开区间，含结束当日）")
        void shouldConvertEndDateToExclusiveUpperBound() {
            when(adminApplicationMapper.countAdminApplications(
                    any(), any(), any(), any(), any(), any())).thenReturn(0L);

            service.listAllApplications(null, null, null, null, null, null,
                    "2026-09-01", "2026-09-28");

            // startTime = 2026-09-01T00:00, endTime = 2026-09-29T00:00（含 09-28 全天）
            ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(adminApplicationMapper).countAdminApplications(
                    isNull(), isNull(), isNull(), isNull(),
                    startCaptor.capture(), endCaptor.capture());
            assertEquals(LocalDateTime.of(2026, 9, 1, 0, 0), startCaptor.getValue());
            assertEquals(LocalDateTime.of(2026, 9, 29, 0, 0), endCaptor.getValue());
        }

        @Test
        @DisplayName("startDate 与 endDate 为同一天时区间仍有效")
        void shouldAcceptSameDayRange() {
            when(adminApplicationMapper.countAdminApplications(
                    any(), any(), any(), any(), any(), any())).thenReturn(0L);

            service.listAllApplications(null, null, null, null, null, null,
                    "2026-09-28", "2026-09-28");

            ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(adminApplicationMapper).countAdminApplications(
                    isNull(), isNull(), isNull(), isNull(),
                    startCaptor.capture(), endCaptor.capture());
            assertEquals(LocalDateTime.of(2026, 9, 28, 0, 0), startCaptor.getValue());
            assertEquals(LocalDateTime.of(2026, 9, 29, 0, 0), endCaptor.getValue());
        }
    }

    // ==================== 正常查询与 VO 组装 ====================

    @Nested
    @DisplayName("正常查询与 VO 组装")
    class QueryTests {

        @Test
        @DisplayName("total=0 时返回空页，不再执行列表查询")
        void shouldReturnEmptyPageWhenTotalZero() {
            when(adminApplicationMapper.countAdminApplications(
                    any(), any(), any(), any(), any(), any())).thenReturn(0L);

            PageResult<AdminApplicationVO> result = service.listAllApplications(
                    null, null, null, null, null, null, null, null);

            assertNotNull(result);
            assertEquals(0L, result.getTotal());
            assertEquals(1, result.getPage());
            assertEquals(10, result.getSize());
            assertEquals(0, result.getList().size());
            verify(adminApplicationMapper, never()).selectAdminPage(
                    any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("过滤条件正确透传给 Mapper")
        void shouldPassFiltersToMapper() {
            when(adminApplicationMapper.countAdminApplications(
                    eq(1L), eq(2L), eq(3L), eq(4), any(), any())).thenReturn(0L);

            service.listAllApplications(null, null, 1L, 2L, 3L, 4, null, null);

            verify(adminApplicationMapper).countAdminApplications(
                    eq(1L), eq(2L), eq(3L), eq(4), isNull(), isNull());
        }

        @Test
        @DisplayName("记录组装正确：职位信息（Feign）与求职者姓名（简历）均补全")
        void shouldAssembleVOWithEnrichment() {
            Applications record = Applications.builder()
                    .id(10L).userId(3L).jobId(1L).resumeId(5L).employerId(2L)
                    .status(2).coverLetter("请给我一个机会").remark("已安排面试")
                    .createTime(LocalDateTime.of(2026, 9, 20, 10, 0))
                    .build();
            when(adminApplicationMapper.countAdminApplications(
                    any(), any(), any(), any(), any(), any())).thenReturn(1L);
            when(adminApplicationMapper.selectAdminPage(
                    any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(record));

            JobInfoVO job = JobInfoVO.builder().title("Java 开发工程师").city("杭州")
                    .employerId(2L).build();
            when(jobClient.getJobInfo(1L)).thenReturn(Result.success(job));

            Resume resume = Resume.builder().id(5L).name("张三").title("3年Java经验").build();
            when(resumeMapper.selectByIdOnly(5L)).thenReturn(resume);

            PageResult<AdminApplicationVO> result = service.listAllApplications(
                    null, null, null, null, null, null, null, null);

            assertEquals(1L, result.getTotal());
            assertEquals(1, result.getList().size());
            AdminApplicationVO vo = result.getList().get(0);
            assertEquals(10L, vo.getId());
            assertEquals(3L, vo.getUserId());
            assertEquals("张三", vo.getApplicantName());
            assertEquals(1L, vo.getJobId());
            assertEquals("Java 开发工程师", vo.getJobTitle());
            assertEquals("杭州", vo.getJobCity());
            assertEquals(2L, vo.getEmployerId());
            assertEquals(5L, vo.getResumeId());
            assertEquals("3年Java经验", vo.getResumeTitle());
            assertEquals(2, vo.getStatus());
            assertEquals("请给我一个机会", vo.getCoverLetter());
            assertEquals("已安排面试", vo.getRemark());
            assertEquals(LocalDateTime.of(2026, 9, 20, 10, 0), vo.getCreateTime());
        }

        @Test
        @DisplayName("同一页内相同 jobId/job 的 Feign 调用有缓存，只调一次")
        void shouldCacheJobInfoWithinPage() {
            Applications r1 = Applications.builder().id(1L).userId(3L).jobId(1L).resumeId(5L)
                    .employerId(2L).status(0).build();
            Applications r2 = Applications.builder().id(2L).userId(4L).jobId(1L).resumeId(6L)
                    .employerId(2L).status(1).build();
            when(adminApplicationMapper.countAdminApplications(
                    any(), any(), any(), any(), any(), any())).thenReturn(2L);
            when(adminApplicationMapper.selectAdminPage(
                    any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(r1, r2));
            when(jobClient.getJobInfo(1L)).thenReturn(Result.success(
                    JobInfoVO.builder().title("Java 开发工程师").city("杭州").build()));
            when(resumeMapper.selectByIdOnly(any(Long.class))).thenReturn(null);

            service.listAllApplications(null, null, null, null, null, null, null, null);

            verify(jobClient, times(1)).getJobInfo(1L);
        }

        @Test
        @DisplayName("Feign 失败时降级：职位信息为 null，不影响列表返回")
        void shouldDegradeWhenFeignFails() {
            Applications record = Applications.builder().id(1L).userId(3L).jobId(1L)
                    .resumeId(5L).employerId(2L).status(0).build();
            when(adminApplicationMapper.countAdminApplications(
                    any(), any(), any(), any(), any(), any())).thenReturn(1L);
            when(adminApplicationMapper.selectAdminPage(
                    any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(record));
            when(jobClient.getJobInfo(1L)).thenThrow(new RuntimeException("connection refused"));
            when(resumeMapper.selectByIdOnly(5L)).thenReturn(null);

            PageResult<AdminApplicationVO> result = service.listAllApplications(
                    null, null, null, null, null, null, null, null);

            assertEquals(1, result.getList().size());
            AdminApplicationVO vo = result.getList().get(0);
            assertNull(vo.getJobTitle());
            assertNull(vo.getJobCity());
            assertNull(vo.getApplicantName());
        }

        @Test
        @DisplayName("简历已被删除（selectByIdOnly 返回 null）时姓名与标题为 null")
        void shouldHandleDeletedResume() {
            Applications record = Applications.builder().id(1L).userId(3L).jobId(1L)
                    .resumeId(5L).employerId(2L).status(0).build();
            when(adminApplicationMapper.countAdminApplications(
                    any(), any(), any(), any(), any(), any())).thenReturn(1L);
            when(adminApplicationMapper.selectAdminPage(
                    any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(record));
            when(jobClient.getJobInfo(1L)).thenReturn(Result.success(
                    JobInfoVO.builder().title("Java 开发工程师").city("杭州").build()));
            when(resumeMapper.selectByIdOnly(5L)).thenReturn(null);

            AdminApplicationVO vo = service.listAllApplications(
                    null, null, null, null, null, null, null, null).getList().get(0);

            assertNotNull(vo.getJobTitle());
            assertNull(vo.getApplicantName());
            assertNull(vo.getResumeTitle());
        }
    }
}
