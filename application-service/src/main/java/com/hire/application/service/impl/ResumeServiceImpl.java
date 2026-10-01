package com.hire.application.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hire.model.entity.Resume;
import com.hire.application.mapper.ResumeMapper;
import com.hire.application.service.ResumeService;
import com.hire.common.context.UserContext;
import com.hire.common.exception.BusinessException;
import com.hire.model.dto.ResumeDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ResumeServiceImpl implements ResumeService {

    private static final String ROLE_SEEKER = "seeker";

    @Autowired
    private ResumeMapper resumeMapper;

    @Override
    public void createResume(ResumeDTO resumeDTO) {
        Long userId = UserContext.getCurrentUserId();
        // 校验用户角色是否为求职者
        checkSeekerRole(userId);
        Resume resume = toEntity(resumeDTO);
        resume.setId(null);
        resume.setUserId(userId);
        // 服务端控制默认值，不依赖前端传值
        if (resume.getIsDefault() == null) {
            resume.setIsDefault(0);
        }
        resume.setDeleted(0);
        resumeMapper.insertResume(resume);
        // 回写自增主键，便于前端后续操作
        resumeDTO.setId(resume.getId());
    }

    @Override
    public void updateResume(ResumeDTO resumeDTO) {
        Long userId = UserContext.getCurrentUserId();
        checkSeekerRole(userId);
        if (resumeDTO.getId() == null) {
            throw new BusinessException("简历ID不能为空");
        }
        Resume resume = toEntity(resumeDTO);
        // 归属校验：userId 由服务端从登录态注入，用户只能改自己的简历
        resume.setUserId(userId);
        // 禁止通过更新接口修改删除标志和默认标志
        resume.setDeleted(null);
        resume.setIsDefault(null);
        int rows = resumeMapper.updateResume(resume);
        if (rows == 0) {
            throw new BusinessException("简历不存在或无权操作");
        }
    }

    /**
     * 查询用户所有简历
     * @return
     */
    @Override
    public List<Resume> listMyResumes() {
        Long userId = UserContext.getCurrentUserId();
        checkSeekerRole(userId);
        return resumeMapper.selectByUserId(userId);
    }

    /**
     * 根据id查询用户简历
     * @param id
     * @return
     */
    @Override
    public Resume getResumeById(Long id) {
        Long userId = UserContext.getCurrentUserId();
        Resume resume = resumeMapper.selectById(id, userId);
        if (resume == null) {
            throw new BusinessException("简历不存在或无权查看");
        }
        return resume;
    }

    /**
     * 查询默认简历：优先返回 is_default = 1 的那份，
     * 若用户尚未设置默认简历，则回退为最近创建的一份；没有任何简历时返回 null
     */
    @Override
    public Resume getDefaultResume() {
        Long userId = UserContext.getCurrentUserId();
        checkSeekerRole(userId);
        Resume resume = resumeMapper.selectDefaultByUserId(userId);
        if (resume != null) {
            return resume;
        }
        List<Resume> resumes = resumeMapper.selectByUserId(userId);
        return resumes.isEmpty() ? null : resumes.get(0);
    }

    @Override
    public void deleteResume(Long id) {
        Long userId = UserContext.getCurrentUserId();
        checkSeekerRole(userId);
        int rows = resumeMapper.logicalDelete(id, userId);
        if (rows == 0) {
            throw new BusinessException("简历不存在或无权操作");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void setDefaultResume(Long id) {
        Long userId = UserContext.getCurrentUserId();
        // 校验用户角色是否为求职者
        checkSeekerRole(userId);
        // 先取消该用户其他简历的默认标记
        resumeMapper.clearDefaultByUserId(userId);
        // 再设置指定简历为默认
        int rows = resumeMapper.setDefault(id, userId);
        if (rows == 0) {
            throw new BusinessException("简历不存在或无权操作");
        }
    }

    /**
     * 校验当前用户是否为求职者角色
     * 角色由网关解析 token 后透传并存入 UserContext，不再远程调用用户服务
     */
    private void checkSeekerRole(Long userId) {
        if (userId == null) {
            throw new BusinessException("用户未登录");
        }
        String role = UserContext.getCurrentRole();
        if (StrUtil.isBlank(role)) {
            // token 中没有角色信息（例如改版前签发的旧 token），要求重新登录
            throw new BusinessException("登录信息已失效，请重新登录");
        }
        if (!ROLE_SEEKER.equals(role)) {
            throw new BusinessException("仅求职者可操作简历");
        }
    }

    /**
     * DTO -> 实体
     */
    private Resume toEntity(ResumeDTO resumeDTO) {
        Resume resume = new Resume();
        BeanUtil.copyProperties(resumeDTO, resume);
        return resume;
    }
}
