-- ============================================
-- EasyPam 增量更新脚本 v2
-- 新增功能：文件去重存储、版本控制、二级缓存
-- ============================================

-- 1. 文件存储表（内容寻址，去重存储）
USE easypam_file;

CREATE TABLE IF NOT EXISTS t_file_storage (
    id BIGINT PRIMARY KEY,
    md5 VARCHAR(32) NOT NULL UNIQUE COMMENT '文件内容MD5，内容寻址的key',
    storage_path VARCHAR(500) NOT NULL COMMENT 'MinIO存储路径',
    file_size BIGINT NOT NULL,
    content_type VARCHAR(100),
    ref_count INT DEFAULT 1 COMMENT '引用计数，为0时可删除实际文件',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_md5 (md5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 文件版本表
CREATE TABLE IF NOT EXISTS t_file_version (
    id BIGINT PRIMARY KEY,
    file_id BIGINT NOT NULL COMMENT '关联的文件ID',
    version_num INT NOT NULL COMMENT '版本号',
    storage_id BIGINT COMMENT '存储ID',
    file_size BIGINT,
    md5 VARCHAR(32),
    remark VARCHAR(255) COMMENT '版本备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_file_version (file_id, version_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. t_file 表新增字段（如果不存在）
-- 添加 storage_id 字段（关联去重存储）
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS 
               WHERE TABLE_SCHEMA='easypam_file' AND TABLE_NAME='t_file' AND COLUMN_NAME='storage_id');
SET @sql := IF(@exist = 0, 
    'ALTER TABLE t_file ADD COLUMN storage_id BIGINT COMMENT ''关联存储表ID（文件夹为空）'' AFTER file_name',
    'SELECT ''storage_id already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 deleted 字段（回收站）
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS 
               WHERE TABLE_SCHEMA='easypam_file' AND TABLE_NAME='t_file' AND COLUMN_NAME='deleted');
SET @sql := IF(@exist = 0, 
    'ALTER TABLE t_file ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT ''0-正常 1-回收站''',
    'SELECT ''deleted already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 delete_time 字段（删除时间）
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS 
               WHERE TABLE_SCHEMA='easypam_file' AND TABLE_NAME='t_file' AND COLUMN_NAME='delete_time');
SET @sql := IF(@exist = 0, 
    'ALTER TABLE t_file ADD COLUMN delete_time DATETIME COMMENT ''删除时间（移入回收站时间）''',
    'SELECT ''delete_time already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 thumbnail_path 字段（缩略图）
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS 
               WHERE TABLE_SCHEMA='easypam_file' AND TABLE_NAME='t_file' AND COLUMN_NAME='thumbnail_path');
SET @sql := IF(@exist = 0, 
    'ALTER TABLE t_file ADD COLUMN thumbnail_path VARCHAR(500) COMMENT ''缩略图存储路径''',
    'SELECT ''thumbnail_path already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. 添加索引（如果不存在）
-- 回收站清理索引
SET @exist := (SELECT COUNT(*) FROM information_schema.STATISTICS 
               WHERE TABLE_SCHEMA='easypam_file' AND TABLE_NAME='t_file' AND INDEX_NAME='idx_delete_time');
SET @sql := IF(@exist = 0, 
    'ALTER TABLE t_file ADD INDEX idx_delete_time (deleted, delete_time)',
    'SELECT ''idx_delete_time already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- 执行完成提示
-- ============================================
SELECT '升级完成！新增表：t_file_storage, t_file_version；t_file 表新增字段：storage_id, deleted, delete_time, thumbnail_path' AS result;
