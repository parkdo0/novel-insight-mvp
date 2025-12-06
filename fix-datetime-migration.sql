-- ===================================
-- Fix DateTime Migration Errors
-- ===================================
-- 문제: 기존 테이블에 NOT NULL datetime 컬럼 추가 시 기본값 미적용
-- 해결: 기존 데이터에 유효한 날짜 값 설정
-- ===================================

USE novel_insight;

-- 1. episode 테이블 수정
-- 기존 created_at 컬럼이 있으면 먼저 삭제 (에러 무시)
ALTER TABLE episode DROP COLUMN IF EXISTS created_at;
ALTER TABLE episode DROP COLUMN IF EXISTS published_at;

-- nullable로 추가 후 기본값 설정
ALTER TABLE episode ADD COLUMN created_at DATETIME NULL;
ALTER TABLE episode ADD COLUMN published_at DATETIME NULL;

-- 기존 데이터에 현재 시간 설정
UPDATE episode SET created_at = NOW() WHERE created_at IS NULL;
UPDATE episode SET published_at = NOW() WHERE published_at IS NULL;

-- NOT NULL 제약조건 추가
ALTER TABLE episode MODIFY COLUMN created_at DATETIME NOT NULL;
ALTER TABLE episode MODIFY COLUMN published_at DATETIME NOT NULL;

-- 2. member 테이블 수정
ALTER TABLE member DROP COLUMN IF EXISTS created_at;
ALTER TABLE member ADD COLUMN created_at DATETIME NULL;
UPDATE member SET created_at = NOW() WHERE created_at IS NULL;
ALTER TABLE member MODIFY COLUMN created_at DATETIME NOT NULL;

-- 3. review 테이블 수정
ALTER TABLE review DROP COLUMN IF EXISTS created_at;
ALTER TABLE review ADD COLUMN created_at DATETIME NULL;
UPDATE review SET created_at = NOW() WHERE created_at IS NULL;
ALTER TABLE review MODIFY COLUMN created_at DATETIME NOT NULL;

-- 4. 결과 확인
SELECT 'Migration completed successfully!' as status;
SELECT COUNT(*) as episode_count, MIN(created_at) as min_date, MAX(created_at) as max_date FROM episode;
SELECT COUNT(*) as member_count, MIN(created_at) as min_date, MAX(created_at) as max_date FROM member;
SELECT COUNT(*) as review_count, MIN(created_at) as min_date, MAX(created_at) as max_date FROM review;
