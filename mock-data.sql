-- ========================================
-- Mock Data Generation Script
-- Novel Insight MVP - Test Data
-- ========================================
--
-- 생성 데이터:
-- - 회원 1,050명 (작가 50명 + 독자 1,000명)
-- - 소설 150편
-- - 에피소드 ~5,000개
-- - 리뷰 + 감성 분석 50,000개
-- - 댓글 100,000개
-- - 읽기 로그 500,000개 (최근 30일)
--
-- 실행 방법:
-- mysql -u novel_user -p novel_insight < mock-data.sql
--
-- 주의: 기존 데이터가 있으면 먼저 삭제하고 실행
-- ========================================

USE novel_insight;

-- 기존 데이터 삭제 (FK 제약조건 역순)
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE episode_read_log;
TRUNCATE TABLE drop_off_point;
TRUNCATE TABLE author_dashboard_cache;
TRUNCATE TABLE review_analysis;
TRUNCATE TABLE review;
TRUNCATE TABLE episode_comment;
TRUNCATE TABLE episode_purchase;
TRUNCATE TABLE episode;
TRUNCATE TABLE novel;
TRUNCATE TABLE point_history;
TRUNCATE TABLE point;
TRUNCATE TABLE member;
SET FOREIGN_KEY_CHECKS = 1;

-- ========================================
-- 1. Member (회원 데이터)
-- ========================================
-- 작가 50명 + 독자 1,000명 = 1,050명

-- 작가 50명 생성 (role = 'AUTHOR')
INSERT INTO member (email, password, nickname, role, created_at) VALUES
('author1@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '김민서 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author2@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '이지수 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author3@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '박현우 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author4@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '최서연 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author5@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '정도윤 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author6@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '강시아 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author7@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '조은혁 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author8@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '윤영선 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author9@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '장재미 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author10@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '임준경 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author11@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '한하민 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author12@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '오서지 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author13@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '서수우 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author14@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '신윤준 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author15@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '권도연 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author16@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '황시재 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author17@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '안아혁 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author18@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '송현영 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author19@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '류은선 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author20@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '홍지미 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author21@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '김서경 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author22@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '이윤민 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author23@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '박도서 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author24@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '최지하 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author25@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '정수은 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author26@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '강현준 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author27@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '조우윤 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author28@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '윤시도 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author29@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '장아연 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author30@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '임재영 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author31@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '한혁선 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author32@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '오영미 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author33@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '서선경 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author34@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '신민우 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author35@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '권서준 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author36@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '황지윤 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author37@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '안도연 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author38@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '송시재 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author39@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '류아혁 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author40@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '홍현영 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author41@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '김은선 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author42@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '이지미 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author43@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '박서경 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author44@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '최윤민 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author45@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '정도서 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author46@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '강지하 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author47@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '조수은 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author48@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '윤현준 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author49@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '장우윤 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)),
('author50@novel.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '임시도 작가', 'AUTHOR', DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY));

-- 독자 1,000명은 프로시저로 생성 (MySQL 8.0+, role = 'READER')
DELIMITER $$
CREATE PROCEDURE generate_readers()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE surname VARCHAR(10);
    DECLARE name1 VARCHAR(10);
    DECLARE name2 VARCHAR(10);

    WHILE i <= 1000 DO
        SET surname = ELT(FLOOR(1 + RAND() * 20), '김','이','박','최','정','강','조','윤','장','임','한','오','서','신','권','황','안','송','류','홍');
        SET name1 = ELT(FLOOR(1 + RAND() * 20), '민','서','지','수','하','은','현','우','준','윤','도','시','아','연','재','혁','영','선','미','경');
        SET name2 = ELT(FLOOR(1 + RAND() * 20), '민','서','지','수','하','은','현','우','준','윤','도','시','아','연','재','혁','영','선','미','경');

        INSERT INTO member (email, password, nickname, role, created_at)
        VALUES (
            CONCAT('reader', i, '@novel.com'),
            '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
            CONCAT(surname, name1, name2),
            'READER',
            DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)
        );

        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL generate_readers();
DROP PROCEDURE generate_readers;

-- ========================================
-- 2. Novel (소설 데이터)
-- ========================================
-- 150편 소설 생성 (genre, status 추가)

