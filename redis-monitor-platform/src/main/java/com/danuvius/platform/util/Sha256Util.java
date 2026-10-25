package com.danuvius.platform.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256摘要计算工具类。
 */
public final class Sha256Util {

    private static final int BUFFER_SIZE = 8192;

    /** 工具类不允许实例化。 */
    private Sha256Util() {
    }

    /**
     * 创建能够在读取数据时同步计算SHA-256的输入流。
     *
     * @param inputStream 原始输入流
     * @return SHA-256输入流
     */
    public static DigestInputStream createInputStream(
            InputStream inputStream) {
        MessageDigest messageDigest = createMessageDigest();
        return new DigestInputStream(inputStream, messageDigest);
    }

    /**
     * 获取SHA-256输入流已经读取内容的最终摘要。
     *
     * 调用前必须先将需要参与计算的数据读取完成。
     *
     * @param inputStream SHA-256输入流
     * @return 十六进制SHA-256摘要
     */
    public static String getDigest(DigestInputStream inputStream) {
        byte[] digestBytes = inputStream.getMessageDigest().digest();
        return toHex(digestBytes);
    }

    /**
     * 计算指定文件的SHA-256摘要。
     *
     * @param file 待计算文件
     * @return 十六进制SHA-256摘要
     * @throws IOException 读取文件失败
     */
    public static String calculate(Path file) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];

        try (InputStream fileInputStream = Files.newInputStream(file);
             DigestInputStream digestInputStream =
                     createInputStream(fileInputStream)) {
            while (digestInputStream.read(buffer) != -1) {
                // 读取文件时，DigestInputStream会同步更新摘要。
            }
            return getDigest(digestInputStream);
        }
    }

    /**
     * 创建SHA-256计算器。
     */
    private static MessageDigest createMessageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前JVM不支持SHA-256",
                    exception
            );
        }
    }

    /**
     * 将摘要字节数组转换为十六进制字符串。
     */
    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }
}
