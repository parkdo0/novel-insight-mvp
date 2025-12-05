# Phase 6: 사용자 행동 분석 & 통합 대시보드 설계

## 📋 개요

작가에게 **데이터 기반 인사이트**를 제공하기 위한 사용자 행동 분석 시스템입니다.

### 핵심 질문
1. **독자들이 어느 에피소드를 가장 많이 읽는가?** (조회 패턴)
2. **어느 지점에서 이탈하는가?** (이탈률 분석)
3. **감정 분석 결과와 조회 패턴의 상관관계는?** (통합 인사이트)

---

## 🏗️ 시스템 아키텍처

```
[사용자 행동] → [Spring Boot] → [Kafka] → [로그 저장]
                    ↓                ↓
                [Redis 캐시]    [MySQL 로그]
                    ↓                ↓
            [실시간 통계]      [배치 집계]
                    ↓                ↓
            [통합 대시보드 API] ← [감정 분석 데이터]
```

---

## 📊 데이터베이스 설계

### 1. **episode_read_log** (에피소드 조회 로그)

**목적**: 사용자가 에피소드를 읽은 기록을 저장

```sql
CREATE TABLE episode_read_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- 기본 정보
    user_id BIGINT NOT NULL COMMENT '사용자 ID (비회원은 NULL)',
    episode_id BIGINT NOT NULL COMMENT '에피소드 ID',
    novel_id BIGINT NOT NULL COMMENT '소설 ID (조회 최적화)',

    -- 읽기 행동 정보
    started_at DATETIME NOT NULL COMMENT '읽기 시작 시간',
    ended_at DATETIME NULL COMMENT '읽기 종료 시간 (NULL = 진행 중)',
    reading_duration_seconds INT NULL COMMENT '읽기 시간 (초)',

    -- 스크롤 정보
    max_scroll_depth DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '최대 스크롤 깊이 (0-100%)',
    is_completed BOOLEAN DEFAULT FALSE COMMENT '완독 여부 (90% 이상)',

    -- 세션 정보
    session_id VARCHAR(100) NOT NULL COMMENT '세션 ID (비회원 추적)',
    device_type VARCHAR(20) NOT NULL COMMENT '디바이스 (MOBILE, TABLET, DESKTOP)',
    referrer VARCHAR(500) NULL COMMENT '유입 경로',

    -- 이탈 정보
    dropped_at_percentage DECIMAL(5,2) NULL COMMENT '이탈 지점 (%)',
    drop_reason VARCHAR(50) NULL COMMENT '이탈 이유 (USER_ACTION, TIMEOUT, ERROR)',

    -- 타임스탬프
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    -- 인덱스
    INDEX idx_user_episode (user_id, episode_id),
    INDEX idx_episode_created (episode_id, created_at),
    INDEX idx_novel_created (novel_id, created_at),
    INDEX idx_session (session_id),
    INDEX idx_completed (is_completed, episode_id)
) COMMENT='에피소드 조회 로그 (Phase 6)';
```

**WHY 이 스키마?**
- `max_scroll_depth`: 사용자가 얼마나 깊이 읽었는지 → 흥미도 측정
- `is_completed`: 완독률 계산 (90% 이상 읽으면 완독)
- `session_id`: 비회원도 추적 가능
- `dropped_at_percentage`: 정확한 이탈 지점 파악

---

### 2. **drop_off_point** (이탈 지점 집계 테이블)

**목적**: 에피소드별 이탈 지점 통계 (배치 집계)

