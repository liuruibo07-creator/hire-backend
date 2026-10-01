package com.hire.application.service;

import com.hire.model.dto.ApplicationStatusUpdateDTO;
import com.hire.model.dto.ApplicationSubmitDTO;
import com.hire.model.vo.ApplicationDetailVO;
import com.hire.model.vo.ApplicationStatisticsVO;
import com.hire.model.vo.MyApplicationVO;
import com.hire.model.vo.PageResult;
import com.hire.model.vo.ReceivedApplicationVO;

public interface ApplicationsService {

    /**
     * 投递简历。
     *
     * @param dto 投递参数：职位ID、简历ID、求职附言
     * @return 投递记录ID
     */
    Long submitApplication(ApplicationSubmitDTO dto);

    /**
     * 分页查询当前求职者的投递历史。
     *
     * @param page   页码，默认 1
     * @param size   每页条数，默认 10
     * @param status 投递状态，为空时不过滤
     * @return 分页结果：投递信息 + 职位基本信息（Feign 实时获取）+ 简历标题
     */
    PageResult<MyApplicationVO> listMyApplications(Integer page, Integer size, Integer status);

    /**
     * 分页查询当前企业收到的投递列表。
     *
     * @param page   页码，默认 1
     * @param size   每页条数，默认 10
     * @param jobId  职位ID，为空时不过滤
     * @param status 投递状态，为空时不过滤
     * @return 分页结果：投递信息 + 求职者姓名 + 简历信息
     */
    PageResult<ReceivedApplicationVO> listReceivedApplications(Integer page, Integer size, Long jobId, Integer status);

    /**
     * 企业更新投递状态（1-已查看 / 2-面试邀请 / 3-已录用 / 4-已拒绝）。
     *
     * @param id  投递记录ID
     * @param dto 目标状态与备注
     */
    void updateApplicationStatus(Long id, ApplicationStatusUpdateDTO dto);

    /**
     * 查看投递详情（需要登录，仅投递人本人或职位发布企业可见）。
     *
     * @param id 投递记录ID
     * @return 投递信息 + 简历详情（本库查询）+ 职位信息（Feign 获取）
     */
    ApplicationDetailVO getApplicationDetail(Long id);

    /**
     * 平台投递统计（API 文档 4.14 内部接口，仅供 user-service 通过 Feign 调用）。
     *
     * 统计规则与文档 2.8 节保持一致：days 仅支持 7/30/90，缺省 7；
     * 按 create_time 统计每日新增投递，区间内无数据的日期补 0，日期升序返回。
     *
     * @param days 统计天数，可空，缺省 7
     * @return 投递总数 + 每日新增投递数
     */
    ApplicationStatisticsVO getApplicationStatistics(Integer days);

}
