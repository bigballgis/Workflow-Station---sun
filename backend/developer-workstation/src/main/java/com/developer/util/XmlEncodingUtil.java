package com.developer.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * XML编码工具类
 * 用于处理XML内容的Base64编码/解码，避免数据库存储时的转义问题
 */
public final class XmlEncodingUtil {
    
    private XmlEncodingUtil() {
        // 工具类不允许实例化
    }
    
    /**
     * 将XML字符串编码为Base64
     * @param xml 原始XML字符串
     * @return Base64编码后的字符串
     */
    public static String encode(String xml) {
        if (xml == null || xml.isEmpty()) {
            return xml;
        }
        return Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * 将Base64编码的字符串解码为XML
     * @param encoded Base64编码的字符串
     * @return 解码后的XML字符串
     */
    public static String decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return encoded;
        }
        try {
            // 移除可能存在的换行符和空白字符
            String cleanEncoded = encoded.replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(cleanEncoded);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 如果不是有效的Base64，返回原始字符串（兼容旧数据）
            return encoded;
        }
    }
    
    /**
     * 检查字符串是否是Base64编码
     * @param str 要检查的字符串
     * @return 如果是Base64编码返回true
     */
    public static boolean isBase64Encoded(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        // 移除空白字符后检查
        String cleanStr = str.replaceAll("\\s+", "");
        // Base64字符串只包含 A-Z, a-z, 0-9, +, /, = 
        // 并且长度是4的倍数（可能有填充）
        if (!cleanStr.matches("^[A-Za-z0-9+/]*={0,2}$")) {
            return false;
        }
        try {
            Base64.getDecoder().decode(cleanStr);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * 智能解码 - 如果是Base64则解码，否则返回原始字符串
     * 用于兼容旧数据
     * @param str 可能是Base64编码的字符串
     * @return 解码后的字符串或原始字符串
     */
    public static String smartDecode(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        // 如果以 <?xml 或 < 开头，说明是原始XML，直接返回
        String trimmed = str.trim();
        if (trimmed.startsWith("<?xml") || trimmed.startsWith("<")) {
            return str;
        }
        // 尝试Base64解码
        return decode(str);
    }
}
