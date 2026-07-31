-- V7：对话系统 —— 多轮对话支持（MySQL 版）

CREATE TABLE conversations (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    user_id     BIGINT,
    created_at  DATETIME(6),
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE messages (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id  BIGINT NOT NULL,
    role             VARCHAR(20) NOT NULL,
    content          MEDIUMTEXT NOT NULL,
    created_at       DATETIME(6),
    FOREIGN KEY (conversation_id) REFERENCES conversations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
