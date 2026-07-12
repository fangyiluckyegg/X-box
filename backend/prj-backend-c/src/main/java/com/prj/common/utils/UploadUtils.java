package com.prj.common.utils;

/**
 * 上传文件名工具类。
 *
 * <p>职责：
 * 为上传文件生成"唯一文件名"——保留原扩展名，前缀使用 {@link IDUtils#generateUniqueId()} 保证不重名，
 * 避免多用户/多次上传互相覆盖。
 *
 * <p>与其他模块的关联：
 * - 依赖：{@code IDUtils}（生成唯一前缀）。
 * - 被依赖：{@code UploadServiceImpl}（上传落盘前生成存储文件名）。
 */
/*** 文件名替换工具 避免文件名重复 */
public class UploadUtils {
    /** 基于原文件名生成唯一文件名（保留扩展名，前缀为唯一ID）。 */
    public static String generateFileName(String oldName) {
        String suffix = oldName.substring(oldName.lastIndexOf("."));
        return IDUtils.generateUniqueId() + suffix;
    }
}
