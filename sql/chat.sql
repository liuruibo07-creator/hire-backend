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
