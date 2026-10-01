package com.hire.application.mapper;

import com.hire.model.dto.StatusCountDTO;
import com.hire.model.entity.Applications;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ApplicationsMapper {

    /**
     * 新增投递记录，主键回填到 id 上
     */
    void insert(Applications applications);

    /**
     * 判断当前用户是否已投递过该职位（用于提前返回友好提示）
     * 真正的兜底是 uk_user_job 唯一索引，应对并发重复投递
     */
    int countByUserAndJob(@Param("userId") Long userId, @Param("jobId") Long jobId);

    /**
     * 根据投递ID和投递人查询未删除的投递记录
     */
    Applications selectByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 根据投递ID查询未删除的投递记录（不限定用户，企业侧更新状态时使用）
     */
    Applications selectById(@Param("id") Long id);

    /**
     * 更新投递状态与备注（企业侧操作）。
     * 只更新未删除记录，返回受影响行数用于判断投递是否存在。
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status, @Param("remark") String remark);

    /**
     * 分页查询当前用户的投递记录（按投递时间倒序），status 为空时不过滤
     */
    List<Applications> selectPageByUser(@Param("userId") Long userId, @Param("status") Integer status,
                                        @Param("offset") int offset, @Param("size") int size);

    /**
     * 统计当前用户的投递总数，status 为空时不过滤
     */
    long countByUser(@Param("userId") Long userId, @Param("status") Integer status);

    /**
     * 分页查询当前企业收到的投递记录（按投递时间倒序），jobId / status 为空时不过滤
     */
    List<Applications> selectPageByEmployer(@Param("employerId") Long employerId, @Param("jobId") Long jobId,
                                            @Param("status") Integer status,
                                            @Param("offset") int offset, @Param("size") int size);

    /**
     * 统计当前企业收到的投递总数，jobId / status 为空时不过滤
     */
    long countByEmployer(@Param("employerId") Long employerId, @Param("jobId") Long jobId,
                         @Param("status") Integer status);

    /**
     * 按投递状态分组统计指定职位的投递数量（不含已删除记录），一条 GROUP BY SQL 完成聚合。
     * 某状态没有投递时不会出现在结果集中，由调用方兜底为 0。
     */
    List<StatusCountDTO> countByJobGroupByStatus(@Param("jobId") Long jobId);
}
