package com.neu.easypam.user.controller;

import com.neu.easypam.common.result.Result;
import com.neu.easypam.user.dto.*;
import com.neu.easypam.user.service.UserService;
import com.neu.easypam.user.vo.CaptchaKeyVO;
import com.neu.easypam.user.vo.LoginVO;
import com.neu.easypam.user.vo.TokenVO;
import com.neu.easypam.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(userService.login(dto));
    }
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        userService.register(dto);
        return Result.success();
    }
    @Operation(summary = "获取用户信息")
    @GetMapping("/info")
    public Result<UserVO> getUserInfo(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(userService.getUserInfo(userId));
    }

    @Operation(summary = "上传用户头像")
    @PutMapping("/avatar")
    public Result<String> uploadAvatar(@RequestHeader("X-User-Id") Long userId,@RequestParam("file") MultipartFile file) {
        return Result.success(userService.uploadAvatar(userId,file));
    }
    @Operation(summary = "获取验证码")
    @GetMapping("/captcha")
    public Result<CaptchaKeyVO> getAvatar() {
        return Result.success(userService.getCaptcha());
    }
    @Operation(summary = "更新密码")
    @PutMapping("/recode")
    public Result<String> recode(@RequestHeader("X-User-Id") Long userId,@RequestBody @Valid RecodeDTO recodeDTO) {
        return userService.recode(userId,recodeDTO);
    }
    @Operation(summary = "刷新验证码")
    @GetMapping("/captcha/refresh")
    public Result<CaptchaKeyVO> refreshCaptcha(@RequestBody @Valid RefreshDTO refreshDTO, HttpServletRequest request) {
        return Result.success(userService.refreshCaptcha(refreshDTO,request));
    }

    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authorization) {
        userService.logout(authorization);
        return Result.success();
    }
    @Operation(summary = "刷新Token")
    @PostMapping("/refresh")
    public Result<TokenVO> refreshToken(@Valid @RequestBody RefreshTokenDTO dto) {
        return Result.success(userService.refreshToken(dto.getRefreshToken()));
    }
}
