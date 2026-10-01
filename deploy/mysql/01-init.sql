-- Fresh-install initialization; executed only when the MySQL volume is empty.
CREATE DATABASE IF NOT EXISTS cloud_user CHARACTER SET utf8mb4;
USE cloud_user;
-- Fresh installations only. No seeded login credentials.
CREATE TABLE IF NOT EXISTS t_user (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(50) NOT NULL UNIQUE,
 password VARCHAR(200) NOT NULL, real_name VARCHAR(50), email VARCHAR(100),
 phone VARCHAR(20), avatar VARCHAR(500), role VARCHAR(20) DEFAULT 'seeker',
 status TINYINT NOT NULL DEFAULT 1, deleted TINYINT NOT NULL DEFAULT 0,
 last_login_time DATETIME, create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE DATABASE IF NOT EXISTS cloud_job CHARACTER SET utf8mb4;
USE cloud_job;
-- Fresh-install schema derived from JobMapper.xml and JobCategoryMapper.xml.
CREATE TABLE IF NOT EXISTS t_job_category (
 id BIGINT PRIMARY KEY, parent_id BIGINT NOT NULL DEFAULT 0, name VARCHAR(100) NOT NULL,
 sort_order INT DEFAULT 0, level INT DEFAULT 1, status TINYINT DEFAULT 1,
 create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO t_job_category (id, parent_id, name, level) VALUES (1, 0, '互联网与技术', 1);
CREATE TABLE IF NOT EXISTS t_job (
 id BIGINT PRIMARY KEY, employer_id BIGINT NOT NULL, title VARCHAR(200) NOT NULL,
 category_id BIGINT, description TEXT, job_type VARCHAR(50), industry VARCHAR(100),
 city VARCHAR(100), address VARCHAR(500), experience_req VARCHAR(100), education_req VARCHAR(100),
 salary_min INT, salary_max INT, salary_months INT DEFAULT 12, skills TEXT, headcount INT DEFAULT 1,
 status TINYINT DEFAULT 3, view_count INT DEFAULT 0, apply_count INT DEFAULT 0,
 review_remark VARCHAR(1000), reviewed_by BIGINT, reviewed_at DATETIME,
 create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 deleted TINYINT DEFAULT 0, KEY idx_employer (employer_id), KEY idx_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seata AT 模式要求：每个参与全局事务的业务数据库都必须有 undo_log 表。
-- 本项目涉及两个库：cloud_application（事务发起方）和 cloud_job（分支事务方），
-- 请分别在两个库中执行以下建表语句。

-- for AT mode you need to use this DDL in each business database
CREATE TABLE IF NOT EXISTS `undo_log` (
  `branch_id`     BIGINT       NOT NULL COMMENT 'branch transaction id',
  `xid`           VARCHAR(128) NOT NULL COMMENT 'global transaction id',
  `context`       VARCHAR(128) NOT NULL COMMENT 'undo_log context, such as serialization',
  `rollback_info` LONGBLOB     NOT NULL COMMENT 'rollback info',
  `log_status`    INT(11)      NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created`   DATETIME(6)  NOT NULL COMMENT 'create datetime',
  `log_modified`  DATETIME(6)  NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`)
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COMMENT ='AT transaction mode undo table';

CREATE DATABASE IF NOT EXISTS cloud_application CHARACTER SET utf8mb4;
USE cloud_application;
-- 投递记录表（application-service 使用 cloud_application 库）
CREATE TABLE IF NOT EXISTS t_applications (
    id              BIGINT        PRIMARY KEY AUTO_INCREMENT COMMENT '投递ID',
    user_id         BIGINT        NOT NULL COMMENT '投递人用户ID',
    job_id          BIGINT        NOT NULL COMMENT '职位ID（逻辑外键，指向 job-service 的 t_job.id）',
    resume_id       BIGINT        NOT NULL COMMENT '使用的简历ID',
    employer_id     BIGINT        COMMENT '职位发布者用户ID，冗余字段，避免每条投递都远程查询 job-service',
    cover_letter    VARCHAR(1000) COMMENT '求职附言',
    status          TINYINT       DEFAULT 0 COMMENT '投递状态：0 待处理，1 已查看，2 面试邀请，3 已录用，4 已拒绝',
    remark          VARCHAR(500)  COMMENT '企业备注（更新状态时选填）',
    deleted         TINYINT       DEFAULT 0 COMMENT '是否删除：0 未删除，1 已删除',
    create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '投递时间',
    update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_job_id (job_id),
    INDEX idx_employer_id (employer_id),
    -- 防重复投递：同一用户对同一职位只能有一条记录，唯一索引是最后一道防线
    UNIQUE KEY uk_user_job (user_id, job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投递记录表';

-- 简历表（application-service 使用 cloud_application 库）
CREATE TABLE IF NOT EXISTS t_resume (
    id                 BIGINT        PRIMARY KEY AUTO_INCREMENT COMMENT '简历ID',
    user_id            BIGINT        NOT NULL COMMENT '所属用户ID',
    title              VARCHAR(100)  NOT NULL COMMENT '简历标题',
    name               VARCHAR(50)   COMMENT '姓名',
    gender             TINYINT       COMMENT '性别：0 女，1 男',
    birth_year         INT           COMMENT '出生年份',
    phone              VARCHAR(20)   COMMENT '手机号',
    email              VARCHAR(100)  COMMENT '邮箱',
    education          VARCHAR(50)   COMMENT '学历',
    university         VARCHAR(100)  COMMENT '毕业院校',
    major              VARCHAR(100)  COMMENT '专业',
    graduation_date    VARCHAR(20)   COMMENT '毕业日期',
    work_experience    TEXT          COMMENT '工作经历',
    skills             TEXT          COMMENT '技能',
    expected_position  VARCHAR(100)  COMMENT '期望职位',
    expected_salary    VARCHAR(50)   COMMENT '期望薪资',
    expected_city      VARCHAR(50)   COMMENT '期望城市',
    self_evaluation    TEXT          COMMENT '自我评价',
    is_default         TINYINT       DEFAULT 0 COMMENT '是否默认：0 否，1 是',
    deleted            TINYINT       DEFAULT 0 COMMENT '是否删除：0 未删除，1 已删除',
    INDEX idx_user_id  (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历表';

-- Seata AT 模式要求：每个参与全局事务的业务数据库都必须有 undo_log 表。
-- 本项目涉及两个库：cloud_application（事务发起方）和 cloud_job（分支事务方），
-- 请分别在两个库中执行以下建表语句。

-- for AT mode you need to use this DDL in each business database
CREATE TABLE IF NOT EXISTS `undo_log` (
  `branch_id`     BIGINT       NOT NULL COMMENT 'branch transaction id',
  `xid`           VARCHAR(128) NOT NULL COMMENT 'global transaction id',
  `context`       VARCHAR(128) NOT NULL COMMENT 'undo_log context, such as serialization',
  `rollback_info` LONGBLOB     NOT NULL COMMENT 'rollback info',
  `log_status`    INT(11)      NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created`   DATETIME(6)  NOT NULL COMMENT 'create datetime',
  `log_modified`  DATETIME(6)  NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`)
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COMMENT ='AT transaction mode undo table';

CREATE DATABASE IF NOT EXISTS cloud_notification CHARACTER SET utf8mb4;
USE cloud_notification;
-- 通知表（notification-service 使用 cloud_notification 库）
-- 雪花算法生成ID，RabbitMQ 消费后落库

CREATE TABLE IF NOT EXISTS t_notification (
  id          BIGINT       NOT NULL COMMENT '通知ID，雪花算法生成',
  user_id     BIGINT       NOT NULL COMMENT '接收用户ID',
  title       VARCHAR(200) NOT NULL COMMENT '通知标题',
  content     TEXT         NULL     COMMENT '通知内容',
  type        VARCHAR(30)  NOT NULL COMMENT '通知类型',
  related_id  BIGINT       NULL     COMMENT '关联业务ID（如投递ID、职位ID）',
  is_read     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读 / 1-已读',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_user_id (user_id),
  KEY idx_type (type),
  KEY idx_create_time (create_time),
  KEY idx_is_read (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE DATABASE IF NOT EXISTS cloud_chat CHARACTER SET utf8mb4;
USE cloud_chat;
-- Run in the new cloud_chat database. Existing business databases are not modified.
CREATE TABLE IF NOT EXISTS t_chat_conversation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    seeker_id BIGINT NOT NULL,
    employer_id BIGINT NOT NULL,
    job_title VARCHAR(200) NOT NULL,
    last_message_id BIGINT NULL,
    seeker_read_id BIGINT NOT NULL DEFAULT 0,
    employer_read_id BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL,
    update_time DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_participants_job (job_id, seeker_id, employer_id),
    KEY idx_chat_seeker (seeker_id, update_time, id),
    KEY idx_chat_employer (employer_id, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_chat_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    create_time DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_chat_history (conversation_id, id),
    KEY idx_chat_unread (conversation_id, sender_id, id),
    CONSTRAINT fk_chat_message_conversation FOREIGN KEY (conversation_id) REFERENCES t_chat_conversation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

