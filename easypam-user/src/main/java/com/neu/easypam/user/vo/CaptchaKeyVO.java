package com.neu.easypam.user.vo;

import lombok.Data;

@Data
public class CaptchaKeyVO {
    private String captchaKey;
    private String captchaBase64;
}