DELIMITER $$
CREATE PROCEDURE generate_novels()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE author_id BIGINT;
    DECLARE novel_title VARCHAR(255);
    DECLARE genre_val VARCHAR(50);
    DECLARE status_val VARCHAR(20);

    WHILE i <= 150 DO
        -- 랜덤 작가 선택 (ID 1-50)
        SET author_id = FLOOR(1 + RAND() * 50);

        -- 랜덤 제목 생성
        SET novel_title = CASE FLOOR(1 + RAND() * 4)
            WHEN 1 THEN CONCAT(ELT(FLOOR(1 + RAND() * 8), '찬란한','어두운','빛나는','숨겨진','금지된','전설의','불멸의','잊혀진'), ' ', ELT(FLOOR(1 + RAND() * 12), '검성','마법사','황제','기사','암살자','영주','현자','용사','악당','공작','마왕','성자'))
            WHEN 2 THEN CONCAT('환생한 ', ELT(FLOOR(1 + RAND() * 12), '검성','마법사','황제','기사','암살자','영주','현자','용사','악당','공작','마왕','성자'))
            WHEN 3 THEN CONCAT('회귀한 ', ELT(FLOOR(1 + RAND() * 12), '검성','마법사','황제','기사','암살자','영주','현자','용사','악당','공작','마왕','성자'))
            ELSE CONCAT(ELT(FLOOR(1 + RAND() * 12), '검성','마법사','황제','기사','암살자','영주','현자','용사','악당','공작','마왕','성자'), '의 귀환')
        END;

        -- 랜덤 장르 (판타지 50%, 로맨스 20%, 무협 15%, 현대물 10%, SF 5%)
        SET genre_val = CASE FLOOR(1 + RAND() * 20)
            WHEN 1,2,3,4,5,6,7,8,9,10 THEN '판타지'
            WHEN 11,12,13,14 THEN '로맨스'
            WHEN 15,16,17 THEN '무협'
            WHEN 18,19 THEN '현대물'
            ELSE 'SF'
        END;

        -- 연재 상태 (연재중 70%, 완결 25%, 휴재 5%)
        SET status_val = CASE
            WHEN RAND() < 0.70 THEN 'ONGOING'
            WHEN RAND() < 0.95 THEN 'COMPLETED'
            ELSE 'HIATUS'
        END;

        INSERT INTO novel (title, author, author_id, description, genre, status, view_count, created_at)
        SELECT
            novel_title,
            nickname,
            author_id,
            '흥미진진한 스토리가 펼쳐집니다. 주인공의 성장과 모험을 함께하세요!',
            genre_val,
            status_val,
            FLOOR(RAND() * 100000),
            DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)
        FROM member WHERE id = author_id;

        SET i = i + 1;
    END WHILE;
END$$
DELIMITER ;

CALL generate_novels();
DROP PROCEDURE generate_novels();

-- ========================================
-- 3. Episode (에피소드 데이터)
-- ========================================
-- 소설당 20-50개 에피소드 생성 (총 ~5,000개)
-- episode_number, published_at, created_at 추가

