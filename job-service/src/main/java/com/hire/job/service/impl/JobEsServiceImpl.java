package com.hire.job.service.impl;

import com.hire.job.constant.JobConstants;
import com.hire.job.service.JobEsService;
import com.hire.model.entity.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Elasticsearch 职位索引同步实现
 * 索引名:job_index;title/description/skills 使用 IK 分词(索引 ik_max_word,搜索 ik_smart)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobEsServiceImpl implements JobEsService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestHighLevelClient restHighLevelClient;

    @Override
    public void indexJob(Job job, String employerName, String categoryName) throws IOException {
        Map<String, Object> doc = new HashMap<>();
        put(doc, "id", job.getId());
        put(doc, "employer_id", job.getEmployerId());
        put(doc, "employer_name", employerName);
        put(doc, "title", job.getTitle());
        put(doc, "category_id", job.getCategoryId());
        put(doc, "category_name", categoryName);
        put(doc, "description", job.getDescription());
        put(doc, "job_type", job.getJobType());
        put(doc, "industry", job.getIndustry());
        put(doc, "city", job.getCity());
        put(doc, "experience_req", job.getExperienceReq());
        put(doc, "education_req", job.getEducationReq());
        put(doc, "salary_min", job.getSalaryMin());
        put(doc, "salary_max", job.getSalaryMax());
        put(doc, "skills", job.getSkills());
        put(doc, "headcount", job.getHeadcount());
        put(doc, "status", job.getStatus());
        put(doc, "view_count", job.getViewCount());
        put(doc, "apply_count", job.getApplyCount());
        if (job.getCreateTime() != null) {
            put(doc, "create_time", job.getCreateTime().format(DATE_TIME_FORMATTER));
        }

        IndexRequest request = new IndexRequest(JobConstants.ES_INDEX)
                .id(String.valueOf(job.getId()))
                .source(doc, XContentType.JSON);
        restHighLevelClient.index(request, RequestOptions.DEFAULT);
    }

    @Override
    public void deleteJob(Long id) throws IOException {
        DeleteRequest request = new DeleteRequest(JobConstants.ES_INDEX, String.valueOf(id));
        restHighLevelClient.delete(request, RequestOptions.DEFAULT);
    }

    @Override
    public boolean indexExists() throws IOException {
        GetIndexRequest request = new GetIndexRequest(JobConstants.ES_INDEX);
        return restHighLevelClient.indices().exists(request, RequestOptions.DEFAULT);
    }

    @Override
    public void createIndexIfAbsent() throws IOException {
        if (indexExists()) {
            return;
        }
        // 一次请求完成索引创建 + IK 分词映射,避免两步分离时中途失败留下空 mapping 索引
        CreateIndexRequest createRequest = new CreateIndexRequest(JobConstants.ES_INDEX)
                .settings(Settings.builder()
                        .put("index.number_of_shards", 1)
                        .put("index.number_of_replicas", 0))
                .mapping(buildJobMapping());
        restHighLevelClient.indices().create(createRequest, RequestOptions.DEFAULT);
    }

    /**
     * 构建职位索引映射(与数据库设计文档 6.1 保持一致)
     */
    private XContentBuilder buildJobMapping() throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        builder.startObject("properties");

        builder.startObject("id").field("type", "long").endObject();
        builder.startObject("employer_id").field("type", "long").endObject();
        builder.startObject("employer_name").field("type", "keyword").endObject();

        // 文本字段使用 IK 分词:索引最大粒度切分,搜索智能切分
        builder.startObject("title")
                .field("type", "text")
                .field("analyzer", "ik_max_word")
                .field("search_analyzer", "ik_smart")
                .endObject();
        builder.startObject("category_id").field("type", "long").endObject();
        builder.startObject("category_name").field("type", "keyword").endObject();
        builder.startObject("description")
                .field("type", "text")
                .field("analyzer", "ik_max_word")
                .field("search_analyzer", "ik_smart")
                .endObject();
        builder.startObject("job_type").field("type", "keyword").endObject();
        builder.startObject("industry").field("type", "keyword").endObject();
        builder.startObject("city").field("type", "keyword").endObject();
        builder.startObject("experience_req").field("type", "keyword").endObject();
        builder.startObject("education_req").field("type", "keyword").endObject();
        builder.startObject("salary_min").field("type", "integer").endObject();
        builder.startObject("salary_max").field("type", "integer").endObject();
        builder.startObject("skills")
                .field("type", "text")
                .field("analyzer", "ik_max_word")
                .field("search_analyzer", "ik_smart")
                .endObject();
        builder.startObject("headcount").field("type", "integer").endObject();
        builder.startObject("status").field("type", "byte").endObject();
        builder.startObject("view_count").field("type", "integer").endObject();
        builder.startObject("apply_count").field("type", "integer").endObject();
        builder.startObject("create_time")
                .field("type", "date")
                .field("format", "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis")
                .endObject();

        builder.endObject();
        builder.endObject();
        return builder;
    }

    private void put(Map<String, Object> doc, String key, Object value) {
        if (value != null) {
            doc.put(key, value);
        }
    }
}