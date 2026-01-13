package com.neu.easypam.user.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neu.easypam.common.exception.BusinessException;
import com.neu.easypam.common.result.Result;
import com.neu.easypam.common.utils.JwtUtil;
import com.neu.easypam.user.dto.LoginDTO;
import com.neu.easypam.user.dto.RecodeDTO;
import com.neu.easypam.user.dto.RefreshDTO;
import com.neu.easypam.user.dto.RegisterDTO;
import com.neu.easypam.user.entity.User;
import com.neu.easypam.user.mapper.UserMapper;
import com.neu.easypam.user.service.UserService;
import com.neu.easypam.user.util.CaptchaUtil;
import com.neu.easypam.user.util.MinioUtil;
import com.neu.easypam.user.vo.CaptchaKeyVO;
import com.neu.easypam.user.vo.LoginVO;
import com.neu.easypam.user.vo.TokenVO;
import com.neu.easypam.user.vo.UserVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    private final MinioUtil minioUtil;
    private final CaptchaUtil captchaUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final RocketMQTemplate rocketMQTemplate;
    @Override
    public LoginVO login(LoginDTO dto) {
        
        User user = getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        
        if (user == null || !BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }
        String captcha = stringRedisTemplate.opsForValue().get(dto.getCaptchaKey());
        log.info("captcha:{}", captcha);
        log.info("getCaptcha:{}", dto.getCaptcha());
        log.info("getCaptchaKey:{}", dto.getCaptchaKey());
        if(captcha == null || !captcha.equalsIgnoreCase(dto.getCaptcha())) {
            throw new BusinessException("验证码错误");
        }
        stringRedisTemplate.delete(dto.getCaptchaKey());
        String deviceId = dto.getDeviceId();
        if(deviceId == null ||deviceId.isEmpty()) {
            deviceId = UUID.randomUUID().toString();
        }
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        String accessToken = JwtUtil.generateAccessToken(claims);
        String refreshToken = JwtUtil.generateRefreshToken(user.getId(),deviceId);
        String redisKey = JwtUtil.REFRESH_TOKEN_PREFIX + user.getId() + ":" + deviceId;
        stringRedisTemplate.opsForValue().set(
                redisKey,
                refreshToken,
                JwtUtil.REFRESH_TOKEN_EXPIRE,
                TimeUnit.MILLISECONDS
        );
        log.info("用户{}登录成功，设备ID{}", user.getUsername(), deviceId);
        LoginVO vo = new LoginVO();
        vo.setAccessToken(accessToken);
        vo.setRefreshToken(refreshToken);
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setAvatar(user.getAvatar());
        vo.setExpiresIn(JwtUtil.ACCESS_TOKEN_EXPIRE / 1000);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO dto) {
        long count = count(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BusinessException("用户名已存在");
        }

        User user = new User();
        BeanUtils.copyProperties(dto, user);
        user.setPassword(BCrypt.hashpw(dto.getPassword()));
        user.setStatus(1);
        save(user);
        
        // 发送消息到存储服务，初始化用户存储空间
        rocketMQTemplate.convertAndSend("user-register-topic", user.getId());
        log.info("用户{}注册成功，已发送初始化存储空间消息", user.getUsername());
    }
    @Override
    public UserVO getUserInfo(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    @Override
    public String uploadAvatar(Long userId,MultipartFile file) {
        String objectName = minioUtil.uploadFile(file);
        String avatar = minioUtil.getPresignedUrl(objectName);
        User user = new User();
        user.setId(userId);
        user.setAvatar(avatar);
        boolean success = updateById(user);
        if(!success) {
            throw new BusinessException("上传头像失败");
        }
        return avatar;
    }

    @Override
    public CaptchaKeyVO getCaptcha() {
        return generateCaptcha();
    }

    @Override
    public CaptchaKeyVO refreshCaptcha(RefreshDTO refreshDTO) {
        // 删除旧验证码
        if (refreshDTO.getCaptchaKey() != null && !refreshDTO.getCaptchaKey().isEmpty()) {
            stringRedisTemplate.delete(refreshDTO.getCaptchaKey());
        }
        return generateCaptcha();
    }

    @Override
    public Result<String> recode(Long userId, RecodeDTO recodeDTO) {
        User user = getById(userId);
        if (!recodeDTO.getOlderPassword().equals(recodeDTO.getOlderPasswordConfirm())) {
            throw new BusinessException("旧密码输入不一致");
        }
        // 使用 checkpw 验证旧密码
        if (!BCrypt.checkpw(recodeDTO.getOlderPassword(), user.getPassword())) {
            throw new BusinessException("旧密码错误");
        }
        if (recodeDTO.getOlderPassword().equals(recodeDTO.getNewerPassword())) {
            throw new BusinessException("新旧密码不能相同");
        }
        user.setPassword(BCrypt.hashpw(recodeDTO.getNewerPassword()));
        boolean success = updateById(user);
        if (!success) {
            return Result.error("更新失败，请重试");
        }
        return Result.success("更新密码成功，请重新登录");
    }

    private CaptchaKeyVO generateCaptcha() {
        String key = "captcha:" + IdUtil.simpleUUID();
        log.info("Captcha key: {}", key);
        String captcha = captchaUtil.generateCaptcha();
        log.info("Captcha captcha: {}", captcha);
        stringRedisTemplate.opsForValue().set(key, captcha, 5, TimeUnit.MINUTES);
        CaptchaKeyVO vo = new CaptchaKeyVO();
        vo.setCaptchaKey(key);
        vo.setCaptchaBase64(captchaUtil.generateCaptchaBase64(captcha));
        return vo;
    }

    @Override
    public void logout(String authorization) {
        if(authorization == null || !authorization.startsWith("Bearer ")) {
            return;
        }
        String token = authorization.substring(7);
        try{
            Claims claims = JwtUtil.parseToken(token);
            Long userId = claims.get("userId", Long.class);
            long ttl = JwtUtil.getTokenRemainTime(token);
            if(ttl > 0){
                String jti = claims.getId();
                stringRedisTemplate.opsForValue().set(
                    JwtUtil.TOKEN_BLACKLIST_PREFIX+jti,
                    "1",
                    ttl,
                    TimeUnit.MILLISECONDS
                );
                log.info("Token已经加入黑名单，剩余的有效期为{}ms",ttl);
            }
            Set<String> keys = stringRedisTemplate.keys(JwtUtil.REFRESH_TOKEN_PREFIX + userId + ":*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
            log.info("用户{}登出成功",userId);
        }catch(Exception e){
            log.warn("登出时解析token失败：{}",e.getMessage());
        }
    }

    @Override
    public TokenVO refreshToken(String refreshToken) {
        if(refreshToken == null || refreshToken.isEmpty()) {
            throw new BusinessException("refreshToken不能为空");
        }
        try{
            Claims claims = JwtUtil.parseToken(refreshToken);
            // 检查是否为refreshToken类型
            if (!"refresh".equals(claims.get("type", String.class))) {
                throw new BusinessException("无效的refreshToken");
            }
            Long userId = claims.get("userId", Long.class);
            String deviceId = claims.get("deviceId", String.class);
            String redisKey = JwtUtil.REFRESH_TOKEN_PREFIX + userId + ":" + deviceId;
            String storedToken = stringRedisTemplate.opsForValue().get(redisKey);
            if(storedToken == null || !storedToken.equals(refreshToken)) {
                throw new BusinessException("refreshToken已失效，请重新登录");
            }
            User user = getById(userId);
            if(user == null || user.getStatus() == 0) {
                throw new BusinessException("用户不存在或已禁用");
            }
            Map<String,Object> newClaims = new HashMap<>();
            newClaims.put("userId", userId);
            newClaims.put("username", user.getUsername());
            String newAccessToken = JwtUtil.generateAccessToken(newClaims);
            log.info("用户{}刷新token成功",user.getUsername());
            TokenVO tokenVO = new TokenVO();
            tokenVO.setAccessToken(newAccessToken);
            tokenVO.setExpiresIn(JwtUtil.ACCESS_TOKEN_EXPIRE / 1000);
            return tokenVO;
        }catch(ExpiredJwtException e){
            throw new BusinessException("refreshToken已过期，请重新登录");
        }catch(BusinessException e){
            throw e;
        }catch(Exception e){
            log.error("刷新Token失败", e);
            throw new BusinessException("refreshToken无效");
        }
    }
}
