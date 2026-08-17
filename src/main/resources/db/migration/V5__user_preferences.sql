-- 계정에 붙는 개인 보기 설정. 유저당 한 행이고 PK는 users.id를 그대로 쓴다.
-- 행이 없으면 전부 기본값이므로 기존 유저용 백필은 없다 — 처음 설정을 바꿀 때 생긴다.
-- 설정이 늘어나면 이 테이블에 컬럼을 추가하는 마이그레이션을 새로 만든다.

CREATE TABLE user_preferences
(
    user_id    BIGINT      NOT NULL,
    show_tags  BIT         NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_preferences_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;
