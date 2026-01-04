package com.neu.easypam.common.dto;

import lombok.Data;

@Data
public class SaveShareDTO {
    private Long sourceFileId;
    private Long targetUserId;
    private Long parentId;
}