```sql
CREATE TABLE drop_off_point (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- 기본 정보
    episode_id BIGINT NOT NULL COMMENT '에피소드 ID',
    novel_id BIGINT NOT NULL COMMENT '소설 ID',

    -- 이탈 지점 통계 (10% 단위)
    drop_0_10_percent INT DEFAULT 0 COMMENT '0-10% 이탈자 수',
    drop_10_20_percent INT DEFAULT 0 COMMENT '10-20% 이탈자 수',
    drop_20_30_percent INT DEFAULT 0 COMMENT '20-30% 이탈자 수',
    drop_30_40_percent INT DEFAULT 0 COMMENT '30-40% 이탈자 수',
    drop_40_50_percent INT DEFAULT 0 COMMENT '40-50% 이탈자 수',
    drop_50_60_percent INT DEFAULT 0 COMMENT '50-60% 이탈자 수',
    drop_60_70_percent INT DEFAULT 0 COMMENT '60-70% 이탈자 수',
    drop_70_80_percent INT DEFAULT 0 COMMENT '70-80% 이탈자 수',
    drop_80_90_percent INT DEFAULT 0 COMMENT '80-90% 이탈자 수',
    drop_90_100_percent INT DEFAULT 0 COMMENT '90-100% 이탈자 수',

    -- 통계 정보
    total_readers INT NOT NULL DEFAULT 0 COMMENT '전체 읽은 사람 수',
    completed_readers INT NOT NULL DEFAULT 0 COMMENT '완독자 수',
    completion_rate DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '완독률 (%)',
    avg_scroll_depth DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '평균 스크롤 깊이 (%)',

    -- 집계 기간
    aggregated_date DATE NOT NULL COMMENT '집계 날짜',

    -- 타임스탬프
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- 인덱스
    UNIQUE KEY idx_episode_date (episode_id, aggregated_date),
    INDEX idx_novel_date (novel_id, aggregated_date),
    INDEX idx_completion_rate (completion_rate)
) COMMENT='에피소드별 이탈 지점 집계 (Phase 6 - 배치 처리)';
```

**WHY 10% 단위 집계?**
- **시각화 용이**: 차트로 표현하기 쉬움
- **패턴 발견**: 특정 구간에서 이탈 급증 → 문제 지점 발견
- **성능**: 실시간 계산 대신 배치 집계 → 빠른 응답

---

### 3. **author_dashboard_cache** (대시보드 캐시 테이블)

**목적**: 작가 대시보드 통계를 사전 계산하여 빠른 응답

```sql
CREATE TABLE author_dashboard_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- 기본 정보
    author_id BIGINT NOT NULL COMMENT '작가 ID',
    novel_id BIGINT NULL COMMENT '소설 ID (NULL = 작가 전체)',

    -- 조회 통계
    total_views INT NOT NULL DEFAULT 0 COMMENT '전체 조회수',
    unique_readers INT NOT NULL DEFAULT 0 COMMENT '고유 독자 수',
    avg_reading_time_seconds INT NOT NULL DEFAULT 0 COMMENT '평균 읽기 시간 (초)',
    completion_rate DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '평균 완독률 (%)',

    -- 감정 분석 통계 (Phase 5 통합)
    total_reviews INT NOT NULL DEFAULT 0 COMMENT '전체 리뷰 수',
    positive_rate DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '긍정률 (%)',
    negative_rate DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '부정률 (%)',
    joy_rate DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '기쁨 감정 비율 (%)',
    anger_rate DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '분노 감정 비율 (%)',

    -- 인기 에피소드 (JSON)
    top_episodes JSON NULL COMMENT '인기 에피소드 TOP 10',
    drop_off_episodes JSON NULL COMMENT '이탈률 높은 에피소드 TOP 10',

    -- 집계 기간
    aggregated_date DATE NOT NULL COMMENT '집계 날짜',
    period_type VARCHAR(20) NOT NULL COMMENT '기간 타입 (DAILY, WEEKLY, MONTHLY)',

    -- 타임스탬프
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- 인덱스
    UNIQUE KEY idx_author_novel_date (author_id, novel_id, aggregated_date, period_type),
    INDEX idx_author_date (author_id, aggregated_date)
) COMMENT='작가 대시보드 캐시 (Phase 6 - 매 시간 갱신)';
```

