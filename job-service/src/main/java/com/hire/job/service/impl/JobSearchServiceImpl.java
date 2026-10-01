package com.hire.job.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hire.job.constant.JobConstants;
import com.hire.job.exception.BusinessException;
import com.hire.job.service.JobSearchService;
import com.hire.model.dto.JobSearchDTO;
import com.hire.model.vo.JobSearchItemVO;
import com.hire.model.vo.PageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Elasticsearch 职位搜索实现
 * 关键词使用 IK 智能分词,多字段检索 + 条件筛选 + 高亮 + 排序 + 分页
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobSearchServiceImpl implements JobSearchService {

    private final RestHighLevelClient restHighLevelClient;

    @Override
    public PageVO<JobSearchItemVO> search(JobSearchDTO dto) {
        int page = dto.getPage() == null || dto.getPage() < 1 ? 1 : dto.getPage();
        int size = dto.getSize() == null || dto.getSize() < 1 ? 10 : Math.min(dto.getSize(), 100);

        // 深分页防护:ES 默认 from+size 不得超过 index.max_result_window(10000)
        if ((long) page * size > JobConstants.ES_MAX_RESULT_WINDOW) {
            throw new BusinessException(400, "结果过多,请细化筛选条件");
        }

        SearchSourceBuilder source = new SearchSourceBuilder();

        // 布尔查询:只有关键词走 must(参与相关度打分),其余精确匹配/范围过滤一律走 filter(不参与打分,可命中 filter cache)
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        // 只搜索"招聘中"的职位
        boolQuery.filter(QueryBuilders.termQuery("status", JobConstants.STATUS_ONLINE));
        // 关键词:多字段全文检索(IK智能分词),标题权重更高
        if (StrUtil.isNotBlank(dto.getKeyword())) {
            boolQuery.must(QueryBuilders.multiMatchQuery(dto.getKeyword(), "title", "description", "skills")
                    .field("title", 3.0f)
                    .analyzer("ik_smart"));
        }
        // 条件筛选(keyword字段精确匹配)
        if (StrUtil.isNotBlank(dto.getJobType())) {
            boolQuery.filter(QueryBuilders.termQuery("job_type", dto.getJobType()));
        }
        if (StrUtil.isNotBlank(dto.getCity())) {
            boolQuery.filter(QueryBuilders.termQuery("city", dto.getCity()));
        }
        if (StrUtil.isNotBlank(dto.getIndustry())) {
            boolQuery.filter(QueryBuilders.termQuery("industry", dto.getIndustry()));
        }
        if (StrUtil.isNotBlank(dto.getExperienceReq())) {
            boolQuery.filter(QueryBuilders.termQuery("experience_req", dto.getExperienceReq()));
        }
        if (StrUtil.isNotBlank(dto.getEducationReq())) {
            boolQuery.filter(QueryBuilders.termQuery("education_req", dto.getEducationReq()));
        }
        if (dto.getCategoryId() != null) {
            boolQuery.filter(QueryBuilders.termQuery("category_id", dto.getCategoryId()));
        }
        // 薪资范围筛选
        if (dto.getSalaryMin() != null) {
            boolQuery.filter(QueryBuilders.rangeQuery("salary_min").gte(dto.getSalaryMin()));
        }
        if (dto.getSalaryMax() != null) {
            boolQuery.filter(QueryBuilders.rangeQuery("salary_max").lte(dto.getSalaryMax()));
        }
        source.query(boolQuery);

        // 排序:relevance(默认,按相关度打分)/latest/salary_desc/salary_asc
        String sort = StrUtil.blankToDefault(dto.getSort(), JobConstants.SORT_RELEVANCE);
        switch (sort) {
            case JobConstants.SORT_LATEST:
                source.sort("create_time", SortOrder.DESC);
                break;
            case JobConstants.SORT_SALARY_DESC:
                source.sort("salary_max", SortOrder.DESC);
                break;
            case JobConstants.SORT_SALARY_ASC:
                source.sort("salary_min", SortOrder.ASC);
                break;
            default:
                // 相关度排序:使用默认打分,无需显式排序
                break;
        }

        // 高亮:标题返回整段,描述返回片段
        HighlightBuilder highlightBuilder = new HighlightBuilder().preTags("<em>").postTags("</em>");
        highlightBuilder.field(new HighlightBuilder.Field("title").numOfFragments(0));
        highlightBuilder.field(new HighlightBuilder.Field("description").fragmentSize(100).numOfFragments(1));
        source.highlighter(highlightBuilder);

        // 分页
        source.from((page - 1) * size).size(size);

        SearchRequest request = new SearchRequest(JobConstants.ES_INDEX).source(source);
        try {
            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            List<JobSearchItemVO> list = new ArrayList<>();
            for (SearchHit hit : response.getHits().getHits()) {
                list.add(convertHit(hit));
            }
            long total = response.getHits().getTotalHits().value;
            return new PageVO<>(total, list);
        } catch (IOException e) {
            log.error("Elasticsearch 职位搜索失败", e);
            throw new BusinessException(500, "搜索服务暂不可用,请稍后重试");
        }
    }

    /**
     * 搜索结果命中转 VO
     */
    private JobSearchItemVO convertHit(SearchHit hit) {
        Map<String, Object> source = hit.getSourceAsMap();
        JobSearchItemVO vo = new JobSearchItemVO();
        vo.setId(toLong(source.get("id")));
        vo.setTitle(toStr(source.get("title")));
        vo.setEmployerName(toStr(source.get("employer_name")));
        vo.setCategoryName(toStr(source.get("category_name")));
        vo.setCity(toStr(source.get("city")));
        vo.setSalaryMin(toInt(source.get("salary_min")));
        vo.setSalaryMax(toInt(source.get("salary_max")));
        vo.setJobType(toStr(source.get("job_type")));
        vo.setExperienceReq(toStr(source.get("experience_req")));
        vo.setEducationReq(toStr(source.get("education_req")));
        vo.setSkills(toStr(source.get("skills")));
        vo.setCreateTime(toStr(source.get("create_time")));
        // 高亮片段
        Map<String, List<String>> highlight = new HashMap<>();
        hit.getHighlightFields().forEach((name, field) -> {
            List<String> fragments = Arrays.stream(field.getFragments())
                    .map(Text::string)
                    .collect(Collectors.toList());
            highlight.put(name, fragments);
        });
        vo.setHighlight(highlight);
        return vo;
    }

    private Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private Integer toInt(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private String toStr(Object value) {
        return value == null ? null : value.toString();
    }
}