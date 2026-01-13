package com.neu.easypam.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neu.easypam.common.result.Result;
import com.neu.easypam.user.dto.LoginDTO;
import com.neu.easypam.user.dto.RecodeDTO;
import com.neu.easypam.user.dto.RefreshDTO;
import com.neu.easypam.user.dto.RegisterDTO;
import com.neu.easypam.user.entity.User;
import com.neu.easypam.user.vo.CaptchaKeyVO;
import com.neu.easypam.user.vo.LoginVO;
import com.neu.easypam.user.vo.TokenVO;
import com.neu.easypam.user.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;

public interface UserService extends IService<User> {
    LoginVO login(LoginDTO dto);
    void register(RegisterDTO dto);
    UserVO getUserInfo(Long userId);

    String uploadAvatar(Long userId,MultipartFile file);

    CaptchaKeyVO getCaptcha();

    CaptchaKeyVO refreshCaptcha(RefreshDTO refreshDTO);

    Result<String> recode(Long userId, @Valid RecodeDTO recodeDTO);

    void logout(String authorization);
    TokenVO refreshToken(String refreshToken);

}