**WHY 캐시 테이블?**
- **성능**: 실시간 JOIN 대신 사전 계산 → 응답 시간 10배 빠름
- **복잡한 통계**: 여러 테이블 조인 + 집계 → 미리 계산
- **시계열 분석**: 날짜별 트렌드 조회 용이

---

## 🚀 캐싱 전략 (3-Tier Architecture)

### Tier 1: Redis (실시간 통계)

**사용 케이스**: 초단위 변화하는 데이터

```
// 현재 읽고 있는 사용자 수 (세션 기반)
Key: "episode:reading:{episodeId}"
Type: SET
Value: [sessionId1, sessionId2, ...]
TTL: 30분

// 실시간 조회수 (카운터)
Key: "episode:views:{episodeId}:{date}"
Type: STRING (increment)
Value: 1234
TTL: 24시간

// 실시간 평균 스크롤 깊이
Key: "episode:scroll:{episodeId}:{date}"
Type: HASH
Fields: {sum: 12345, count: 100}
TTL: 24시간
```

**WHY Redis?**
- **빠름**: 메모리 기반, 1ms 미만 응답
- **원자성**: INCR, SADD 등 원자적 연산 지원
- **TTL**: 자동 만료로 메모리 관리

---

### Tier 2: MySQL Cache Table (시간 단위 집계)

**사용 케이스**: 시간/일 단위 통계

```sql
-- 매 시간 배치 작업으로 집계
-- episode_read_log → drop_off_point
-- episode_read_log → author_dashboard_cache
```

**WHY MySQL 캐시?**
- **영속성**: Redis 재시작 시에도 데이터 유지
- **복잡한 쿼리**: JOIN, GROUP BY 가능
- **히스토리**: 과거 데이터 조회 가능

---

### Tier 3: MySQL Raw Log (영구 보관)

**사용 케이스**: 원본 로그 저장

```sql
-- episode_read_log: 모든 조회 로그 영구 보관
-- 추후 ML 학습 데이터로 활용 가능
```

---

## 📈 배치 집계 작업 (Spring Batch)

### Job 1: Hourly Drop-Off Aggregation

**실행 주기**: 매 시간 5분 (크론: `0 5 * * * *`)

**처리 흐름**:
```
1. episode_read_log에서 지난 1시간 데이터 조회
2. 에피소드별로 GROUP BY
3. 이탈 지점 10% 단위로 집계
4. drop_off_point 테이블에 INSERT/UPDATE
```

**SQL 예시**:
```sql
INSERT INTO drop_off_point (
    episode_id,
    novel_id,
    drop_0_10_percent,
    drop_10_20_percent,
    ...
    total_readers,
    completed_readers,
    aggregated_date
)
SELECT
    episode_id,
    novel_id,
    SUM(CASE WHEN dropped_at_percentage BETWEEN 0 AND 10 THEN 1 ELSE 0 END),
    SUM(CASE WHEN dropped_at_percentage BETWEEN 10 AND 20 THEN 1 ELSE 0 END),
    ...
    COUNT(*),
    SUM(CASE WHEN is_completed = TRUE THEN 1 ELSE 0 END),
    DATE(NOW())
FROM episode_read_log
WHERE created_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
GROUP BY episode_id, novel_id
ON DUPLICATE KEY UPDATE
    drop_0_10_percent = VALUES(drop_0_10_percent),
    ...
```

---

### Job 2: Daily Dashboard Cache Update

**실행 주기**: 매일 새벽 2시 (크론: `0 0 2 * * *`)

**처리 흐름**:
```
1. 작가별로 모든 소설 통계 집계
2. 감정 분석 데이터와 조인
3. TOP 10 에피소드 계산 (JSON 저장)
4. author_dashboard_cache에 INSERT/UPDATE
```

---

## 🎨 통합 대시보드 API 스펙

### GET /api/dashboard/author/{authorId}

