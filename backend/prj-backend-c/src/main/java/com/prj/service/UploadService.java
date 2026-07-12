package com.prj.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件上传服务接口（Service 层契约）。
 *
 * <p>职责：
 * 定义文件上传业务能力，具体实现见 {@code com.prj.service.impl.UploadServiceImpl}。
 * 供 PositionLearningController 调用，负责把上传的 MultipartFile 落盘并返回文件名。
 */
/**
 * 文件上传服务接口
 */
public interface UploadService {

    /** 上传文件
     * @param file 上传的文件
     * @return 服务器存储的文件名
     * @throws IOException 上传异常
     */
    String uploadDoc(MultipartFile file) throws IOException;
        

}
