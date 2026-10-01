package com.hire.application.service;

import com.hire.model.dto.ResumeDTO;
import com.hire.model.entity.Resume;

import java.util.List;

public interface ResumeService {
    void createResume(ResumeDTO resumeDTO);

    void updateResume(ResumeDTO resumeDTO);

    List<Resume> listMyResumes();

    Resume getResumeById(Long id);

    Resume getDefaultResume();

    void deleteResume(Long id);

    void setDefaultResume(Long id);
}
