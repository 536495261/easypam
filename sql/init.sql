-- 创建数据库
CREATE DATABASE IF NOT EXISTS easypam_user DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS easypam_file DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS easypam_storage DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS easypam_share DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS easypam_notify DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 用户表
USE easypam_user;
CREATE TABLE IF NOT EXISTS t_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    avatar VARCHAR(255),
    status TINYINT DEFAULT 1 COMMENT '0-禁用 1-正常',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 文件表
USE easypam_file;
CREATE TABLE IF NOT EXISTS t_file (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    parent_id BIGINT DEFAULT 0 COMMENT '父文件夹ID，0表示根目录',
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500),
    file_size BIGINT DEFAULT 0,
    file_type VARCHAR(50) COMMENT 'folder/image/video/audio/document/other',
    content_type VARCHAR(100),
    md5 VARCHAR(32),
    is_folder TINYINT DEFAULT 0 COMMENT '0-文件 1-文件夹',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0 COMMENT '0-正常 1-回收站',
    delete_time DATETIME COMMENT '删除时间（移入回收站时间）',
    INDEX idx_user_parent (user_id, parent_id),
    INDEX idx_md5 (md5),
    INDEX idx_deleted (user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 分片上传任务表
CREATE TABLE IF NOT EXISTS t_chunk_upload (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    parent_id BIGINT DEFAULT 0 COMMENT '目标文件夹ID',
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    file_md5 VARCHAR(32) NOT NULL,
    chunk_size INT NOT NULL COMMENT '每个分片大小(字节)',
    chunk_count INT NOT NULL COMMENT '总分片数',
    uploaded_chunks VARCHAR(2000) DEFAULT '' COMMENT '已上传分片索引,逗号分隔',
    status TINYINT DEFAULT 0 COMMENT '0-上传中 1-已完成 2-已取消',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_md5 (user_id, file_md5),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 存储空间表
USE easypam_storage;
CREATE TABLE IF NOT EXISTS t_user_storage (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    total_space BIGINT DEFAULT 10737418240 COMMENT '默认10GB',
    used_space BIGINT DEFAULT 0,
    level INT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 分享表
USE easypam_share;
CREATE TABLE IF NOT EXISTS t_share (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    share_code VARCHAR(20) NOT NULL UNIQUE,
    extract_code VARCHAR(10),
    share_type TINYINT DEFAULT 0 COMMENT '0-公开 1-私密',
    expire_time DATETIME,
    view_count INT DEFAULT 0,
    download_count INT DEFAULT 0,
    status TINYINT DEFAULT 1 COMMENT '0-已取消 1-有效',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_share_code (share_code),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 操作日志表
USE easypam_notify;
CREATE TABLE IF NOT EXISTS t_operation_log (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    operation VARCHAR(50) NOT NULL,
    target_type VARCHAR(20),
    target_id BIGINT,
    target_name VARCHAR(255),
    ip VARCHAR(50),
    user_agent VARCHAR(500),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
