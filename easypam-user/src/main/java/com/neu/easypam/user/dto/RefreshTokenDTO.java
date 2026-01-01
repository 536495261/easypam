package com.neu.easypam.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RefreshTokenDTO {
    @NotNull(message="refreshToken不能为空")
    private String refreshToken;
}
