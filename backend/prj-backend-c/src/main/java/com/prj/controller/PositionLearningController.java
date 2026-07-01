package com.prj.controller;

import com.prj.common.ResponseResult;
import com.prj.service.UploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

// Java 标准库导入
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URLEncoder;


/** 文件上传控制器 */
@CrossOrigin(origins = "*", maxAge = 3600)
@RequestMapping("/api/positionLearning")
@RestController
public class PositionLearningController {

    private static final Logger logger = LoggerFactory.getLogger(PositionLearningController.class);

    @Autowired
    private UploadService uploadService;

    /**
     * @param file 上传的文件
     * @return 响应结果
     */
    @PostMapping("/docUpload/uploadDoc")
    public ResponseResult<String> uploadDoc(@RequestParam("file") MultipartFile file) {
        String filename = null;
        try {
            filename = uploadService.uploadDoc(file);
            return ResponseResult.ok(filename, "文件上传成功");
        } catch (IOException e) {
            logger.error("文件上传失败: {}", e.getMessage());
            return ResponseResult.failed(e.getMessage(), "文件上传失败");
        }
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> reactionDownload() {
        String directory = "E:/WorkSpace/prj-backend-c/uploadTemp/doc/";
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