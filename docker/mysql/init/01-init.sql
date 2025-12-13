-- GitLab Mirror 数据库初始化脚本

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS gitlab_mirror
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE gitlab_mirror;

-- 授权
GRANT ALL PRIVILEGES ON gitlab_mirror.* TO 'gitlab_mirror'@'%';
FLUSH PRIVILEGES;

-- 提示信息
SELECT 'GitLab Mirror 数据库初始化完成' AS message;