DELIMITER $$
CREATE PROCEDURE generate_episodes()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE novel_id_var BIGINT;
    DECLARE episode_count INT;
    DECLARE i INT;
    DECLARE episode_title VARCHAR(255);
    DECLARE base_date DATETIME;

    DECLARE novel_cursor CURSOR FOR SELECT id FROM novel;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN novel_cursor;

    read_loop: LOOP
        FETCH novel_cursor INTO novel_id_var;
        IF done THEN
            LEAVE read_loop;
        END IF;

        -- 인기 소설(20%)은 40-80개, 일반 소설은 10-30개
        IF RAND() < 0.2 THEN
            SET episode_count = FLOOR(40 + RAND() * 40);
        ELSE
            SET episode_count = FLOOR(10 + RAND() * 20);
        END IF;

        -- 소설별 기준 날짜 (최근 1년 이내)
        SET base_date = DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY);

        SET i = 1;
        WHILE i <= episode_count DO
            SET episode_title = CASE FLOOR(1 + RAND() * 12)
                WHEN 1 THEN '프롤로그'
                WHEN 2 THEN '운명적인 만남'
                WHEN 3 THEN '첫 번째 시련'
                WHEN 4 THEN '숨겨진 진실'
                WHEN 5 THEN '각성'
                WHEN 6 THEN '반전의 시작'
                WHEN 7 THEN '위기의 순간'
                WHEN 8 THEN '새로운 힘'
                WHEN 9 THEN '결전의 서막'
                WHEN 10 THEN '최후의 결투'
                WHEN 11 THEN '숨겨진 비밀'
                ELSE '새로운 여정'
            END;

            -- 에피소드는 순차적으로 발행 (1일 ~ 7일 간격)
            INSERT INTO episode (title, content, episode_number, price, is_free, view_count, purchase_count, published_at, created_at, novel_id)
            VALUES (
                CONCAT(i, '화 - ', episode_title),
                REPEAT('에피소드 내용입니다. ', FLOOR(50 + RAND() * 150)),
                i, -- 회차 번호 (1, 2, 3...)
                IF(i <= 3, 0, FLOOR(100 + RAND() * 400)), -- 처음 3화는 무료
                IF(i <= 3, TRUE, FALSE),
                FLOOR(RAND() * 50000),
                IF(i > 3, FLOOR(RAND() * 10000), 0),
                DATE_ADD(base_date, INTERVAL (i - 1) * FLOOR(1 + RAND() * 7) DAY), -- published_at
                DATE_ADD(base_date, INTERVAL (i - 1) * FLOOR(1 + RAND() * 7) DAY), -- created_at
                novel_id_var
            );

            SET i = i + 1;
        END WHILE;
    END LOOP;

    CLOSE novel_cursor;
END$$
DELIMITER ;

CALL generate_episodes();
DROP PROCEDURE generate_episodes;

-- ========================================
-- 4. Review (리뷰 데이터)
-- ========================================
-- 50,000개 리뷰 생성
-- user_id, rating, created_at 추가

DELIMITER $$
CREATE PROCEDURE generate_reviews()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE novel_id_var BIGINT;
    DECLARE user_id_var BIGINT;
    DECLARE review_content TEXT;
    DECLARE rating_val DECIMAL(3,1);
    DECLARE sentiment_val VARCHAR(20);
    DECLARE score_val DOUBLE;

    WHILE i <= 50000 DO
        -- 랜덤 소설 선택
        SELECT id INTO novel_id_var FROM novel ORDER BY RAND() LIMIT 1;

        -- 랜덤 독자 선택 (ID 51-1050)
        SET user_id_var = FLOOR(51 + RAND() * 1000);

        -- 감성 분포: 긍정 60%, 부정 20%, 중립 20%
        IF RAND() < 0.6 THEN
            SET sentiment_val = 'POSITIVE';
            SET score_val = 70 + RAND() * 30;
            SET rating_val = 4.0 + RAND() * 1.0; -- 4.0 ~ 5.0
            SET review_content = ELT(FLOOR(1 + RAND() * 8),
                '정말 재미있어요! 다음 화가 너무 기대됩니다.',
                '스토리 전개가 완벽합니다. 강추!',
                '캐릭터가 매력적이고 몰입도가 높아요.',
                '이런 소설을 기다렸어요. 대박!',
                '전개가 빠르고 지루할 틈이 없네요.',
                '작가님 필력이 대단하십니다.',
                '킹갓작가님 감사합니다! 핵꿀잼!',
                '이 정도 퀄리티면 유료로 봐도 아깝지 않아요.'
            );
        ELSEIF RAND() < 0.8 THEN
            SET sentiment_val = 'NEGATIVE';
            SET score_val = RAND() * 30;
            SET rating_val = 1.0 + RAND() * 2.0; -- 1.0 ~ 3.0
            SET review_content = ELT(FLOOR(1 + RAND() * 8),
                '전개가 너무 느려요. 지루합니다.',
                '주인공이 공감이 안 가네요.',
                '스토리가 너무 뻔해요.',
                '설정 구멍이 많아서 몰입이 안 됩니다.',
                '캐릭터가 너무 평면적이에요.',
                '작가님 좀 더 신경 써주세요.',
                '앞뒤가 안 맞는 전개가 너무 많아요.',
                '기대했는데 실망이네요.'
            );
        ELSE
            SET sentiment_val = 'NEUTRAL';
            SET score_val = 30 + RAND() * 40;
            SET rating_val = 2.5 + RAND() * 2.0; -- 2.5 ~ 4.5
            SET review_content = ELT(FLOOR(1 + RAND() * 8),
                '나쁘지 않은데 특별한 건 없네요.',
                '그냥 시간 때우기 좋은 소설입니다.',
                '평범한 스토리예요.',
                '무난하게 읽을 만합니다.',
                '기대치를 낮추면 볼 만해요.',
                '호불호가 갈릴 것 같아요.',
                '아직 초반이라 판단하기 이르네요.',
                '더 지켜봐야 할 것 같습니다.'
            );
        END IF;

        -- Review 삽입 (user_id, rating, created_at 추가)
        INSERT INTO review (content, user_id, novel_id, rating, created_at)
        VALUES (
            review_content,
            user_id_var,
            novel_id_var,
            rating_val,
            DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 30) DAY)
        );

        -- ReviewAnalysis 삽입
        INSERT INTO review_analysis (novel_id, author_id, content, sentiment, score, positive_count, negative_count, analyzed_at)
        SELECT
            novel_id_var,
            author_id,
            review_content,
            sentiment_val,
            score_val,
            IF(sentiment_val = 'POSITIVE', FLOOR(1 + RAND() * 4), 0),
            IF(sentiment_val = 'NEGATIVE', FLOOR(1 + RAND() * 4), 0),
            DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 30) DAY)
        FROM novel WHERE id = novel_id_var;

        SET i = i + 1;

        -- Progress (매 10,000개마다)
        IF i % 10000 = 0 THEN
            SELECT CONCAT('Reviews: ', i, ' / 50000') AS progress;
        END IF;
    END WHILE;
