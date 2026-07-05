package com.prj.controller;

import com.prj.common.core.domain.AjaxResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/excel")
public class CompareController {

    // [P1-FIX] 添加权限注解，需登录认证

    /**
     * Excel双文件比对接口
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/compare")
    public AjaxResult compareExcel(
            @RequestParam("originExcel") MultipartFile originExcel,
            @RequestParam("newExcel") MultipartFile newExcel
    ) {
        // 业务比对逻辑（暂时返回成功占位）
        return AjaxResult.success();
    }

    /**
     * 下载比对结果Excel
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/downloadResult")
    public void downloadResult(HttpServletResponse response) {
        // 导出文件流逻辑
    }
}
