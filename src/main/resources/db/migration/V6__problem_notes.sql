-- 문제별 개인 메모. 유저가 문제를 풀면서 적어두는 것이라 계정에 저장한다
-- (기기에 매인 코드 draft는 그대로 localStorage에 남는다).
-- (user_id, problem_id) 당 한 행이고, 내용이 비면 행을 지운다 — "메모 없음"이
-- 진짜 없음이 되도록.

CREATE TABLE problem_notes
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    problem_id BIGINT      NOT NULL,
    content    TEXT        NOT NULL,
    -- 공개하면 "그 문제를 이미 푼 사람"에게만 보인다(제출 상세의 기존 열람 조건).
    -- 안 푼 사람에게는 애초에 닿지 않으므로 스포일러 정책과 충돌하지 않는다.
    is_public  BIT         NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_problem_notes_user_problem UNIQUE (user_id, problem_id),
    -- 메모는 문제/계정에 딸린 부속물이라 원본이 사라지면 같이 지운다.
    -- CASCADE가 없으면 메모를 적어둔 사람이 있다는 이유만으로 문제 삭제가 막힌다
    -- (문제의 서브태스크·테스트케이스는 JPA cascade로 지워지지만 메모는 Problem의
    --  자식 컬렉션이 아니다).
    CONSTRAINT fk_problem_notes_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_problem_notes_problem
        FOREIGN KEY (problem_id) REFERENCES problems (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- 정답 처리된 제출에 그 시점의 메모를 복사해 남긴다. 메모는 계속 고쳐지지만
-- 제출 로그는 "그때 무슨 생각으로 풀었나"의 기록이라 스냅샷이어야 한다.
-- 오답/부분점수 제출에는 남기지 않는다(NULL).
ALTER TABLE submissions
    ADD COLUMN note_snapshot TEXT NULL;

-- 공개 여부도 정답 시점의 선택을 함께 찍는다. 살아있는 메모의 공개 설정을 그때그때
-- 참조하면, 나중에 메모를 공개로 바꾸는 순간 예전 제출에 붙은 (지금은 다른 내용인)
-- 사본들까지 소급 공개된다.
ALTER TABLE submissions
    ADD COLUMN note_snapshot_public BIT NOT NULL DEFAULT 0;
