package com.hire.application.controller;

import com.hire.application.service.ResumeService;
import com.hire.common.domain.Result;
import com.hire.model.dto.ResumeDTO;
import com.hire.model.entity.Resume;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Resume controller, API doc 4.1-4.6.
 * Gateway route /api/application/** strips /api/application (StripPrefix=2),
 * so frontend /api/application/resumes maps to /resumes here.
 */
@RestController
@RequestMapping("/resumes")
public class ResumeController {
    @Autowired
    private ResumeService resumeService;

    @PostMapping
    public Result createResume(@RequestBody ResumeDTO resumeDTO) {
        resumeService.createResume(resumeDTO);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result updateResume(@PathVariable("id") Long id, @RequestBody ResumeDTO resumeDTO) {
        resumeDTO.setId(id);
        resumeService.updateResume(resumeDTO);
        return Result.success();
    }

    @GetMapping("/my")
    public Result<List<Resume>> listMyResumes() {
        return Result.success(resumeService.listMyResumes());
    }

    @GetMapping("/default")
    public Result<Resume> getDefaultResume() {
        return Result.success(resumeService.getDefaultResume());
    }

    @GetMapping("/{id}")
    public Result<Resume> getResumeById(@PathVariable("id") Long id) {
        return Result.success(resumeService.getResumeById(id));
    }

    @DeleteMapping("/{id}")
    public Result deleteResume(@PathVariable("id") Long id) {
        resumeService.deleteResume(id);
        return Result.success();
    }

    @PutMapping("/{id}/default")
    public Result setDefaultResume(@PathVariable("id") Long id) {
        resumeService.setDefaultResume(id);
        return Result.success();
    }
}
