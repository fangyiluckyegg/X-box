package com.prj.controller;

import com.prj.common.core.domain.AjaxResult;
import com.prj.service.UploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

// Java 标准库导入
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URLEncoder;


/**
 * 岗位学习文件上传/下载控制器（PositionLearning）。
 *
 * <p>职责：
 * 提供岗位学习相关文档的上传与下载接口：
 * - {@code POST /api/positionLearning/docUpload/uploadDoc} 上传文档，文件落盘路径由配置注入；
 * - {@code GET  /api/positionLearning/download} 下载内置的固定示例文件（names_budong.xlsx）。
 *
 * <p>与其他模块的关联：
 * - 依赖：{@code UploadService}（封装文件上传落盘逻辑）、
 *         {@code @Value("${file.upload.path}")}（上传根路径配置，避免硬编码）、
 *         Spring Security {@code @PreAuthorize}（接口需登录认证）。
 * - 被依赖：前端岗位学习相关页面（web/prj-frontend/src/views）通过 api 调用本控制器。
 *
 * <p>安全/规范说明：已移除 {@code @CrossOrigin(origins="*")}（改由全局 CorsFilter 管控跨域）、
 * 移除硬编码绝对路径（改由配置注入）、统一使用 AjaxResult 作为上传响应（见上方 [P0-FIX]/[P1-FIX] 备注）。
 */
/** 文件上传控制器 */
// [P0-FIX] 移除@CrossOrigin(origins="*")，由全局CorsFilter统一管控跨域
// [P0-FIX] 添加 @Validated 开启控制器级别输入校验
@Validated
@RequestMapping("/api/positionLearning")
@RestController
public class PositionLearningController {

    /** 类级日志对象。 */
    private static final Logger logger = LoggerFactory.getLogger(PositionLearningController.class);

    // [P0-FIX] 文件路径从配置注入，移除硬编码绝对路径
    /** 文件上传根路径，由 application.yml 的 file.upload.path 注入，默认 ./uploadTemp/。 */
    @Value("${file.upload.path:./uploadTemp/}")
    private String uploadPath;

    /** 文件上传业务服务，由 Spring 自动注入。 */
    @Autowired
    private UploadService uploadService;

    // [P1-FIX] 添加权限注解，需登录认证

    /**
     * 上传岗位学习文档。
     *
     * @param file 前端上传的 MultipartFile 文件
     * @return 成功时返回文件名；IO 异常时返回失败响应（不抛出，避免暴露堆栈）
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/docUpload/uploadDoc")
    // [P1-FIX] 统一使用 AjaxResult 替代 ResponseResult，消除双响应类
    public AjaxResult uploadDoc(@RequestParam("file") MultipartFile file) {
        String filename = null;
        try {
            filename = uploadService.uploadDoc(file);
            return AjaxResult.success("文件上传成功", filename);
        } catch (IOException e) {
            logger.error("文件上传失败: {}", e.getMessage());
            return AjaxResult.error("文件上传失败");
        }
    }

    /**
     * 下载内置的固定示例文件 names_budong.xlsx。
     *
     * @return 文件流响应（200 + 附件头）；文件不存在返回 404，异常返回 400
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/download")
    public ResponseEntity<Resource> reactionDownload() {
        // [P0-FIX] 使用配置注入的路径，移除硬编码Windows绝对路径
        String directory = uploadPath + "doc/";
        String fileName = "names_budong.xlsx";
        String filePath = directory + fileName;
        
        try {
            Path path = Paths.get(filePath);
            Resource resource = new FileSystemResource(path);
            
            if (!resource.exists()) {
                // logger.warn("文件不存在: {}", filePath);
                return ResponseEntity.notFound().build();
            }
            
            String encodedFileName = URLEncoder.encode(fileName, "UTF-8")
                    .replaceAll("\\+", "%20");
            
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                           "attachment; filename*=UTF-8''" + encodedFileName)
                    .body(resource);
                    
        } catch (Exception e) {
            //logger.error("文件下载失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
