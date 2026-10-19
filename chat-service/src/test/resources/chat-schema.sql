CREATE TABLE IF NOT EXISTS t_chat_conversation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    job_id BIGINT NOT NULL,
    seeker_id BIGINT NOT NULL,
    employer_id BIGINT NOT NULL,
    job_title VARCHAR(200) NOT NULL,
    last_message_id BIGINT NULL,
    seeker_read_id BIGINT NOT NULL DEFAULT 0,
    employer_read_id BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    UNIQUE KEY uk_chat_participants (job_id, seeker_id, employer_id)
);

CREATE TABLE IF NOT EXISTS t_chat_message (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    create_time DATETIME NOT NULL,
    INDEX idx_chat_history (conversation_id, id)
);