**응답 예시**:
```json
{
  "author": {
    "id": 1,
    "name": "박작가",
    "totalNovels": 5
  },

  "overview": {
    "totalViews": 125000,
    "uniqueReaders": 8500,
    "avgCompletionRate": 68.5,
    "avgReadingTimeMinutes": 12.3
  },

  "sentimentAnalysis": {
    "totalReviews": 1500,
    "positiveRate": 72.3,
    "joyRate": 28.5,
    "angerRate": 5.2,
    "avgConfidence": 87.3
  },

  "topEpisodes": [
    {
      "episodeId": 123,
      "title": "1화: 환생",
      "views": 15000,
      "completionRate": 85.2,
      "avgScrollDepth": 92.1,
      "joyRate": 45.3
    }
  ],

  "dropOffAnalysis": [
    {
      "episodeId": 456,
      "title": "10화: 전투",
      "completionRate": 45.2,
      "dropOffPoints": [
        {"range": "0-10%", "count": 50},
        {"range": "10-20%", "count": 120},
        {"range": "20-30%", "count": 450},  // 이탈 급증 지점!
        ...
      ]
    }
  ],

  "insights": [
    {
      "type": "WARNING",
      "message": "10화의 20-30% 구간에서 이탈률이 급증했습니다. 해당 부분을 검토해보세요.",
      "episodeId": 456,
      "severity": "HIGH"
    },
    {
      "type": "SUCCESS",
      "message": "1화의 완독률이 85%로 매우 높습니다! 도입부가 효과적입니다.",
      "episodeId": 123,
      "severity": "POSITIVE"
    }
  ]
}
```

---

## 🔥 실무 최적화 전략

### 1. **파티셔닝** (대용량 로그 처리)

```sql
-- episode_read_log를 월별로 파티셔닝
ALTER TABLE episode_read_log
PARTITION BY RANGE (YEAR(created_at) * 100 + MONTH(created_at)) (
    PARTITION p202501 VALUES LESS THAN (202502),
    PARTITION p202502 VALUES LESS THAN (202503),
    ...
);
```

**WHY?**
- 1년 후 수백만 건 로그 → 파티셔닝으로 쿼리 속도 10배 향상
- 오래된 파티션 삭제 용이

---

### 2. **Read Replica** (읽기 부하 분산)

```
[Writer] → [MySQL Master] → [Replication] → [MySQL Slave]
              ↓                                    ↓
        [로그 INSERT]                       [통계 SELECT]
```

**WHY?**
- 로그 쓰기 + 통계 읽기 → DB 부하 분산
- 대시보드 조회 시 Slave 사용 → Master 부하 감소

---

### 3. **비동기 로깅** (Spring Event)

```kotlin
// 사용자가 에피소드 읽기 → 이벤트 발행
applicationEventPublisher.publishEvent(
    EpisodeReadEvent(userId, episodeId, scrollDepth)
)

// 비동기 리스너가 로그 저장
@Async
@EventListener
fun handleEpisodeRead(event: EpisodeReadEvent) {
    episodeReadLogRepository.save(...)
}
```

**WHY?**
- 사용자 응답 속도에 영향 없음
- 로그 저장 실패해도 서비스 중단 안 됨

---

## 📊 기대 효과

### AS-IS (Phase 1-5)
- 감정 분석만 제공
- 작가가 "왜 독자들이 이탈하는지" 모름
- 데이터 기반 의사결정 불가

### TO-BE (Phase 6)
- **정량적 인사이트**: "10화 20-30% 구간에서 이탈률 450명"
- **감정 + 행동 통합**: "분노 감정 높은 에피소드 = 이탈률 높음"
- **즉각적 피드백**: "도입부 완독률 85% → 도입 전략 성공"

---

## 🚀 다음 단계

Phase 6 구현 시작하겠습니다:
1. ✅ DB 스키마 설계 완료
2. Entity 및 Repository 작성
3. UserBehaviorLogService 구현
4. Redis 캐싱 구현
5. Spring Batch 집계 작업
6. 통합 대시보드 API
7. README 업데이트 및 커밋

진행할까요?
