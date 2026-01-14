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

-- 文件存储表（内容寻址，去重存储）
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

-- 文件表（用户文件元数据，引用存储表）
CREATE TABLE IF NOT EXISTS t_file (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    parent_id BIGINT DEFAULT 0 COMMENT '父文件夹ID，0表示根目录',
    file_name VARCHAR(255) NOT NULL,
    storage_id BIGINT COMMENT '关联存储表ID（文件夹为空）',
    file_path VARCHAR(500) COMMENT '冗余存储路径，便于查询',
    file_size BIGINT DEFAULT 0,
    file_type VARCHAR(50) COMMENT 'folder/image/video/audio/document/other',
    content_type VARCHAR(100),
    md5 VARCHAR(32),
    is_folder TINYINT DEFAULT 0 COMMENT '0-文件 1-文件夹',
    thumbnail_path VARCHAR(500) COMMENT '缩略图存储路径',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0 COMMENT '0-正常 1-回收站',
    delete_time DATETIME COMMENT '删除时间（移入回收站时间）',
    -- 核心索引：文件列表查询 WHERE user_id=? AND parent_id=? AND deleted=0 ORDER BY is_folder DESC, create_time DESC
    INDEX idx_user_parent_deleted (user_id, parent_id, deleted),
    -- 秒传检测：WHERE md5=? AND deleted=0
    INDEX idx_md5 (md5),
    -- 回收站清理定时任务：WHERE deleted=1 AND delete_time < ?
    INDEX idx_delete_time (deleted, delete_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 本地消息表（用于消息发送失败补偿）
CREATE TABLE IF NOT EXISTS t_local_message (
    id BIGINT PRIMARY KEY,
    message_id VARCHAR(36) NOT NULL UNIQUE COMMENT '消息唯一ID',
    topic VARCHAR(100) NOT NULL COMMENT '消息主题',
    message_body TEXT NOT NULL COMMENT '消息内容JSON',
    hash_key VARCHAR(100) COMMENT '顺序消息的hashKey',
    status TINYINT DEFAULT 0 COMMENT '0-待发送 1-发送成功 2-发送失败',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    max_retry INT DEFAULT 5 COMMENT '最大重试次数',
    next_retry_time DATETIME COMMENT '下次重试时间',
    error_msg VARCHAR(500) COMMENT '错误信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status_retry (status, next_retry_time),
    INDEX idx_message_id (message_id)
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

-- 文件版本表
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

-- 存储空间表
USE easypam_storage;

-- 本地消息表（用于消息发送失败补偿）
CREATE TABLE IF NOT EXISTS t_local_message (
    id BIGINT PRIMARY KEY,
    message_id VARCHAR(36) NOT NULL UNIQUE COMMENT '消息唯一ID',
    topic VARCHAR(100) NOT NULL COMMENT '消息主题',
    message_body TEXT NOT NULL COMMENT '消息内容JSON',
    hash_key VARCHAR(100) COMMENT '顺序消息的hashKey',
    status TINYINT DEFAULT 0 COMMENT '0-待发送 1-发送成功 2-发送失败',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    max_retry INT DEFAULT 5 COMMENT '最大重试次数',
    next_retry_time DATETIME COMMENT '下次重试时间',
    error_msg VARCHAR(500) COMMENT '错误信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status_retry (status, next_retry_time),
    INDEX idx_message_id (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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

-- 本地消息表（用于消息发送失败补偿）
CREATE TABLE IF NOT EXISTS t_local_message (
    id BIGINT PRIMARY KEY,
    message_id VARCHAR(36) NOT NULL UNIQUE COMMENT '消息唯一ID',
    topic VARCHAR(100) NOT NULL COMMENT '消息主题',
    message_body TEXT NOT NULL COMMENT '消息内容JSON',
    hash_key VARCHAR(100) COMMENT '顺序消息的hashKey',
    status TINYINT DEFAULT 0 COMMENT '0-待发送 1-发送成功 2-发送失败',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    max_retry INT DEFAULT 5 COMMENT '最大重试次数',
    next_retry_time DATETIME COMMENT '下次重试时间',
    error_msg VARCHAR(500) COMMENT '错误信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status_retry (status, next_retry_time),
    INDEX idx_message_id (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    -- 分享链接访问：WHERE share_code=? (UNIQUE已自动创建索引)
    -- 我的分享列表：WHERE user_id=? AND status=1 ORDER BY create_time DESC
    INDEX idx_user_status (user_id, status),
    -- 过期分享清理：WHERE status=1 AND expire_time < NOW()
    INDEX idx_expire (status, expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 操作日志表
USE easypam_notify;
CREATE TABLE IF NOT EXISTS t_operation_log (
    id BIGINT PRIMARY KEY,
    log_id VARCHAR(36) UNIQUE COMMENT '日志唯一ID，用于幂等去重',
    user_id BIGINT NOT NULL,
    operation VARCHAR(50) NOT NULL,
    target_type VARCHAR(20),
    target_id BIGINT,
    target_name VARCHAR(255),
    ip VARCHAR(50),
    user_agent VARCHAR(500),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    -- 用户操作日志查询：WHERE user_id=? ORDER BY create_time DESC
    INDEX idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 站内通知表
CREATE TABLE IF NOT EXISTS t_notification (
    id BIGINT PRIMARY KEY,
    message_id VARCHAR(36) UNIQUE COMMENT '消息唯一ID，用于幂等去重',
    user_id BIGINT NOT NULL COMMENT '接收用户',
    type VARCHAR(30) NOT NULL COMMENT '通知类型',
    title VARCHAR(100) NOT NULL,
    content VARCHAR(500),
    biz_id BIGINT COMMENT '关联业务ID',
    from_user_id BIGINT COMMENT '发送者',
    is_read TINYINT DEFAULT 0 COMMENT '0-未读 1-已读',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_read (user_id, is_read),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
