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
