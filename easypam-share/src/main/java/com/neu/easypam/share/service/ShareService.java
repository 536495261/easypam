package com.neu.easypam.share.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neu.easypam.share.entity.ShareInfo;

import java.util.List;

public interface ShareService extends IService<ShareInfo> {
    ShareInfo createShare(Long fileId, Long userId, Integer shareType, Integer expireDays);
    ShareInfo getByShareCode(String shareCode);
    void cancelShare(Long shareId, Long userId);
    List<ShareInfo> listUserShares(Long userId);
    boolean verifyExtractCode(String shareCode, String extractCode);
}
