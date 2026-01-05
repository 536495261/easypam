package com.neu.easypam.share.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neu.easypam.common.dto.FileInfoDTO;
import com.neu.easypam.common.exception.BusinessException;
import com.neu.easypam.common.feign.FileFeignClient;
import com.neu.easypam.common.result.Result;
import com.neu.easypam.share.dto.CreateShareDTO;
import com.neu.easypam.share.dto.SaveShareDTO;
import com.neu.easypam.share.vo.PreviewVO;
import com.neu.easypam.share.vo.ShareVO;
import com.neu.easypam.share.entity.ShareInfo;
import com.neu.easypam.share.mapper.ShareMapper;
import com.neu.easypam.share.service.ShareService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareServiceImpl extends ServiceImpl<ShareMapper, ShareInfo> implements ShareService {

    private final FileFeignClient fileFeignClient;
    private final RestTemplate restTemplate;

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

    @Override
    public PreviewVO getPreviewInfo(String shareCode) {
        ShareInfo share = getValidShare(shareCode);
        FileInfoDTO fileInfo = getFileInfo(share.getFileId());
        Result<String> result = fileFeignClient.getShareDownloadUrl(share.getFileId());
        if(result.getCode() != 200 || result.getData() == null) {
            throw new BusinessException("获取预览链接失败");
        }
        PreviewVO vo = new PreviewVO();
        vo.setPreviewUrl(result.getData());
        vo.setFileName(fileInfo.getFileName());
        vo.setFileSize(fileInfo.getFileSize());
        vo.setFileType(fileInfo.getFileType());
        vo.setContentType(fileInfo.getContentType());

        String contentType = fileInfo.getContentType();
        if (contentType != null) {
            if (contentType.startsWith("image/")) {
                vo.setPreviewable(true);
                vo.setPreviewType("img");
            } else if (contentType.startsWith("video/")) {
                vo.setPreviewable(true);
                vo.setPreviewType("video");
            } else if (contentType.startsWith("audio/")) {
                vo.setPreviewable(true);
                vo.setPreviewType("audio");
            } else if (contentType.equals("application/pdf")) {
                vo.setPreviewable(true);
                vo.setPreviewType("iframe");
            } else if (contentType.startsWith("text/")) {
                vo.setPreviewable(true);
                vo.setPreviewType("text");
            } else {
                vo.setPreviewable(false);
                vo.setPreviewType("unsupported");
            }
        } else {
            vo.setPreviewable(false);
            vo.setPreviewType("unsupported");
        }
        share.setViewCount(share.getViewCount() + 1);
        updateById(share);
        return vo;
    }

    @Override
    public FileInfoDTO saveShare(Long userId, String shareCode, SaveShareDTO dto) {
        // 1. 校验分享有效性
        ShareInfo share = getValidShare(shareCode);
        
        // 2. 不能保存自己分享的文件
        if (share.getUserId().equals(userId)) {
            throw new BusinessException("不能保存自己分享的文件");
        }

        // 3. 调用 file 服务保存文件
        com.neu.easypam.common.dto.SaveShareDTO saveDto = new com.neu.easypam.common.dto.SaveShareDTO();
        saveDto.setSourceFileId(share.getFileId());
        saveDto.setTargetUserId(userId);
        saveDto.setParentId(dto.getParentId() != null ? dto.getParentId() : 0L);

        Result<FileInfoDTO> result = fileFeignClient.saveShareFile(saveDto);
        if (result.getCode() != 200 || result.getData() == null) {
            throw new BusinessException("保存失败：" + result.getMessage());
        }

        // 4. 增加保存次数（可选，复用下载次数字段或新增字段）
        share.setDownloadCount(share.getDownloadCount() + 1);
        updateById(share);

        log.info("用户{}保存分享{}到网盘", userId, shareCode);
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

    @Override
    public List<FileInfoDTO> listShareFolder(String shareCode, Long folderId) {
        ShareInfo share = getValidShare(shareCode);
        FileInfoDTO rootFile = getFileInfo(share.getFileId());

        // 如果分享的是文件，不能浏览
        if (rootFile.getIsFolder() != 1) {
            throw new BusinessException("分享的不是文件夹");
        }

        // 确定要查询的文件夹ID
        Long targetFolderId;
        if (folderId == null || folderId == 0) {
            // 查询分享的根文件夹
            targetFolderId = share.getFileId();
        } else {
            // 校验 folderId 是否在分享范围内
            if (!isFileInShareScope(folderId, share.getFileId())) {
                throw new BusinessException("无权访问该文件夹");
            }
            targetFolderId = folderId;
        }

        Result<List<FileInfoDTO>> result = fileFeignClient.listFolderChildren(targetFolderId);
        if (result.getCode() != 200) {
            throw new BusinessException("获取文件列表失败");
        }
        return result.getData();
    }

    @Override
    public void downloadShare(String shareCode, Long fileId, HttpServletResponse response) {
        ShareInfo share = getValidShare(shareCode);

        // 确定要下载的文件ID
        Long targetFileId = (fileId != null && fileId != 0) ? fileId : share.getFileId();

        // 校验文件是否在分享范围内
        if (!targetFileId.equals(share.getFileId()) && !isFileInShareScope(targetFileId, share.getFileId())) {
            throw new BusinessException("无权下载该文件");
        }

        FileInfoDTO fileInfo = getFileInfo(targetFileId);

        try {
            if (fileInfo.getIsFolder() == 1) {
                // 文件夹：通过 RestTemplate 转发流式下载
                String url = "http://easypam-file/file/internal/" + targetFileId + "/download-zip";
                restTemplate.execute(url, HttpMethod.GET, null, clientResponse -> {
                    response.setContentType("application/zip");
                    String contentDisposition = clientResponse.getHeaders().getFirst("Content-Disposition");
                    if (contentDisposition != null) {
                        response.setHeader("Content-Disposition", contentDisposition);
                    } else {
                        response.setHeader("Content-Disposition", 
                            "attachment; filename=\"" + fileInfo.getFileName() + ".zip\"");
                    }
                    StreamUtils.copy(clientResponse.getBody(), response.getOutputStream());
                    return null;
                });
            } else {
                // 文件：通过 RestTemplate 转发流式下载
                String url = "http://easypam-file/file/internal/" + targetFileId + "/download";
                restTemplate.execute(url, HttpMethod.GET, null, clientResponse -> {
                    response.setContentType(fileInfo.getContentType() != null ? 
                        fileInfo.getContentType() : "application/octet-stream");
                    String contentDisposition = clientResponse.getHeaders().getFirst("Content-Disposition");
                    if (contentDisposition != null) {
                        response.setHeader("Content-Disposition", contentDisposition);
                    } else {
                        response.setHeader("Content-Disposition", 
                            "attachment; filename=\"" + fileInfo.getFileName() + "\"");
                    }
                    StreamUtils.copy(clientResponse.getBody(), response.getOutputStream());
                    return null;
                });
            }
        } catch (Exception e) {
            log.error("下载分享文件失败", e);
            throw new BusinessException("下载失败: " + e.getMessage());
        }

        // 增加下载次数
        share.setDownloadCount(share.getDownloadCount() + 1);
        updateById(share);
    }

    /**
     * 判断文件是否在分享范围内（是分享文件夹的子文件）
     */
    private boolean isFileInShareScope(Long fileId, Long shareRootId) {
        if (fileId.equals(shareRootId)) {
            return true;
        }
        // 向上查找父文件夹
        FileInfoDTO file = getFileInfo(fileId);
        Long parentId = file.getParentId();
        while (parentId != null && parentId != 0) {
            if (parentId.equals(shareRootId)) {
                return true;
            }
            FileInfoDTO parent = getFileInfo(parentId);
            parentId = parent.getParentId();
        }
        return false;
    }
}
