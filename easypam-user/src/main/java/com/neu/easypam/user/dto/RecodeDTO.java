package com.neu.easypam.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RecodeDTO {
    @NotBlank(message = "更新的密码不能为空")
    private String newerPassword;
    @NotBlank(message = "旧密码的确认不能为空")
    private String olderPasswordConfirm;
    @NotBlank(message = "原始密码不能为空")
    private String olderPassword;
}
