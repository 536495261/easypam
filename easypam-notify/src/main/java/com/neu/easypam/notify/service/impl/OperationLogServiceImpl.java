package com.neu.easypam.notify.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neu.easypam.notify.entity.OperationLog;
import com.neu.easypam.notify.mapper.OperationLogMapper;
import com.neu.easypam.notify.service.OperationLogService;
import org.springframework.stereotype.Service;

@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> 
        implements OperationLogService {

    @Override
    public IPage<OperationLog> listByUserId(Long userId, int page, int size) {
        return page(new Page<>(page, size), new LambdaQueryWrapper<OperationLog>()
                .eq(OperationLog::getUserId, userId)
                .orderByDesc(OperationLog::getCreateTime));
    }
}
