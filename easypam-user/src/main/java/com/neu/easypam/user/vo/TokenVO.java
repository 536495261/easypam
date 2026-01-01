package com.neu.easypam.user.vo;

import lombok.Data;

@Data
public class TokenVO {
    private String accessToken;
    private long expiresIn;
}
