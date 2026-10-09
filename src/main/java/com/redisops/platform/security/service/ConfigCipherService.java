package com.redisops.platform.security.service;

/**
 * 平台配置文件正文的加密和解密接口。
 */
public interface ConfigCipherService {

    /** 加密Base64编码后的配置正文。 */
    String encrypt(String plainText);

    /** 解密数据库中的配置正文密文。 */
    String decrypt(String ciphertext);
}
