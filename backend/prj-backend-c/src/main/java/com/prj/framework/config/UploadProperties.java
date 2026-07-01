package com.prj.framework.config;
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
    public long getMaxSize() {        
        System.out.println(maxFileSize);
        this.maxSize = DataSize.parse(maxFileSize).toBytes();
        return maxSize;
    }

}