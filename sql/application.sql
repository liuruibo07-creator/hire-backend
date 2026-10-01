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
