-- Fresh Docker volumes only. Existing volumes must apply db/migrations explicitly.
USE cloud_application;
-- Run in cloud_application before deploying application-service. MySQL 8 / InnoDB / Seata AT.
CREATE TABLE IF NOT EXISTS t_application_outbox (
    message_id VARCHAR(64) NOT NULL PRIMARY KEY,
    routing_key VARCHAR(128) NOT NULL,
    payload LONGTEXT NOT NULL,
    global_xid VARCHAR(128) NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at DATETIME NULL,
    last_error VARCHAR(128) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_outbox_due (sent_at, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

USE cloud_notification;
-- Run in cloud_notification before deploying notification-service.
CREATE TABLE IF NOT EXISTS t_notification_event (
    message_id VARCHAR(64) NOT NULL PRIMARY KEY,
    processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
