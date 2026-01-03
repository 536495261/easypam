package com.neu.easypam.share.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neu.easypam.common.dto.FileInfoDTO;
import com.neu.easypam.common.exception.BusinessException;
import com.neu.easypam.common.feign.FileFeignClient;
import com.neu.easypam.common.result.Result;
import com.neu.easypam.share.dto.CreateShareDTO;
import com.neu.easypam.share.vo.ShareVO;
import com.neu.easypam.share.entity.ShareInfo;
import com.neu.easypam.share.mapper.ShareMapper;
import com.neu.easypam.share.service.ShareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareServiceImpl extends ServiceImpl<ShareMapper, ShareInfo> implements ShareService {

    private final FileFeignClient fileFeignClient;

    @Value("${share.base-url:http://localhost:8080/share/}")
    private String shareBaseUrl;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShareVO createShare(CreateShareDTO dto, Long userId) {
        // 1. 校验文件存在且属于当前用户
        Result<FileInfoDTO> result = fileFeignClient.checkFileOwner(dto.getFileId(), userId);
        if (result.getCode() != 200 || result.getData() == null) {
            throw new BusinessException("文件不存在或无权限");
        }
        FileInfoDTO fileInfo = result.getData();

        // 2. 生成唯一分享码
        String shareCode = generateUniqueShareCode();

        // 3. 私密分享生成提取码
        String extractCode = null;
        if (dto.getShareType() != null && dto.getShareType() == 1) {
            extractCode = RandomUtil.randomNumbers(4);
        }

        // 4. 计算过期时间
        LocalDateTime expireTime = null;
        if (dto.getExpireDays() != null && dto.getExpireDays() > 0) {
            expireTime = LocalDateTime.now().plusDays(dto.getExpireDays());
        }

        // 5. 保存分享记录
        ShareInfo share = new ShareInfo();
        share.setUserId(userId);
        share.setFileId(dto.getFileId());
        share.setShareCode(shareCode);
        share.setExtractCode(extractCode);
        share.setShareType(dto.getShareType() != null ? dto.getShareType() : 0);
        share.setExpireTime(expireTime);
        share.setViewCount(0);
        share.setDownloadCount(0);
        share.setStatus(1);
        save(share);

        log.info("用户{}创建分享：fileId={}, shareCode={}", userId, dto.getFileId(), shareCode);

        // 6. 构建返回结果
        return buildShareVO(share, fileInfo, true);
    }

    @Override
    public ShareVO getShareInfo(String shareCode) {
        ShareInfo share = getValidShare(shareCode);

        // 私密分享不直接返回文件信息，需要验证提取码
        if (share.getShareType() == 1) {
            ShareVO vo = new ShareVO();
            vo.setShareCode(shareCode);
            vo.setShareUrl(shareBaseUrl + shareCode);
            vo.setExpireTime(share.getExpireTime());
            // 不返回文件信息，提示需要提取码
            return vo;
        }
        
        // 增加浏览次数
        share.setViewCount(share.getViewCount() + 1);
        updateById(share);

        // 获取文件信息
        FileInfoDTO fileInfo = getFileInfo(share.getFileId());

        return buildShareVO(share, fileInfo, false);
    }

    @Override
    public ShareVO verifyAndGetShare(String shareCode, String extractCode) {
        ShareInfo share = getValidShare(shareCode);

        // 校验提取码
        if (share.getShareType() == 1) {
            if (extractCode == null || !extractCode.equals(share.getExtractCode())) {
                throw new BusinessException("提取码错误");
            }
        }

        // 增加浏览次数
        share.setViewCount(share.getViewCount() + 1);
        updateById(share);

        // 获取文件信息
        FileInfoDTO fileInfo = getFileInfo(share.getFileId());

        return buildShareVO(share, fileInfo, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelShare(String shareCode, Long userId) {
        ShareInfo share = getOne(new LambdaQueryWrapper<ShareInfo>()
                .eq(ShareInfo::getShareCode, shareCode)
                .eq(ShareInfo::getUserId, userId));

        if (share == null) {
            throw new BusinessException("分享不存在或无权限");
        }

        share.setStatus(0);
        updateById(share);

        log.info("用户{}取消分享：{}", userId, shareCode);
    }

    @Override
    public List<ShareVO> listMyShares(Long userId) {
        List<ShareInfo> shares = list(new LambdaQueryWrapper<ShareInfo>()
                .eq(ShareInfo::getUserId, userId)
                .eq(ShareInfo::getStatus, 1)
                .orderByDesc(ShareInfo::getCreateTime));

        return shares.stream().map(share -> {
            FileInfoDTO fileInfo = null;
            try {
                fileInfo = getFileInfo(share.getFileId());
            } catch (Exception e) {
                log.warn("获取文件信息失败：fileId={}", share.getFileId());
            }
            return buildShareVO(share, fileInfo, true);
        }).collect(Collectors.toList());
    }

    @Override
    public void incrementDownloadCount(String shareCode) {
        ShareInfo share = getValidShare(shareCode);
        share.setDownloadCount(share.getDownloadCount() + 1);
        updateById(share);
    }

    @Override
    public String getDownloadUrl(String shareCode) {
        // 1. 校验分享有效性
        ShareInfo share = getValidShare(shareCode);

        // 2. 调用 file 服务获取下载链接
        Result<String> result = fileFeignClient.getShareDownloadUrl(share.getFileId());
        if (result.getCode() != 200 || result.getData() == null) {
            throw new BusinessException("获取下载链接失败");
        }

        // 3. 增加下载次数
        share.setDownloadCount(share.getDownloadCount() + 1);
        updateById(share);

        log.info("分享{}被下载，fileId={}", shareCode, share.getFileId());

        return result.getData();
    }

    /**
     * 获取有效的分享记录
     */
    private ShareInfo getValidShare(String shareCode) {
        ShareInfo share = getOne(new LambdaQueryWrapper<ShareInfo>()
                .eq(ShareInfo::getShareCode, shareCode));

        if (share == null) {
            throw new BusinessException("分享不存在");
        }

        if (share.getStatus() != 1) {
            throw new BusinessException("分享已取消");
        }

        if (share.getExpireTime() != null && share.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("分享已过期");
        }
        return share;
    }

    /**
     * 获取文件信息
     */
    private FileInfoDTO getFileInfo(Long fileId) {
        Result<FileInfoDTO> result = fileFeignClient.getFileById(fileId);
        if (result.getCode() != 200 || result.getData() == null) {
            throw new BusinessException("文件不存在或已删除");
        }
        return result.getData();
    }

    /**
     * 生成唯一分享码
     */
    private String generateUniqueShareCode() {
        String code;
        int maxRetry = 10;
        int retry = 0;
        do {
            code = RandomUtil.randomString(6);
            retry++;
        } while (count(new LambdaQueryWrapper<ShareInfo>()
                .eq(ShareInfo::getShareCode, code)) > 0 && retry < maxRetry);

        if (retry >= maxRetry) {
            // 如果重试多次仍有重复，使用更长的码
            code = RandomUtil.randomString(8);
        }
        return code;
    }

    /**
     * 构建 ShareVO
     */
    private ShareVO buildShareVO(ShareInfo share, FileInfoDTO fileInfo, boolean includeExtractCode) {
        ShareVO vo = new ShareVO();
        vo.setShareCode(share.getShareCode());
        vo.setShareUrl(shareBaseUrl + share.getShareCode());
        vo.setExpireTime(share.getExpireTime());
        vo.setCreateTime(share.getCreateTime());
        vo.setViewCount(share.getViewCount());
        vo.setDownloadCount(share.getDownloadCount());

        // 私密分享创建时返回提取码
        if (includeExtractCode && share.getShareType() == 1) {
            vo.setExtractCode(share.getExtractCode());
        }

        // 文件信息
        if (fileInfo != null) {
            vo.setFileId(fileInfo.getId());
            vo.setFileName(fileInfo.getFileName());
            vo.setFileSize(fileInfo.getFileSize());
            vo.setFileType(fileInfo.getFileType());
            vo.setIsFolder(fileInfo.getIsFolder());
        }
        return vo;
    }
}
