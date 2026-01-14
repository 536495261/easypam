package com.neu.easypam.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neu.easypam.file.entity.FileStorage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FileStorageMapper extends BaseMapper<FileStorage> {
    
    /**
     * 原子性增加引用计数
     * 使用乐观锁防止并发问题
     */
    @Update("UPDATE t_file_storage SET ref_count = ref_count + 1, update_time = NOW() WHERE id = #{id}")
    int incrementRefCount(@Param("id") Long id);
    
    /**
     * 原子性减少引用计数
     * 返回更新后的行数，用于判断是否成功
     */
    @Update("UPDATE t_file_storage SET ref_count = ref_count - 1, update_time = NOW() WHERE id = #{id} AND ref_count > 0")
    int decrementRefCount(@Param("id") Long id);
}