END$$
DELIMITER ;

CALL generate_reviews();
DROP PROCEDURE generate_reviews;

-- ========================================
-- 5. EpisodeComment (에피소드 댓글)
-- ========================================
-- 100,000개 댓글 생성

DELIMITER $$
CREATE PROCEDURE generate_comments()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE episode_id_var BIGINT;
    DECLARE writer_id_var BIGINT;
    DECLARE writer_name_var VARCHAR(100);
    DECLARE comment_content TEXT;

    WHILE i <= 100000 DO
        -- 랜덤 에피소드 선택
        SELECT id INTO episode_id_var FROM episode ORDER BY RAND() LIMIT 1;

        -- 랜덤 독자 선택 (ID 51-1050)
        SET writer_id_var = FLOOR(51 + RAND() * 1000);
        SELECT nickname INTO writer_name_var FROM member WHERE id = writer_id_var;

        -- 랜덤 댓글 내용
        SET comment_content = ELT(FLOOR(1 + RAND() * 20),
            '재미있어요!',
            '다음 화 언제 나와요?',
            '전개가 너무 느려요 ㅠㅠ',
            '주인공 너무 멋져요!',
            '이 장면에서 소름 돋았어요',
            '작가님 감사합니다!',
            'ㅋㅋㅋㅋㅋㅋㅋ개웃기네',
            '어? 이거 복선 아니에요?',
            '드디어 나왔다!!!',
            '예상대로 전개되네요',
            '와... 이 반전은 진짜',
            '주인공 이번엔 어떻게 해결할까요?',
            '이 캐릭터 진짜 싫어 ㅡㅡ',
            '작화가 너무 좋아요',
            '스토리 완전 탄탄하네요',
            '이번 화는 별로네요...',
            '감동적이에요 ㅠㅠ',
            '빨리 다음 화 보고 싶어요!',
            '여기서 끊으면 어떡해요 ㅠㅠ',
            '이게 바로 클리프행어...'
        );

        -- EpisodeComment 삽입
        INSERT INTO episode_comment (content, writer_name, writer_id, episode_id, like_count, dislike_count, created_at)
        VALUES (
            comment_content,
            writer_name_var,
            writer_id_var,
            episode_id_var,
            FLOOR(RAND() * 50), -- 좋아요 0~50
            FLOOR(RAND() * 10), -- 싫어요 0~10
            DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 30) DAY)
        );

        SET i = i + 1;

        -- Progress (매 20,000개마다)
        IF i % 20000 = 0 THEN
            SELECT CONCAT('Comments: ', i, ' / 100000') AS progress;
        END IF;
    END WHILE;
END$$
DELIMITER ;

CALL generate_comments();
DROP PROCEDURE generate_comments;

-- ========================================
-- 6. EpisodeReadLog (읽기 로그)
-- ========================================
-- 500,000개 로그 생성 (최근 30일)
-- Phase 6 핵심 데이터

