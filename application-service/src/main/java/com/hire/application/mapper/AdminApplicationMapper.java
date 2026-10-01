package com.hire.application.mapper;

import com.hire.model.dto.StatusCountDTO;
import com.hire.model.entity.Applications;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 管理员投递统计 Mapper。
 *
 * 仅服务于管理员统计接口，与 ApplicationsMapper 职责分离：
 * ApplicationsMapper 负责 C 端求职者与企业侧的投递 CRUD，
 * 本接口只提供按职位维度的投递统计聚合查询。
 */
public interface AdminApplicationMapper {

    /**
     * 按投递状态分组统计指定职位的投递数量（不含已删除记录），一条 GROUP BY SQL 完成聚合。
     * 某状态没有投递时不会出现在结果集中，由调用方兜底为 0。
     *
     * @param jobId 职位ID
     * @return 各状态及其对应的投递数量列表
     */
    @Select("SELECT status, COUNT(*) AS total FROM t_applications " +
            "WHERE job_id = #{jobId} AND deleted = 0 GROUP BY status")
    List<StatusCountDTO> countByJobGroupByStatus(@Param("jobId") Long jobId);

    /**
     * 管理员分页查询全平台投递记录（不含已删除），按投递时间倒序。
     * jobId / employerId / userId / status / startTime / endTime 均为可选过滤条件，为空时不过滤。
     * startTime / endTime 为半开区间：create_time >= startTime 且 create_time < endTime，
     * 由调用方把 endDate 转成“次日 0 点”传入，保证“含结束当日”且边界无歧义。
     */
    @Select("<script>" +
            "SELECT id, user_id AS userId, job_id AS jobId, resume_id AS resumeId, " +
            "employer_id AS employerId, cover_letter AS coverLetter, remark, status, deleted, " +
            "create_time AS createTime, update_time AS updateTime " +
            "FROM t_applications WHERE deleted = 0 " +
            "<if test='jobId != null'>AND job_id = #{jobId}</if> " +
            "<if test='employerId != null'>AND employer_id = #{employerId}</if> " +
            "<if test='userId != null'>AND user_id = #{userId}</if> " +
            "<if test='status != null'>AND status = #{status}</if> " +
            "<if test='startTime != null'>AND create_time &gt;= #{startTime}</if> " +
            "<if test='endTime != null'>AND create_time &lt; #{endTime}</if> " +
            "ORDER BY id DESC LIMIT #{offset}, #{size}" +
            "</script>")
    List<Applications> selectAdminPage(@Param("jobId") Long jobId,
                                       @Param("employerId") Long employerId,
                                       @Param("userId") Long userId,
                                       @Param("status") Integer status,
                                       @Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    /**
     * 管理员统计全平台投递总数（不含已删除），过滤条件与 selectAdminPage 完全一致，
     * 保证 total 与列表数据口径相同。
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM t_applications WHERE deleted = 0 " +
            "<if test='jobId != null'>AND job_id = #{jobId}</if> " +
            "<if test='employerId != null'>AND employer_id = #{employerId}</if> " +
            "<if test='userId != null'>AND user_id = #{userId}</if> " +
            "<if test='status != null'>AND status = #{status}</if> " +
            "<if test='startTime != null'>AND create_time &gt;= #{startTime}</if> " +
            "<if test='endTime != null'>AND create_time &lt; #{endTime}</if>" +
            "</script>")
    long countAdminApplications(@Param("jobId") Long jobId,
                                @Param("employerId") Long employerId,
                                @Param("userId") Long userId,
                                @Param("status") Integer status,
                                @Param("startTime") LocalDateTime startTime,
                                @Param("endTime") LocalDateTime endTime);

    /**
     * 平台未逻辑删除的投递记录总数（内部统计 4.14 用）
     */
    @Select("SELECT COUNT(*) FROM t_applications WHERE deleted = 0")
    long countTotalApplications();

    /**
     * 按天统计每日新增投递数（按 create_time，不含已删除），返回 [{date: 'yyyy-MM-dd', count: n}]。
     * 区间内某天没有投递时不会出现在结果集中，由调用方补 0。
     *
     * @param start 统计区间起点（含，当日 0 点）
     */
    @Select("SELECT date_format(create_time, '%Y-%m-%d') AS date, COUNT(*) AS count " +
            "FROM t_applications WHERE deleted = 0 AND create_time >= #{start} " +
            "GROUP BY date_format(create_time, '%Y-%m-%d')")
    List<Map<String, Object>> countDailyNewApplications(@Param("start") LocalDateTime start);
}
