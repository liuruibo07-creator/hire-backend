package com.hire.job.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch 7.12.1 客户端配置
 */
@Configuration
public class ElasticSearchConfig {

    @Value("${cloud.elasticsearch.host:localhost}")
    private String host;

    @Value("${cloud.elasticsearch.port:9200}")
    private int port;

    @Bean(destroyMethod = "close")
    public RestHighLevelClient restHighLevelClient() {
        return new RestHighLevelClient(
                RestClient.builder(new HttpHost(host, port, "http")));
    }
}