package com.neu.easypam.notify.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.neu.easypam.notify.entity.OperationLog;

public interface OperationLogService extends IService<OperationLog> {

    /**
     * 分页查询用户操作日志
     */
    IPage<OperationLog> listByUserId(Long userId, int page, int size);
}
