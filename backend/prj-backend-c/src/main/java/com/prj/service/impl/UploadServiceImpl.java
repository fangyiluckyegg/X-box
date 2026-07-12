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

/**
 * 文件上传服务实现类。
 *
 * <p>职责：
 * 实现 {@link UploadService}，提供文档上传落盘能力。上传前依次进行
 * 内容类型校验、文件大小校验、扩展名白名单校验（防恶意文件/路径遍历），
 * 生成随机文件名后写入配置指定目录，最终返回服务端存储的文件名。
 *
 * <p>与其他模块的关联：
 * - 依赖：{@code UploadProperties}（上传根路径/允许类型/最大大小配置）、
 *         {@code UploadUtils}（随机文件名生成）、
 *         {@code ServiceException}（业务异常）。
 * - 被依赖：{@code PositionLearningController}。
 *
 * <p>安全说明：扩展名白名单与随机文件名见上方 [P0-FIX]，避免上传可执行文件与路径穿越。
 */
/** 文件上传服务实现类 */
@Service
public class UploadServiceImpl implements UploadService {

    /** 类级日志对象。 */
    private static final Logger logger = LoggerFactory.getLogger(UploadServiceImpl.class);

    // [P0-FIX] 文件扩展名白名单，防止上传恶意可执行文件
    /** 允许上传的文件扩展名白名单（小写）。 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "pdf", "doc", "docx", "xls", "xlsx", "txt", "zip");

    /** 上传相关配置，由 Spring 自动注入。 */
    @Autowired
    private UploadProperties uploadProperties;

    /** 文档上传入口：固定写入 "doc" 子目录。 */
    @Override
    public String uploadDoc(MultipartFile file) throws IOException {
        return uploadFile(file, "doc");
    }

    /**
     * 通用文件上传方法
     * @param file 上传的文件
     * @param type 文件类型目录（子目录名）
     * @return 服务器存储的文件名
     * @throws IOException 上传异常（类型/大小不支持时以 IOException 形式抛出）
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
