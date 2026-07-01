package com.prj.common.utils;

/*** 文件名替换工具 避免文件名重复 */
public class UploadUtils {
    public static String generateFileName(String oldName) {
        String suffix = oldName.substring(oldName.lastIndexOf("."));
        return IDUtils.generateUniqueId() + suffix;
    }
}