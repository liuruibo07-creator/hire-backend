package com.hire.job.mapper;

import com.hire.model.entity.Job;
import com.hire.model.vo.JobInfoVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 职位 Mapper。
 * 投递流程的查询与计数递增用注解 SQL；其余 SQL 在 JobMapper.xml。
 */
public interface JobMapper {

    /**
     * 查询未删除职位的简要信息(内部接口 3.12 用)。
     * 用别名把下划线字段映射成驼峰属性，不依赖 mybatis 的驼峰转换开关。
     */
    @Select("SELECT id, employer_id AS employerId, title, city, status " +
            "FROM t_job WHERE id = #{id} AND deleted = 0")
    JobInfoVO selectInfoById(@Param("id") Long id);

    /**
     * 投递数自增。
     * 用 SQL 层面的自增而不是"查出来+1再写回"，避免并发投递时互相覆盖。
     */
    @Update("UPDATE t_job SET apply_count = apply_count + 1 WHERE id = #{id} AND deleted = 0")
    int increaseApplyCount(@Param("id") Long id);

    /**
     * 浏览数自增（职位详情接口用，Redis 不可用时的降级路径）
     */
    @Update("UPDATE t_job SET view_count = view_count + 1 WHERE id = #{id} AND deleted = 0")
    int increaseViewCount(@Param("id") Long id);

    /**
     * 浏览量批量回刷（ViewCountFlushTask 定时把 Redis 计数增量累加回数据库）
     */
    @Update("UPDATE t_job SET view_count = view_count + #{delta} WHERE id = #{id} AND deleted = 0")
    int addViewCount(@Param("id") Long id, @Param("delta") long delta);

    /** 根据ID查询未删除职位(含全部字段)，SQL 见 JobMapper.xml */
    Job selectById(@Param("id") Long id);

    /** 查询未删除职位的完整详情，SQL 见 JobMapper.xml */
    Job selectDetailById(@Param("id") Long id);

    /** 新增职位，SQL 见 JobMapper.xml */
    int insert(Job job);

    /** 按非空字段更新职位，SQL 见 JobMapper.xml */
    int update(Job job);

    /** 修改职位状态，SQL 见 JobMapper.xml */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 写入审核结果(状态+审核意见+审核人+审核时间)，SQL 见 JobMapper.xml */
    int updateWithReview(@Param("id") Long id, @Param("status") Integer status,
                         @Param("reviewRemark") String reviewRemark,
                         @Param("reviewedBy") Long reviewedBy,
                         @Param("reviewedAt") java.time.LocalDateTime reviewedAt);

    /** 清空审核信息(编辑后重新进入待审核时用)，SQL 见 JobMapper.xml */
    int clearReview(@Param("id") Long id);

    /** 企业分页查询自己发布的职位，SQL 见 JobMapper.xml */
    List<Job> selectPageByEmployer(@Param("offset") int offset, @Param("size") int size,
                                   @Param("employerId") Long employerId,
                                   @Param("status") Integer status, @Param("keyword") String keyword);

    /** 企业职位总数，筛选条件与 selectPageByEmployer 一致 */
    long countByEmployer(@Param("employerId") Long employerId,
                         @Param("status") Integer status, @Param("keyword") String keyword);

    /** 管理员分页查询职位（可按状态/城市/企业/关键词筛选），SQL 见 JobMapper.xml */
    List<Job> selectPageForAdmin(@Param("offset") int offset, @Param("size") int size,
                                 @Param("status") Integer status, @Param("city") String city,
                                 @Param("employerId") Long employerId, @Param("keyword") String keyword);

    /** 管理员分页查询的总记录数，筛选条件与 selectPageForAdmin 保持一致 */
    long countForAdmin(@Param("status") Integer status, @Param("city") String city,
                       @Param("employerId") Long employerId, @Param("keyword") String keyword);

    /** 未逻辑删除的职位总数(内部统计 3.13 用) */
    long countTotal();

    /** 查询所有未删除职位(ES 索引重建后全量同步用)，SQL 见 JobMapper.xml */
    List<Job> selectAllForSync();

    /** 按天统计每日新增职位数，返回 [{date: 'yyyy-MM-dd', count: n}]，SQL 见 JobMapper.xml */
    List<Map<String, Object>> countDailyNew(@Param("start") java.time.LocalDateTime start);
}
