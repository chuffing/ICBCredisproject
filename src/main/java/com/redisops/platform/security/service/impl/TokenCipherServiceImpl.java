package com.redisops.platform.security.service.impl;

import lombok.RequiredArgsConstructor;
import com.redisops.platform.agent.config.AgentPlatformProperties;
import com.redisops.platform.security.service.TokenCipherService;
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
 * 执行器 Token 的加密和解密服务。
 */
@Service
@RequiredArgsConstructor
public class TokenCipherServiceImpl implements TokenCipherService {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final AgentPlatformProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 加密执行器 Token 后再保存到数据库。
     *
     * @param plainToken 原始 Token
     * @return Base64 编码的密文
     */
    @Override
    public String encrypt(String plainToken) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, buildKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainToken.getBytes(StandardCharsets.UTF_8));

            // 密文前面保存随机 IV，解密时再把两部分拆开。
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("执行器 Token 加密失败", e);
        }
    }

    /**
     * 解密数据库中保存的执行器 Token。
     *
     * @param ciphertext Base64 编码的密文
     * @return 原始 Token
     */
    @Override
    public String decrypt(String ciphertext) {
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            if (combined.length <= IV_LENGTH) {
                throw new IllegalArgumentException("Token 密文长度不正确");
            }

            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, buildKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("执行器 Token 解密失败", e);
        }
    }

    /**
     * 根据配置密钥生成 128 位 AES 密钥。
     *
     * @return AES 密钥
     */
    private SecretKeySpec buildKey() throws Exception {
        String secret = properties.getTokenSecret();
        if (secret == null || secret.trim().length() < 16) {
            throw new IllegalStateException("platform.agent.token-secret 至少需要 16 个字符");
        }
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(Arrays.copyOf(digest, 16), "AES");
    }
}
