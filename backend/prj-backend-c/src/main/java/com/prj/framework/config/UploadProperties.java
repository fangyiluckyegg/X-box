package com.prj.framework.config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
//import javax.servlet.MultipartConfigElement;

/** 文件上传
 * 上传路径
 * 文件格式
 */
@Component
@ConfigurationProperties(prefix = "file.upload")

public class UploadProperties {

    private static final Logger logger = LoggerFactory.getLogger(UploadProperties.class);

    private String path;
    private List<String> allowType;

    public String getPath() {
        return path;
    }
    public void setPath(String path) {
        this.path = path;
    }

    public List<String> getAllowType() {
        return allowType;
    }
    public void setAllowType(List<String> allowType) {
        this.allowType = allowType;
    }

    @Value("${spring.servlet.multipart.max-file-size}")
    private String maxFileSize;

    @Value("${spring.servlet.multipart.max-request-size}")
    private String maxRequestSize;

    private long maxSize;

    // [P2-11-FIX] 启动时预解析 maxSize 并缓存，避免每次调用 getMaxSize() 都重新解析字符串
    @javax.annotation.PostConstruct
    public void init() {
        this.maxSize = DataSize.parse(maxFileSize).toBytes();
    }

    public long getMaxSize() {
        // [P1-FIX] 替换 System.out.println 为 SLF4J logger
        logger.debug("maxFileSize: {}", maxFileSize);
        return maxSize;
    }

}