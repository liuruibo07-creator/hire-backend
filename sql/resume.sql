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
