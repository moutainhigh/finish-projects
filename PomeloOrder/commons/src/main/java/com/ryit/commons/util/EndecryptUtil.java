package com.ryit.commons.util;

import org.apache.shiro.crypto.hash.Md5Hash;

/**
 * @program: whsoso
 * @description: 加密解密工具类 (依赖shiro的加密)
 * @author: Mr.Lu
 * @create: 2019-07-24 14:52
 **/
public class EndecryptUtil {
    public static final String DEFAULT_SALT = "easyweb";

    /**
     * md5加密(没有盐)
     *
     * @param password 要加密的字符串
     */
    public static String encrytMd5(String password) {
        return new Md5Hash(password).toHex();
    }

    /**
     * 指定盐salt进行md5加密
     *
     * @param password       要加密的字符串
     * @param salt           盐
     * @param hashIterations 散列次数
     * @return
     */
    public static String encrytMd5(String password, String salt, int hashIterations) {
        String passwordCipherText = new Md5Hash(password, salt, hashIterations).toHex();
        return passwordCipherText;
    }

    public static String encrytMd5(String password, int hashIterations) {
        return encrytMd5(password, DEFAULT_SALT, hashIterations);
    }

}