-- Run in cloud_notification before deploying notification-service.
CREATE TABLE IF NOT EXISTS t_notification_event (
    message_id VARCHAR(64) NOT NULL PRIMARY KEY,
    processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
