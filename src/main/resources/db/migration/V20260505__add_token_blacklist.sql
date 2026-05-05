CREATE TABLE IF NOT EXISTS token_blacklist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(500) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    reason VARCHAR(100) NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_email (email),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
