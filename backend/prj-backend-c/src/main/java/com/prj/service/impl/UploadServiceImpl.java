package com.prj.service.impl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.prj.framework.config.UploadProperties;
import com.prj.common.utils.UploadUtils;
import com.prj.service.UploadService;


import java.io.File;
import java.io.IOException;

/** 文件上传服务实现类 */
@Service
public class UploadServiceImpl implements UploadService {

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
        System.out.println("文件类型: " + file.getContentType());
        
        // 检查文件类型
        if (!uploadProperties.getAllowType().contains(file.getContentType())) {
            throw new IOException("文件类型不支持，仅支持: " + uploadProperties.getAllowType());
        }
        
        // 检查文件大小
        if (file.getSize() > uploadProperties.getMaxSize()) {
            throw new IOException("文件大小超过限制，最大支持: " + (uploadProperties.getMaxSize() / 1024 / 1024) + "MB");
        }
   
        // 生成新的文件名
        //String fileName = UploadUtils.generateFileName(file.getOriginalFilename());
        String fileName = file.getOriginalFilename();
        //String fileName = "names_bak.xlsx"; // 临时测试代码，固定文件名
        
        // 创建上传目录
        File uploadDir = new File(uploadProperties.getPath() + type);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
        
        // 保存文件
        File newFile = new File(uploadDir, fileName);
        file.transferTo(newFile);
        
        System.out.println("文件保存路径: " + newFile.getAbsolutePath());
        return fileName;
    }
}