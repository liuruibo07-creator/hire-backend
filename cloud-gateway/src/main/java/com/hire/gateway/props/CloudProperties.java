package com.hire.gateway.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "cloud")
@Data
public class CloudProperties {
    private List<String> whitePaths;
}
