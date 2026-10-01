package com.hire.application.service;

import com.hire.model.vo.AdminApplicationVO;
import com.hire.model.vo.JobApplicationStatisticsVO;
import com.hire.model.vo.PageResult;

/**
 * 管理员投递统计 Service。
 *
 * 与 ApplicationsService 职责分离：
 * ApplicationsService 负责求职者投递与企业侧投递管理，
 * 本接口仅提供管理员维度的投递统计查询。
 */
public interface AdminApplicationService {

    /**
     * 管理员查看指定职位的投递统计。
     *
     * 仅管理员角色可调用：先通过 Feign 校验职位是否存在（不存在则报错），
     * 再按状态分组聚合本库 t_applications，返回各状态数量。
     *
     * @param jobId 职位ID
     * @return 投递总数 + 各状态数量
     */
    JobApplicationStatisticsVO getJobApplicationStatistics(Long jobId);

    /**
     * 管理员分页查询全平台投递记录（API 文档 4.13）。
     *
     * 不限于当前企业或当前求职者，所有未逻辑删除的记录均可查询；
     * jobId / employerId / userId / status / startDate / endDate 均为可选过滤条件。
     *
     * @param page      页码，默认 1
     * @param size      每页条数，默认 10（上限 100）
     * @param jobId     按职位ID筛选，可空
     * @param employerId 按企业用户ID筛选，可空
     * @param userId    按求职者用户ID筛选，可空
     * @param status    投递状态 0~4，可空；非法值报错
     * @param startDate 投递起始日期 YYYY-MM-DD（含当日），可空；格式非法报错
     * @param endDate   投递结束日期 YYYY-MM-DD（含当日），可空；格式非法报错
     * @return 分页结果（total + 当前页列表）
     */
    PageResult<AdminApplicationVO> listAllApplications(Integer page, Integer size,
                                                       Long jobId, Long employerId, Long userId,
                                                       Integer status, String startDate, String endDate);
}
