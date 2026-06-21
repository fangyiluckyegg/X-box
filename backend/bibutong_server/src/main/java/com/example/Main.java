package com.example;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Main {    
    public static void main(String[] args) {
       try {
            // 创建ProcessBuilder，更灵活和安全
            //ProcessBuilder pb = new ProcessBuilder("python", "E:/WorkSpace/xbox_bibutong/src/main/java/com/example/name_butong.py", "arg1", "arg2");
            //ProcessBuilder pb = new ProcessBuilder("python", "E:/WorkSpace/xbox_bibutong/src/main/java/com/example/name_butong.py", "arg1", "arg2");
            ProcessBuilder pb = new ProcessBuilder("python", "/app/ai-infer/name_butong.py", "arg1", "arg2");

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