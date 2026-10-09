package com.redisops.platform.security.service.impl;

import com.redisops.platform.agent.config.AgentPlatformProperties;
import com.redisops.platform.security.service.ConfigCipherService;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 使用AES-GCM加密平台保存的Redis和Sentinel配置正文。
 */
@Service
public class ConfigCipherServiceImpl implements ConfigCipherService {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final AgentPlatformProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    /** 注入平台安全配置。 */
    public ConfigCipherServiceImpl(AgentPlatformProperties properties) {
        this.properties = properties;
    }

    /** 加密配置正文并把随机IV放在密文前面。 */
    @Override
    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, buildKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("配置文件加密失败", e);
        }
    }

    /** 解密数据库中保存的配置正文。 */
    @Override
    public String decrypt(String ciphertext) {
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            if (combined.length <= IV_LENGTH) {
                throw new IllegalArgumentException("配置密文长度不正确");
            }
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, buildKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("配置文件解密失败", e);
        }
    }

    /** 根据配置密钥生成128位AES密钥。 */
    private SecretKeySpec buildKey() throws Exception {
        String secret = properties.getConfigSecret();
        if (secret == null || secret.trim().length() < 16) {
            throw new IllegalStateException("platform.agent.config-secret 至少需要16个字符");
        }
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(Arrays.copyOf(digest, 16), "AES");
    }
}
