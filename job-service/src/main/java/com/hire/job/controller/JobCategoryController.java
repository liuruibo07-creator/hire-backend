package com.hire.job.controller;

import com.hire.common.domain.Result;
import com.hire.job.service.JobCategoryService;
import com.hire.model.vo.JobCategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 职位类别接口,对应 API 设计文档 3.7
 */
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class JobCategoryController {

    private final JobCategoryService jobCategoryService;

    /** 3.7 职位类别列表(树形结构,无需登录) */
    @GetMapping
    public Result<List<JobCategoryVO>> listCategories() {
        return Result.success("查询成功", jobCategoryService.listCategoryTree());
    }
}