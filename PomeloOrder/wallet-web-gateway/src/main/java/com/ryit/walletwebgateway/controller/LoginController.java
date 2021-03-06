package com.ryit.walletwebgateway.controller;


import com.ryit.commons.base.AjaxResult;
import com.ryit.commons.constant.JwtConstant;
import com.ryit.commons.constant.RedisConstants;
import com.ryit.commons.entity.pojo.WalletUser;
import com.ryit.commons.util.EndecryptUtil;
import com.ryit.commons.util.JwtUtil;
import com.ryit.commons.util.RedisUtil;
import com.ryit.commons.util.StringUtil;
import com.ryit.walletwebgateway.feign.WalletUserFeignClient;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;

/**
 * @author : 刘修
 * @Date : 2019/8/19 12:12
 */
@RestController
public class LoginController {

    private Logger log = LoggerFactory.getLogger(LoginController.class);

    @Resource
    private WalletUserFeignClient walletUserFeignClient;

    @Autowired
    private RedisUtil redisUtil;

    @Value("${refreshTokenExpireTime}")
    private String refreshTokenExpireTime;

    @PostMapping("/login")
    public AjaxResult login(@RequestBody WalletUser walletUser, HttpServletResponse response) {
        try {
            String phone = walletUser.getPhone();
            String password = walletUser.getPassword();
            if (StringUtils.isEmpty(phone) || StringUtils.isEmpty(password)) {
                return AjaxResult.error(0, "参数错误");
            }
            WalletUser user = (WalletUser) redisUtil.getObject(String.format(RedisConstants.PREFIX_WALLET_USER, phone));
            if (null == user) {
                user = walletUserFeignClient.getWalletUserByPhone(phone);
            }
            if (null == user || user.getPassword() == null) {
                return AjaxResult.error(0, "用户不存在");
            } else if (!user.getPassword().equals(EndecryptUtil.encrytMd5(password))) {
                return AjaxResult.error(0, "密码错误");
            }

            // 清除可能存在的shiro权限信息缓存
            if (redisUtil.hasKey(String.format(RedisConstants.PREFIX_SHIRO_CACHE, phone))) {
                redisUtil.remove(String.format(RedisConstants.PREFIX_SHIRO_CACHE, phone));
            }

            // 设置RefreshToken，时间戳为当前时间戳，直接设置即可(不用先删后设，会覆盖已有的RefreshToken)
            String currentTimeMillis = String.valueOf(System.currentTimeMillis());
            redisUtil.set(String.format(RedisConstants.PREFIX_WALLET_WEB_REFRESH_TOKEN, phone), currentTimeMillis,
                    Integer.parseInt(refreshTokenExpireTime));
            //设置用户缓存
            redisUtil.set(String.format(RedisConstants.PREFIX_WALLET_USER, phone), user,
                    Integer.parseInt(refreshTokenExpireTime));

            // 从Header中Authorization返回AccessToken，时间戳为当前时间戳
            String token = JwtUtil.sign(phone, currentTimeMillis);
            response.setHeader("Authorization", token);
            response.setHeader("Access-Control-Expose-Headers", "Authorization");

            user.setPassword(null);
            if(StringUtil.isEmpty(user.getAvatar())){
                user.setAvatar("");
            }
            return AjaxResult.success(user);

        } catch (AuthenticationException e) {
            return AjaxResult.error(0, e.getMessage());
        } catch (Exception e) {
            log.error(e.getMessage());
            return AjaxResult.error(0, "未知错误");
        }
    }

    @GetMapping("/logout")
    public AjaxResult logout() {
        Subject subject = SecurityUtils.getSubject();
        String token = (String) subject.getPrincipal();
        if (StringUtil.isNotBlank(token)) {
            String account = JwtUtil.getClaim(token, JwtConstant.ACCOUNT);
            if (redisUtil.hasKey(String.format(RedisConstants.PREFIX_WALLET_WEB_REFRESH_TOKEN, account))) {
                redisUtil.remove(String.format(RedisConstants.PREFIX_WALLET_WEB_REFRESH_TOKEN, account));
            }
            if (redisUtil.hasKey(String.format(RedisConstants.PREFIX_WALLET_USER, account))) {
                redisUtil.remove(String.format(RedisConstants.PREFIX_WALLET_USER,account));
            }
        }
        return AjaxResult.success();
    }
}
