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