DELIMITER $$
CREATE PROCEDURE generate_read_logs()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE member_id_var BIGINT;
    DECLARE episode_id_var BIGINT;
    DECLARE novel_id_var BIGINT;
    DECLARE scroll_depth_var DECIMAL(5,2);
    DECLARE is_completed_var BOOLEAN;
    DECLARE drop_reason_var VARCHAR(20);
    DECLARE device_type_var VARCHAR(20);
    DECLARE reading_duration INT;
    DECLARE started_at_var DATETIME;

    WHILE i <= 500000 DO
        -- 랜덤 에피소드 선택
        SELECT id, novel_id INTO episode_id_var, novel_id_var
        FROM episode ORDER BY RAND() LIMIT 1;

        -- 70%는 회원, 30%는 비회원
        IF RAND() < 0.7 THEN
            SELECT id INTO member_id_var FROM member WHERE id > 50 ORDER BY RAND() LIMIT 1;
        ELSE
            SET member_id_var = NULL;
        END IF;

        -- 읽기 패턴: 완독(40%), 중간 이탈(30%), 초반 이탈(30%)
        IF RAND() < 0.4 THEN
            -- 완독
            SET scroll_depth_var = 90 + RAND() * 10;
            SET is_completed_var = TRUE;
            SET drop_reason_var = NULL;
        ELSEIF RAND() < 0.7 THEN
            -- 중간 이탈
            SET scroll_depth_var = 40 + RAND() * 40;
            SET is_completed_var = FALSE;
            SET drop_reason_var = 'USER_ACTION';
        ELSE
            -- 초반 이탈
            SET scroll_depth_var = 5 + RAND() * 25;
            SET is_completed_var = FALSE;
            SET drop_reason_var = 'USER_ACTION';
        END IF;

        -- 디바이스: Mobile(60%), Desktop(30%), Tablet(10%)
        IF RAND() < 0.6 THEN
            SET device_type_var = 'MOBILE';
        ELSEIF RAND() < 0.9 THEN
            SET device_type_var = 'DESKTOP';
        ELSE
            SET device_type_var = 'TABLET';
        END IF;

        -- 읽기 시간 (30초 ~ 10분)
        SET reading_duration = FLOOR(30 + RAND() * 570);

        -- 최근 30일 랜덤 시간
        SET started_at_var = DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 30) DAY) - INTERVAL FLOOR(RAND() * 24) HOUR;

        INSERT INTO episode_read_log (
            user_id, episode_id, novel_id, session_id, started_at, ended_at,
            reading_duration_seconds, max_scroll_depth, is_completed, device_type,
            referrer, dropped_at_percentage, drop_reason, created_at
        ) VALUES (
            member_id_var,
            episode_id_var,
            novel_id_var,
            CONCAT('session-', FLOOR(1000000 + RAND() * 8999999)),
            started_at_var,
            DATE_ADD(started_at_var, INTERVAL reading_duration SECOND),
            reading_duration,
            scroll_depth_var,
            is_completed_var,
            device_type_var,
            IF(RAND() < 0.3, 'https://google.com', NULL),
            IF(is_completed_var = FALSE, scroll_depth_var, NULL),
            drop_reason_var,
            started_at_var
        );

        SET i = i + 1;

        -- Progress (매 50,000개마다)
        IF i % 50000 = 0 THEN
            SELECT CONCAT('Read logs: ', i, ' / 500000') AS progress;
        END IF;
    END WHILE;
END$$
DELIMITER ;

CALL generate_read_logs();
DROP PROCEDURE generate_read_logs;

-- ========================================
-- 완료
-- ========================================
SELECT 'Mock data generation completed!' AS status;

-- 데이터 개수 확인
SELECT 'Member count:' AS category, COUNT(*) AS count FROM member
UNION ALL
SELECT 'Novel count:', COUNT(*) FROM novel
UNION ALL
SELECT 'Episode count:', COUNT(*) FROM episode
UNION ALL
SELECT 'Review count:', COUNT(*) FROM review
UNION ALL
SELECT 'ReviewAnalysis count:', COUNT(*) FROM review_analysis
UNION ALL
SELECT 'EpisodeComment count:', COUNT(*) FROM episode_comment
UNION ALL
SELECT 'EpisodeReadLog count:', COUNT(*) FROM episode_read_log;
