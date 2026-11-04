package com.danuvius.platform.util.http;
import com.google.common.io.BaseEncoding;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * API加密工具类
 *
 * @author iPan
 * @date 2022-06-29
 */
public final class ApiUtil {
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyyMMddHHmmss");
    public static final String ALGORITHM_MD5 = "MD5";
    public static final String ALGORITHM_SHA_256 = "SHA-256";
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /**
     * 根据算法名称返回{@code MessageDigest}实例
     *
     * @param algorithm 摘要算法名称
     * @return 返回摘要实例
     * @see MessageDigest#getInstance(String)
     */
    private static MessageDigest getMessageDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            // safe to swallow
            throw new RuntimeException(e);
        }
    }

    /**
     * SHA256摘要计算
     *
     * @param data 待处理的数据
     * @return 计算后的数据
     */
    public static byte[] sha256(final String data) {
        Validate.notBlank(data, "The data is null or empty.");
        return sha256(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * SHA256摘要计算
     *
     * @param data 待处理的数据
     * @return 计算后的数据
     */
    public static byte[] sha256(final byte[] data) {
        if (data == null) {
            throw new NullPointerException("The data is null");
        }
        if (data.length == 0) {
            throw new IllegalArgumentException("The data is empty");
        }
        return getMessageDigest(ALGORITHM_SHA_256).digest(data);
    }

    /**
     * SHA256摘要计算,计算结果转为16进制字符串返回
     *
     * @param data 待处理的数据
     * @return 16进制的字符串
     */
    public static String sha256Hex(final String data) {
        return BaseEncoding.base16().encode(sha256(data));
    }

    /**
     * SHA256摘要计算
     *
     * @param data 待处理的数据
     * @return 计算后的数据
     */
    public static String sha256Hex(final byte[] data) {
        if (data == null) {
            throw new NullPointerException("The data is null");
        }
        if (data.length == 0) {
            throw new IllegalArgumentException("The data is empty");
        }
        return BaseEncoding.base16().encode(sha256(data));
    }

    /**
     * md5计算
     *
     * @param data 待计算的数据
     * @return md5计算结果
     */
    public static byte[] md5(final byte[] data) {
        if (data == null) {
            throw new NullPointerException("The data is null");
        }
        if (data.length == 0) {
            throw new IllegalArgumentException("The data is empty");
        }
        return getMessageDigest(ALGORITHM_MD5).digest(data);
    }

    /**
     * 计算字符串的md5值，结果转为16进制的字符串返回,默认采用{@link StandardCharsets#UTF_8}编码
     *
     * @param data 待计算的数据
     * @return 16进制的字符串
     */
    public static String md5(final String data) {
        return md5(data, StandardCharsets.UTF_8);
    }

    /**
     * 计算字符串的md5值，结果转为16进制的字符串返回
     *
     * @param data   待计算的数据
     * @param charset 指定编码，为空时采用UTF8读取
     * @return 16进制的字符串
     */
    public static String md5(final String data, final Charset charset) {
        Validate.notBlank(data, "The data is null or empty.");
        byte[] salt = data.getBytes(charset == null ? StandardCharsets.UTF_8 : charset);
        byte[] md5Data = md5(salt);
        return BaseEncoding.base16().encode(md5Data);
    }

    // -- HmacSHA256 -- //
    public static byte[] hmacSHA256Byte(final String message, final String encKey) {
        byte[] bytes = null;
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(encKey.getBytes("UTF-8"), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            bytes = sha256_HMAC.doFinal(message.getBytes("UTF-8"));
        } catch (Exception e) {
            throw new RuntimeException("hmacSHA256加密异常", e);
        }
        return bytes;
    }

    public static String hmacSHA256Hex(final String message, final String encKey) {
        byte[] data = hmacSHA256Byte(message, encKey);
        String result = BaseEncoding.base16().encode(data);
        return result;
    }

    public static String hmacSHA256Base64(final String message, final String encKey) {
        byte[] data = hmacSHA256Byte(message, encKey);
        String result = BaseEncoding.base64().encode(data);
        return result;
    }

    /**
     * 将输入流的数据分段计算
     *
     * @param digest 某种摘要算法
     * @param data   待计算的数据
     * @return 返回计算后的结果
     * @throws IOException IO异常
     */
    static byte[] digest(final MessageDigest digest, final InputStream data) throws IOException {
        byte[] buffer = new byte[1024];
        for (int read; (read = data.read(buffer, 0, 1024)) != -1; ) {
            digest.update(buffer, 0, read);
        }
        return digest.digest();
    }
    /**
     * OPEN-BODY-SIG方式生成签名
     *
     * 签名算法
     * 1) json内容转二进制
     * 2) sha256Hex加密二进制，并转小写；
     * 3) 字符串拼接 "appId + timestamp + nonce + 小写的sha256Hex"
     * 4) 使用hmacSHA256加密对拼接字符串进行加密，秘钥使用appKey；
     * 5) 对加密后字节转base64
     */
    public static String createSignature(String appId, String appKey, String timestamp, String nonce, String bodyJson) {
        Validate.notBlank(appId);
        Validate.notBlank(appKey);
        Validate.notBlank(timestamp);
        Validate.notBlank(nonce);
        Validate.notBlank(bodyJson);

        String result = "";
        try {
            byte[] a = bodyJson.getBytes("utf-8");
            String b = sha256Hex(a).toLowerCase();
            String c = StringUtils.join(appId, timestamp, nonce, b);
            String d = appKey;
            byte[] e = hmacSHA256Byte(c, d);
            result = BaseEncoding.base64().encode(e);
        } catch(Exception e) {
            throw new RuntimeException("生成签名异常", e);
        }
        return result;
    }

    /**
     * 创建Timestamp（格式：yyyyMMddHHmmss）
     */
    public static String createTimestamp() {
        return SDF.format(new Date());
    }

    /**
     * 创建随机数nonce（不超过128位）
     */
    public static String createNonce() {
        return nextLong(ThreadLocalRandom.current(), 100000000, 999999999) + "";
    }

    /**
     * 创建唯一ID（客户流水号，不超过32位）
     */
    public static String createId() {
        String uuid = new UUID(ThreadLocalRandom.current().nextLong(), ThreadLocalRandom.current().nextLong()).toString();
        return ApiUtil.md5(uuid);
    }

    public static Date parseDate(String str) throws ParseException {
        return SDF.parse(str);
    }

    private static long nextLong(Random random, long min, long max) {
        Validate.isTrue(max >= min, "Start value must be smaller or equal to end value.");
        if (min < 0) {
            throw new IllegalArgumentException("min (" + min + ") must be >= 0");
        }
        if (min == max) {
            return min;
        }
        return (long) (min + ((max - min) * random.nextDouble()));
    }

    //-- 正文加密/解密 --//
    public static String encrypt(String input, String key) {
        if(StringUtils.isBlank(input)) {
            return null;
        }
        byte[] crypted = null;
        try {
            SecretKeySpec skey = new SecretKeySpec(key.getBytes(UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, skey);
            crypted = cipher.doFinal(input.getBytes(UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("内容加密异常", e);
        }
        return BaseEncoding.base64().encode(crypted);
    }

    public static String decrypt(String input, String key, String charSet) {
        if(StringUtils.isBlank(input)) {
            return null;
        }
        byte[] output = null;
        try {
            SecretKeySpec skey = new SecretKeySpec(key.getBytes(charSet), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, skey);
            output = cipher.doFinal(BaseEncoding.base64().decode(input));
        } catch (Exception e) {
            throw new RuntimeException("内容解密异常", e);
        }
        return new String(output);
    }

    public static String decrypt(String input, String key) {
        if(StringUtils.isBlank(input)) {
            return null;
        }
        byte[] output = null;
        try {
            SecretKeySpec skey = new SecretKeySpec(key.getBytes(UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, skey);
            output = cipher.doFinal(BaseEncoding.base64().decode(input));
        } catch (Exception e) {
            throw new RuntimeException("内容解密异常", e);
        }
        return new String(output, StandardCharsets.UTF_8);
    }

}
