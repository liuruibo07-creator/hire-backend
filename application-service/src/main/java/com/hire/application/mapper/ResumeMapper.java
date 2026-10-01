package com.hire.application.mapper;

import com.hire.model.entity.Resume;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ResumeMapper {
    /**
     * 插入简历
     * @param resume 简历实体
     */
    @Insert("INSERT INTO t_resume (user_id, title, name, gender, birth_year, phone, email, education, university, major, " +
            "graduation_date, work_experience, skills, expected_position, expected_salary, expected_city, " +
            "self_evaluation, is_default, deleted) " +
            "VALUES (#{userId}, #{title}, #{name}, #{gender}, #{birthYear}, #{phone}, #{email}, #{education}, #{university}, " +
            "#{major}, #{graduationDate}, #{workExperience}, #{skills}, #{expectedPosition}, #{expectedSalary}, " +
            "#{expectedCity}, #{selfEvaluation}, #{isDefault}, #{deleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertResume(Resume resume);

    /**
     * 更新简历
     * @param resume 简历实体
     */
    int updateResume(Resume resume);

    /**
     * 查询当前用户的所有未删除简历
     */
    List<Resume> selectByUserId(@Param("userId") Long userId);

    /**
     * 根据简历 ID 和用户 ID 查询未删除简历
     */
    Resume selectById(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 根据简历 ID 查询未删除简历（不限用户）
     * 企业查看收到的投递时，需要读取求职者的简历，此时当前登录人是企业而非简历所有者
     */
    Resume selectByIdOnly(@Param("id") Long id);

    /**
     * 查询当前用户的默认简历（is_default = 1）
     */
    Resume selectDefaultByUserId(@Param("userId") Long userId);

    /**
     * 逻辑删除简历
     */
    int logicalDelete(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 取消当前用户所有简历的默认标记
     */
    int clearDefaultByUserId(@Param("userId") Long userId);

    /**
     * 将指定简历设为默认简历
     */
    int setDefault(@Param("id") Long id, @Param("userId") Long userId);
}
