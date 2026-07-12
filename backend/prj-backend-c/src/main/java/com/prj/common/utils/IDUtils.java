package com.prj.common.utils;

import java.util.UUID;

/**
 * 唯一 ID 生成工具类。
 *
 * <p>职责：基于 JDK 的 {@code UUID} 叠加当前毫秒时间戳，生成高概率唯一的字符串 ID，
 * 主要用于上传文件命名等需要"防重名"的场景。
 *
 * <p>与其他模块的关联：
 * - 被依赖：{@code UploadUtils}（文件重命名时使用本方法生成唯一前缀）。
 */
/**
 * @author ：RukiHuang
 * @description：唯一ID生成器
 * @date ：2022/9/2 10:07
 */
public class IDUtils {
    /** 生成唯一的字符串 ID（UUID + 当前时间戳）。 */
    public static String generateUniqueId() {
        return UUID.randomUUID().toString() + System.currentTimeMillis();
    }
}
