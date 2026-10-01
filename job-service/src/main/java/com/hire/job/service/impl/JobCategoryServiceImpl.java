package com.hire.job.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.hire.common.utils.JsonUtils;
import com.hire.common.utils.RedisUtil;
import com.hire.job.mapper.JobCategoryMapper;
import com.hire.job.service.JobCategoryService;
import com.hire.model.entity.JobCategory;
import com.hire.model.vo.JobCategoryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 职位类别业务实现:平铺列表组装为树形结构。
 *
 * 缓存设计:分类全量只有几十条、代码中没有任何写接口(数据靠初始化脚本维护),
 * 属于准静态数据,因此整表 JSON 缓存到 Redis(job:category:list,TTL 12小时+抖动),
 * 树形组装与"按ID取名称"都基于同一份缓存,顺带消除详情/列表里的逐行查库 N+1。
 * 注意:实体含 LocalDateTime,公共 JsonUtils 未注册时间模块,所以缓存的是无时间字段的 VO。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobCategoryServiceImpl implements JobCategoryService {

    /** 分类平铺列表缓存键 */
    private static final String KEY_CATEGORY_LIST = "job:category:list";
    /** 缓存 TTL:12 小时+随机抖动(小时) */
    private static final long CATEGORY_TTL_HOURS = 12;

    private final JobCategoryMapper jobCategoryMapper;
    private final RedisUtil redisUtil;

    @Override
    public List<JobCategoryVO> listCategoryTree() {
        List<JobCategoryVO> flatList = getCachedFlatList();
        // 保持数据库排序(LinkedHashMap)
        Map<Long, JobCategoryVO> voMap = flatList.stream()
                .collect(Collectors.toMap(JobCategoryVO::getId,
                        vo -> vo,
                        (oldValue, newValue) -> oldValue,
                        LinkedHashMap::new));

        // 组装树:parentId=0 为一级分类,其余挂到父分类下
        List<JobCategoryVO> tree = new ArrayList<>();
        for (JobCategoryVO vo : voMap.values()) {
            Long parentId = vo.getParentId();
            if (parentId == null || parentId == 0L) {
                tree.add(vo);
            } else {
                JobCategoryVO parent = voMap.get(parentId);
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(vo);
                }
            }
        }
        return tree;
    }

    @Override
    public String getCategoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        for (JobCategoryVO vo : getCachedFlatList()) {
            if (categoryId.equals(vo.getId())) {
                return vo.getName();
            }
        }
        return null;
    }

    /**
     * 读取分类平铺列表(Cache Aside):
     * 缓存未命中时回源查库并回填;Redis 异常时 RedisUtil 已降级,直接走数据库。
     */
    private List<JobCategoryVO> getCachedFlatList() {
        String cached = redisUtil.get(KEY_CATEGORY_LIST);
        if (cached != null) {
            List<JobCategoryVO> list = JsonUtils.jsonToList(cached, JobCategoryVO.class);
            if (!list.isEmpty()) {
                return list;
            }
        }
        List<JobCategoryVO> list = jobCategoryMapper.selectAllEnabled().stream()
                .map(category -> BeanUtil.copyProperties(category, JobCategoryVO.class))
                .collect(Collectors.toList());
        redisUtil.set(KEY_CATEGORY_LIST, JsonUtils.toJson(list),
                Duration.ofHours(CATEGORY_TTL_HOURS + RandomUtil.randomInt(0, 2)));
        return list;
    }
}
