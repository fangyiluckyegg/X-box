package com.prj.controller;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.springframework.web.bind.annotation.*;


/** 启动比对控制器 */
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
public class Main {    
    // 注意：此类包含开发时用于测试的 main 方法，已移除错误的 @PostMapping 映射
    public static void main(String[] args) {

       try {
            // 创建ProcessBuilder，更灵活和安全
            ProcessBuilder pb = new ProcessBuilder("python", "D:/crh123dexiaohao/server/backend/prj-backend-c/src/main/java/com/prj/controller/name_butong.py", "arg1", "arg2");
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            
            // 可以设置工作目录
            // pb.directory(new File("/working/directory"));
            
            Process process = pb.start();
            
            // 读取标准输出
            BufferedReader stdInput = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "UTF-8"));
            
            // 读取错误输出
            BufferedReader stdError = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), "UTF-8"));
            
            String s;
            System.out.println("标准输出:");
            while ((s = stdInput.readLine()) != null) {
                System.out.println(s);
            }
            
            System.out.println("错误输出:");
            while ((s = stdError.readLine()) != null) {
                System.out.println(s);
            }
            
            int exitCode = process.waitFor();
            System.out.println("退出码: " + exitCode);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}