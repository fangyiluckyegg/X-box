package com.prj.common.utils;

import java.util.UUID;

/**
 * @author ：RukiHuang
 * @description：唯一ID生成器
 * @date ：2022/9/2 10:07
 */
public class IDUtils {
    public static String generateUniqueId() {
        return UUID.randomUUID().toString() + System.currentTimeMillis();
    }
}