package com.example.cloud_box.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {
    private String url;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private int connectTimeout = 10;
    private int writeTimeout = 30;
    private int readTimeout = 30;
}
