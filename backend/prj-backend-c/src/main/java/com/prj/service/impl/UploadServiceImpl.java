package com.prj.service.impl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.prj.framework.config.UploadProperties;
import com.prj.common.utils.UploadUtils;
import com.prj.common.exception.ServiceException;
import com.prj.service.UploadService;


import java.io.File;
import java.io.IOException;
import java.util.Set;

/** 文件上传服务实现类 */
@Service
public class UploadServiceImpl implements UploadService {

    private static final Logger logger = LoggerFactory.getLogger(UploadServiceImpl.class);

    // [P0-FIX] 文件扩展名白名单，防止上传恶意可执行文件
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "pdf", "doc", "docx", "xls", "xlsx", "txt", "zip");

    @Autowired
    private UploadProperties uploadProperties;

    @Override
    public String uploadDoc(MultipartFile file) throws IOException {
        return uploadFile(file, "doc");
    }

    /**
     * 通用文件上传方法
     * @param file 上传的文件
     * @param type 文件类型目录
     * @return 服务器存储的文件名
     * @throws IOException 上传异常
     */
    private String uploadFile(MultipartFile file, String type) throws IOException {
        // [P1-FIX] 替换 System.out.println 为 SLF4J logger
        logger.info("文件类型: {}", file.getContentType());
        
        // 检查文件类型
        if (!uploadProperties.getAllowType().contains(file.getContentType())) {
            throw new IOException("文件类型不支持，仅支持: " + uploadProperties.getAllowType());
        }
        
        // 检查文件大小
        if (file.getSize() > uploadProperties.getMaxSize()) {
            throw new IOException("文件大小超过限制，最大支持: " + (uploadProperties.getMaxSize() / 1024 / 1024) + "MB");
        }
   
        // [P0-FIX] 恢复随机文件名 + 扩展名白名单校验，防止路径遍历和恶意文件上传
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ServiceException("不支持的文件类型: " + extension);
        }
        String fileName = UploadUtils.generateFileName(originalFilename);
        
        // 创建上传目录
        File uploadDir = new File(uploadProperties.getPath() + type);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
        
        // 保存文件
        File newFile = new File(uploadDir, fileName);
        file.transferTo(newFile);
        
        // [P1-FIX] 替换 System.out.println 为 SLF4J logger
        logger.info("文件保存路径: {}", newFile.getAbsolutePath());
        return fileName;
    }
}