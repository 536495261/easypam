package com.neu.easypam.share.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neu.easypam.share.dto.CreateShareDTO;
import com.neu.easypam.share.vo.PreviewVO;
import com.neu.easypam.share.vo.ShareVO;
import com.neu.easypam.share.entity.ShareInfo;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

public interface ShareService extends IService<ShareInfo> {

    /**
     * 创建分享
     */
    ShareVO createShare(CreateShareDTO dto, Long userId);

    /**
     * 获取分享信息（公开分享）
     */
    ShareVO getShareInfo(String shareCode);

    /**
     * 验证提取码并获取分享信息（私密分享）
     */
    ShareVO verifyAndGetShare(String shareCode, String extractCode);

    /**
     * 取消分享
     */
    void cancelShare(String shareCode, Long userId);

    /**
     * 我的分享列表
     */
    List<ShareVO> listMyShares(Long userId);

    /**
     * 增加下载次数
     */
    void incrementDownloadCount(String shareCode);

    /**
     * 获取分享文件下载链接
     */
    String getDownloadUrl(String shareCode);

    PreviewVO getPreviewInfo(String shareCode);
}
