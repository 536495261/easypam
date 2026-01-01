package com.neu.easypam.user.vo;

import lombok.Data;

@Data
public class LoginVO {
    private String accessToken;
    private String refreshToken;
    private Long userId;
    private String username;
    private String avatar;
    private Long expiresIn; // accessToken过期时间（秒）
}
