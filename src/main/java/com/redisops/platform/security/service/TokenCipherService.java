package com.redisops.platform.security.service;

/**
 * 执行器 Token 加密和解密接口。
 */
public interface TokenCipherService {

    /** 加密原始 Token。 */
    String encrypt(String plainToken);

    /** 解密数据库中的 Token 密文。 */
    String decrypt(String ciphertext);
}
