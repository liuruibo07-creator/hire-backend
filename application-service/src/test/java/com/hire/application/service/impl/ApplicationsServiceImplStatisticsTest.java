package com.hire.application.service.impl;

import com.hire.api.clients.JobClient;
import com.hire.application.mapper.AdminApplicationMapper;
import com.hire.application.mapper.ApplicationsMapper;
import com.hire.application.mapper.ResumeMapper;
import com.hire.common.exception.BusinessException;
import com.hire.common.utils.RedisUtil;
import com.hire.model.vo.ApplicationStatisticsVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ApplicationsServiceImpl#getApplicationStatistics（API 文档 4.14 内部接口）单元测试。
 *
 * 覆盖：days 校验（缺省/非法/边界）、统计区间起点、每日补 0 与计数映射、
 * 投递总数透传、脏数据（null 日期/count）容错。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("平台投递统计(内部接口)")
class ApplicationsServiceImplStatisticsTest {

    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Mock
    private ApplicationsMapper applicationsMapper;

    @Mock
    private ResumeMapper resumeMapper;

    @Mock
    private JobClient jobClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private AdminApplicationMapper adminApplicationMapper;

    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private ApplicationsServiceImpl service;

    // ==================== days 参数校验 ====================

    @Nested
    @DisplayName("days 参数校验")
    class DaysValidationTests {

        @Test
        @DisplayName("days 缺省时默认统计 7 天")
        void shouldDefaultToSevenDays() {
            when(adminApplicationMapper.countTotalApplications()).thenReturn(0L);
            when(adminApplicationMapper.countDailyNewApplications(any())).thenReturn(List.of());

            ApplicationStatisticsVO vo = service.getApplicationStatistics(null);

            assertEquals(7, vo.getDailyNewApplications().size());
            verifyStartAt(LocalDate.now(ZONE_SHANGHAI).minusDays(6));
        }

        @Test
        @DisplayName("days 非法时报错且不查库")
        void shouldRejectIllegalDays() {
            BusinessException e = assertThrows(BusinessException.class,
                    () -> service.getApplicationStatistics(8));
            assertEquals("days 仅支持 7、30、90", e.getMessage());

            assertThrows(BusinessException.class, () -> service.getApplicationStatistics(0));
            assertThrows(BusinessException.class, () -> service.getApplicationStatistics(-7));

            verify(adminApplicationMapper, never()).countTotalApplications();
            verify(adminApplicationMapper, never()).countDailyNewApplications(any());
        }

        @Test
        @DisplayName("days 边界值 7/30/90 均合法，区间长度与 days 一致")
        void shouldAcceptBoundaryDays() {
            when(adminApplicationMapper.countTotalApplications()).thenReturn(0L);
            when(adminApplicationMapper.countDailyNewApplications(any())).thenReturn(List.of());

            assertEquals(7, service.getApplicationStatistics(7).getDailyNewApplications().size());
            assertEquals(30, service.getApplicationStatistics(30).getDailyNewApplications().size());
            assertEquals(90, service.getApplicationStatistics(90).getDailyNewApplications().size());
        }
    }

    // ==================== 统计区间与补零 ====================

    @Nested
    @DisplayName("统计区间与补零")
    class RangeAndZeroFillTests {

        @Test
        @DisplayName("区间起点为 today-(days-1) 的当日 0 点")
        void shouldStartAtFirstDayMidnight() {
            when(adminApplicationMapper.countTotalApplications()).thenReturn(0L);
            when(adminApplicationMapper.countDailyNewApplications(any())).thenReturn(List.of());

            service.getApplicationStatistics(30);

            verifyStartAt(LocalDate.now(ZONE_SHANGHAI).minusDays(29));
        }

        @Test
        @DisplayName("日期升序且无数据的日期补 0")
        void shouldFillZeroAndSortAscending() {
            when(adminApplicationMapper.countTotalApplications()).thenReturn(0L);
            when(adminApplicationMapper.countDailyNewApplications(any())).thenReturn(List.of());

            ApplicationStatisticsVO vo = service.getApplicationStatistics(7);

            List<ApplicationStatisticsVO.DayCount> days = vo.getDailyNewApplications();
            LocalDate expectedStart = LocalDate.now(ZONE_SHANGHAI).minusDays(6);
            for (int i = 0; i < 7; i++) {
                assertEquals(expectedStart.plusDays(i).toString(), days.get(i).getDate());
                assertEquals(0L, days.get(i).getCount());
            }
        }

        @Test
        @DisplayName("Mapper 返回的日期计数正确映射到对应日期")
        void shouldMapCountsToDates() {
            when(adminApplicationMapper.countTotalApplications()).thenReturn(42L);

            LocalDate today = LocalDate.now(ZONE_SHANGHAI);
            String midDate = today.minusDays(3).toString();
            Map<String, Object> row = new HashMap<>();
            row.put("date", midDate);
            row.put("count", 40L);
            when(adminApplicationMapper.countDailyNewApplications(any())).thenReturn(List.of(row));

            ApplicationStatisticsVO vo = service.getApplicationStatistics(7);

            assertEquals(42L, vo.getApplicationTotal());
            List<ApplicationStatisticsVO.DayCount> days = vo.getDailyNewApplications();
            assertEquals(7, days.size());
            for (ApplicationStatisticsVO.DayCount day : days) {
                if (midDate.equals(day.getDate())) {
                    assertEquals(40L, day.getCount());
                } else {
                    assertEquals(0L, day.getCount());
                }
            }
        }

        @Test
        @DisplayName("Mapper 返回脏数据（date/count 为 null）时跳过，不影响统计")
        void shouldSkipDirtyRows() {
            when(adminApplicationMapper.countTotalApplications()).thenReturn(0L);
            Map<String, Object> dirty = new HashMap<>();
            dirty.put("date", null);
            dirty.put("count", null);
            when(adminApplicationMapper.countDailyNewApplications(any()))
                    .thenReturn(List.of(dirty));

            ApplicationStatisticsVO vo = service.getApplicationStatistics(7);

            assertEquals(7, vo.getDailyNewApplications().size());
            for (ApplicationStatisticsVO.DayCount day : vo.getDailyNewApplications()) {
                assertEquals(0L, day.getCount());
            }
        }
    }

    /**
     * 校验传给 Mapper 的统计区间起点为目标日期的当日 0 点
     */
    private void verifyStartAt(LocalDate expectedStart) {
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(adminApplicationMapper).countDailyNewApplications(captor.capture());
        assertEquals(expectedStart.atStartOfDay(), captor.getValue());
    }
}
