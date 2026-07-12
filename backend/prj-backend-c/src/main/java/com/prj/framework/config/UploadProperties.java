package com.prj.framework.config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
//import jakarta.servlet.MultipartConfigElement;

/**
 * 文件上传配置属性（绑定 application.yml 中 {@code file.upload.*} 前缀）。
 *
 * <p>职责：
 * 以类型安全方式读取文件上传相关配置：存储根路径（path）、允许的内容类型（allowType）、
 * 以及从 Spring 上传配置解析出的最大字节数（maxSize），供 {@code UploadServiceImpl} 做校验与落盘使用。
 *
 * <p>与其他模块的关联：
 * - 被依赖：{@code UploadServiceImpl}（读取 path/allowType/maxSize）。
 *
 * <p>说明：maxSize 在 {@link #init()} 中（@PostConstruct）由 max-file-size 字符串预解析并缓存，避免每次上传重复解析，见 [P2-11-FIX]。
 */
/** 文件上传
 * 上传路径
 * 文件格式
 */
@Component
@ConfigurationProperties(prefix = "file.upload")

public class UploadProperties {

    private static final Logger logger = LoggerFactory.getLogger(UploadProperties.class);

    /** 文件上传根路径。 */
    private String path;
    /** 允许上传的内容类型（MIME）列表。 */
    private List<String> allowType;

    /** 获取上传根路径。 */
    public String getPath() {
        return path;
    }
    /** 设置上传根路径。 */
    public void setPath(String path) {
        this.path = path;
    }

    /** 获取允许的内容类型列表。 */
    public List<String> getAllowType() {
        return allowType;
    }
    /** 设置允许的内容类型列表。 */
    public void setAllowType(List<String> allowType) {
        this.allowType = allowType;
    }

    @Value("${spring.servlet.multipart.max-file-size}")
    private String maxFileSize;

    @Value("${spring.servlet.multipart.max-request-size}")
    private String maxRequestSize;

    /** 预解析后的最大文件字节数（由 init() 填充）。 */
    private long maxSize;

    // [P2-11-FIX] 启动时预解析 maxSize 并缓存，避免每次调用 getMaxSize() 都重新解析字符串
    @jakarta.annotation.PostConstruct
    public void init() {
        this.maxSize = DataSize.parse(maxFileSize).toBytes();
    }

    /** 获取最大文件字节数（读取预解析缓存值）。 */
    public long getMaxSize() {
        // [P1-FIX] 替换 System.out.println 为 SLF4J logger
        logger.debug("maxFileSize: {}", maxFileSize);
        return maxSize;
    }

}
