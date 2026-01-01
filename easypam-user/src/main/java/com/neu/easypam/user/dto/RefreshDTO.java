package com.neu.easypam.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

@Data
public class RefreshDTO {
    @NotBlank(message = "验证码的key不能为空")
    private String captchaKey;
}
