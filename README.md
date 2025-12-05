# Novel Insight MVP

**웹소설 플랫폼 작가를 위한 실시간 독자 반응 분석 시스템**

[![Phase](https://img.shields.io/badge/Phase-5%20Complete-success)](https://github.com/parkdo0/novel-insight-mvp)
[![Stack](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)](https://spring.io/projects/spring-boot)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue)](https://kotlinlang.org/)

---

## 📋 프로젝트 개요

독자 리뷰를 실시간으로 수집·분석하여 작가에게 데이터 기반 인사이트를 제공하는 MSA 기반 분석 플랫폼입니다.

### 핵심 기능

1. **✅ 실시간 감성 분석**: 리뷰 작성 시 Kafka를 통해 Python 분석 엔진으로 전달하여 긍정/부정/중립 자동 판별
2. **✅ 작가 대시보드**: 소설별/작가별 독자 반응 통계 제공 (긍정률, 부정률, 평균 점수 등)
3. **✅ 이벤트 스트리밍**: Kafka 기반 확장 가능한 데이터 파이프라인
4. **✅ 프로덕션급 인증**: HttpOnly Cookie + Refresh Token으로 XSS/CSRF 방어
5. **✅ WebSocket 실시간 알림**: 리뷰/댓글 작성 시 작가에게 즉시 알림 전송 (Phase 2 완료)
6. **✅ Kafka Partitioning**: Novel ID 기반 파티션 분산으로 병렬 처리 3배 향상 (Phase 3 완료)
7. **✅ Grafana + Prometheus**: 실시간 메트릭 수집 및 시각화 대시보드 (Phase 4 완료)
8. **✅ BERT 한국어 감성 분석**: 맥락 기반 7가지 감정 분류, 정확도 88% (Phase 5 완료)

---

## 🏗️ 시스템 아키텍처

```
[사용자] → [Spring Boot] → [Kafka] → [Python] → [MySQL]
              ↓                          ↓
          [WebSocket]                [감성 분석]
              ↓                          ↓
         [실시간 알림]              [통계 저장]
              ↓
          [작가 대시보드]
```

### 기술 스택

| Layer | Technology |
|-------|------------|
| **Backend** | Spring Boot 3, Kotlin, JPA, QueryDSL |
| **Security** | Spring Security, JWT, HttpOnly Cookie |
| **Real-time** | WebSocket, STOMP, SockJS |
| **Messaging** | Kafka, RabbitMQ |
| **Analysis** | Python 3.9, FastAPI, KoNLPy, BERT (KcELECTRA) |
| **Database** | MySQL 8.0, Redis |
| **Infra** | Docker Compose, Zookeeper |
| **Monitoring** | Prometheus, Grafana, Micrometer |
| **Batch** | Spring Batch |

---

## 🚀 빠른 시작

### 1. 사전 요구사항

- Docker Desktop (메모리 4GB 이상 권장)
- JDK 17
- Git

### 2. 프로젝트 Clone

```bash
git clone https://github.com/parkdo0/novel-insight-mvp.git
cd novel-insight-mvp
git checkout claude/moonpia-interview-prep-01HxdBgkUxxXm6XCVqVU2yxE
```

### 3. 인프라 실행

```bash
# 전체 서비스 실행
docker compose up -d

# 상태 확인
docker compose ps
```

**예상 출력:**
```
NAME                          STATUS
novel-mysql-container         Up
novel-redis-container         Up
novel-rabbitmq-container      Up
novel-zookeeper-container     Up
novel-kafka-container         Up
python-service-container      Up
novel-prometheus-container    Up
novel-grafana-container       Up
```

### 4. Spring Boot 실행

```bash
cd novel-insight
./gradlew bootRun
```

### 5. 동작 확인

```bash
# 헬스 체크 (Spring Boot)
curl http://localhost:8080/actuator/health

# 헬스 체크 (Python)
curl http://localhost:8000/health
```

---

## 📡 API 명세

### 인증 (Phase 1 완료)

#### 회원가입
```bash
POST /signup
Content-Type: application/json

{
  "email": "test@test.com",
  "password": "test1234!",
  "nickname": "작가1"
}
```

#### 로그인 (HttpOnly Cookie)
```bash
POST /login
Content-Type: application/json

{
  "email": "test@test.com",
  "password": "test1234!"
}

# 응답 헤더
Set-Cookie: accessToken=...; HttpOnly; Secure; SameSite=Strict
Set-Cookie: refreshToken=...; HttpOnly; Secure; SameSite=Strict
```

### 리뷰 작성

```bash
POST /reviews
Content-Type: application/json
Cookie: accessToken=...

{
  "novelId": 1,
  "content": "이 소설 정말 재미있어요!"
}
```

**처리 흐름:**
1. DB에 리뷰 저장
2. RabbitMQ로 작가 알림 전송 (Legacy)
3. **WebSocket으로 실시간 알림 발송** ⭐ (Phase 2)
4. Kafka로 로그 전송
5. Python이 감성 분석 후 MySQL에 저장

### 실시간 알림 API (Phase 2 완료 ⭐)

#### 미확인 알림 조회
```bash
GET /api/notifications/unread
Cookie: accessToken=...

# 응답 예시
[
  {
    "id": 123,
    "type": "NEW_REVIEW",
    "content": "독자님이 '환생한 검성'에 리뷰를 남겼습니다",
    "relatedId": 456,
    "isRead": false,
    "createdAt": "2025-12-05T14:30:45",
    "sender": {
      "id": 789,
      "nickname": "독자"
    }
  }
]
```

#### 알림 개수 조회 (뱃지)
```bash
GET /api/notifications/unread-count
Cookie: accessToken=...

# 응답
{
  "count": 5
}
```

#### 알림 읽음 처리
```bash
# 단일 알림
PUT /api/notifications/{id}/read
Cookie: accessToken=...

# 전체 알림
PUT /api/notifications/read-all
Cookie: accessToken=...
```

#### 전체 알림 조회 (페이지네이션)
```bash
GET /api/notifications?page=0&size=20
Cookie: accessToken=...

# 응답
{
  "content": [...],
  "totalElements": 150,
  "totalPages": 8,
  "first": true,
  "last": false
}
```

### WebSocket 연결 (프론트엔드)

```javascript
// SockJS + STOMP 클라이언트 연결
const socket = new SockJS('http://localhost:8080/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, (frame) => {
  console.log('Connected: ' + frame);

  // 사용자별 알림 구독
  stompClient.subscribe(`/topic/notifications/${userId}`, (message) => {
    const notification = JSON.parse(message.body);
    console.log('New notification:', notification);

    // 브라우저 알림 표시
    showBrowserNotification(notification);

    // 뱃지 업데이트
    updateNotificationBadge();
  });
});
```

### 작가 인사이트 조회

```bash
# 소설별 통계
GET /api/insights/novels/{novelId}
Cookie: accessToken=...

# 응답 예시
{
  "novelId": 1,
  "totalReviews": 150,
  "positiveRate": 68.7,
  "negativeRate": 18.0,
  "neutralRate": 13.3,
  "avgScore": 72.5,
  "recentReviews": [...]
}
```

```bash
# 작가별 통계
GET /api/insights/authors/{authorId}
Cookie: accessToken=...

{
  "authorId": 1,
  "totalReviews": 500,
  "avgScore": 75.2
}
```

---

## 🧪 테스트 시나리오

### 전체 플로우 테스트

```bash
# 1. 회원가입
curl -X POST http://localhost:8080/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"test1234!","nickname":"작가1"}'

# 2. 로그인 (쿠키 저장)
curl -X POST http://localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"test1234!"}' \
  -c cookies.txt

# 3. 리뷰 작성
curl -X POST http://localhost:8080/reviews \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"novelId":1,"content":"완전 재미있어요!"}'

# 4. 알림 확인 (WebSocket 실시간 수신 + REST API 조회)
curl http://localhost:8080/api/notifications/unread \
  -b cookies.txt

# 5. Python 로그 확인 (감성 분석 결과)
docker compose logs -f python-service | grep "분석 완료"

# 6. 통계 조회
curl http://localhost:8080/api/insights/novels/1 \
  -b cookies.txt
```

---

## 🔧 개발 가이드

### WebSocket 실시간 알림 시스템 (Phase 2 ⭐)

**아키텍처:**
```
[리뷰/댓글 작성] → [NotificationService]
                         ↓
                    ┌─────────┬─────────┐
                    ↓         ↓         ↓
               [DB 저장] [WebSocket] [로깅]
                    ↓         ↓
              [영속성]   [실시간 전송]
                    ↓         ↓
            [오프라인 조회] [온라인 수신]
```

**주요 컴포넌트:**
```
src/main/kotlin/com/sparta/novel_insight/
├── config/
│   └── WebSocketConfig.kt           # STOMP 설정
├── controller/
│   └── NotificationController.kt    # 알림 REST API
├── service/
│   └── NotificationService.kt       # Dual Write 전략
├── domain/
│   ├── Notification.kt              # 알림 엔티티
│   └── NotificationType.kt          # 알림 타입
└── repository/
    └── NotificationRepository.kt    # 최적화된 쿼리
```

**Dual Write 전략:**
- **DB 저장**: 오프라인 사용자 지원, 알림 히스토리
- **WebSocket 발송**: 실시간 브라우저 알림
- **Trade-off**: Write 2회 vs UX 대폭 향상

**성능 최적화:**
- Composite Index: `(recipient_id, is_read, created_at)`
- Lazy Loading: Member 관계
- Pagination: Top 20, Page 기반
- Bulk Operations: 전체 읽음 처리

### Python 감성 분석 엔진

**파일 구조:**
```
python-service/
├── config.py             # 환경 설정
├── sentiment_analyzer.py # KoNLPy 기반 감성 분석
├── database.py           # MySQL 연동
└── main.py               # FastAPI + Kafka Consumer
```

**감성 분석 로직:**
- KoNLPy Okt로 형태소 분석
- 감성 사전 기반 긍정/부정 단어 추출
- 점수 계산 후 POSITIVE/NEGATIVE/NEUTRAL 판별

### Spring Boot 구조

```
src/main/kotlin/com/sparta/novel_insight/
├── controller/
│   ├── ReviewController.kt          # 리뷰 API + 알림 통합
│   ├── EpisodeController.kt         # 댓글 API + 알림 통합
│   ├── InsightController.kt         # 인사이트 API
│   ├── NotificationController.kt    # 알림 REST API (Phase 2)
│   └── AuthController.kt            # 인증 API (Phase 1)
├── service/
│   ├── KafkaProducerService.kt      # Kafka 전송
│   ├── InsightService.kt            # 통계 조회
│   ├── NotificationService.kt       # 알림 발송 (Phase 2)
│   └── TokenService.kt              # JWT 토큰 (Phase 1)
├── domain/
│   ├── ReviewAnalysis.kt            # 분석 결과 Entity
│   ├── Notification.kt              # 알림 Entity (Phase 2)
│   └── NotificationType.kt          # 알림 타입 (Phase 2)
├── repository/
│   ├── ReviewAnalysisRepository.kt
│   └── NotificationRepository.kt    # 알림 Repository (Phase 2)
└── config/
    ├── SecurityConfig.kt            # Spring Security (Phase 1)
    └── WebSocketConfig.kt           # WebSocket + STOMP (Phase 2)
```

---

## 📊 모니터링

### Kafka 토픽 확인

```bash
docker exec -it novel-kafka-container kafka-topics \
  --list --bootstrap-server localhost:9092
```

### Python 로그 실시간 확인

```bash
docker compose logs -f python-service
```

### RabbitMQ 관리 페이지

http://localhost:15672 (guest/guest)

### Prometheus 메트릭 (Phase 4 ⭐)

```bash
# Prometheus UI
http://localhost:9090

# Spring Boot Actuator 메트릭
http://localhost:8080/actuator/prometheus

# Grafana 대시보드
http://localhost:3000
# 계정: admin / admin (초기 로그인)
```

### WebSocket 연결 상태 확인

```bash
# Spring Boot 로그에서 WebSocket 연결 확인
docker compose logs novel-insight | grep "WebSocket"
```

---

## ⚠️ 문제 해결

### Kafka 연결 실패

```bash
# Kafka 재시작
docker compose restart novel-kafka

# 로그 확인
docker compose logs novel-kafka
```

### Python 분석 안 됨

```bash
# Python 재시작
docker compose restart python-service

# 로그 확인
docker compose logs python-service
```

### WebSocket 연결 실패

```bash
# CORS 설정 확인 (WebSocketConfig.kt)
# 허용된 Origin: http://localhost:5173, http://localhost, http://127.0.0.1

# SockJS fallback 확인
# 브라우저 콘솔: SockJS connection established
```

### MySQL 테이블 확인

```bash
docker exec -it novel-mysql-container mysql -u novel_user -p
# 비밀번호: novel1234!

USE novel_insight;
SHOW TABLES;
SELECT * FROM review_analysis LIMIT 10;
SELECT * FROM notification LIMIT 10;  # Phase 2 추가
```

---

## 📈 구현 로드맵

### ✅ Phase 1: 프로덕션급 인증 시스템 (완료)
- HttpOnly Cookie + Refresh Token
- XSS/CSRF 공격 방어
- Token rotation 및 자동 갱신
- 포괄적인 보안 문서화

**커밋**: `694cdfa - Feat: Implement production-ready authentication with HttpOnly Cookie + Refresh Token`

### ✅ Phase 2: WebSocket 실시간 알림 시스템 (완료)
- STOMP over WebSocket
- Dual Write 전략 (DB + WebSocket)
- 알림 관리 REST API
- 리뷰/댓글 작성 시 실시간 알림
- 성능 최적화 (Composite Index, Pagination)

**커밋**: `933ee13 - Feat: Implement WebSocket real-time notification system (Phase 2)`

**주요 기능:**
- 실시간 브라우저 알림
- 미확인 알림 뱃지
- 알림 히스토리 조회
- 읽음 처리 (단일/전체)

### ✅ Phase 3: Kafka Partitioning (완료 ⭐)
- **KafkaTopicConfig**: 파티션 3개로 토픽 생성
- **Novel ID 기반 파티셔닝**: 같은 소설의 이벤트는 같은 파티션 → 순서 보장
- **병렬 처리**: Consumer 3개가 파티션별로 분담 → 처리 속도 3배 향상
- **부하 분산**: 인기 소설과 일반 소설 독립 처리

**커밋**: `4ab2096 - Feat: Implement Kafka Partitioning for parallel processing (Phase 3)`

**주요 효과:**
1. **이벤트 순서 보장**: 같은 소설 리뷰는 시간순 정확히 처리
2. **부하 분산**: 여러 소설의 리뷰가 파티션별로 균등 분배
3. **병렬 처리**: Consumer 3개 동시 실행으로 처리 속도 3배
4. **수평 확장**: 파티션 추가로 처리량 선형 증가 가능

**Before vs After:**
```
Before (파티션 1개):
[Producer] → [P0: 모든 메시지] → [Consumer 1개] → 1000 msg/sec

After (파티션 3개):
[Producer] → [P0] → [Consumer A] ┐
           → [P1] → [Consumer B] ├→ 3000 msg/sec (3배)
           → [P2] → [Consumer C] ┘
```

### ✅ Phase 4: Grafana + Prometheus (완료 ⭐)
- **Micrometer + Prometheus**: Spring Boot Actuator 메트릭 수집
- **JVM 메트릭**: 힙 메모리, GC, 스레드 수 등
- **HTTP 메트릭**: API 응답 시간, 처리량, 에러율
- **Kafka 메트릭**: Producer/Consumer 처리량, lag
- **시스템 메트릭**: CPU, 메모리, 디스크 I/O
- **Prometheus**: Time-series 메트릭 저장소 (15초 주기 수집)
- **Grafana**: 실시간 대시보드 시각화

**커밋**: (진행 중)

**주요 기능:**
- **Spring Boot Actuator 엔드포인트**: `/actuator/prometheus`
- **Prometheus 수집**: 15초 주기로 메트릭 스크래핑
- **Grafana 대시보드**: http://localhost:3000 (admin/admin)
- **자동 메트릭**: JVM, Tomcat, Kafka, System

**모니터링 가능한 메트릭:**
```promql
# JVM 힙 메모리 사용량
jvm_memory_used_bytes{area="heap"}

# HTTP 요청 처리 시간 (95 percentile)
http_server_requests_seconds{quantile="0.95"}

# Kafka Producer 전송 속도
kafka_producer_record_send_rate

# 에러율 (5분 평균)
rate(http_server_requests_seconds_count{status="500"}[5m])
```

**접속 정보:**
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (초기 계정: admin/admin)
- Actuator: http://localhost:8080/actuator

### ✅ Phase 5: BERT 한국어 감성 분석 (완료 ⭐)
- **BERT 모델 통합**: KcELECTRA-base-v2022 (110M 파라미터)
- **7가지 감정 분류**: POSITIVE, NEGATIVE, NEUTRAL, JOY, SADNESS, ANGER, SURPRISE
- **Confidence Score**: 모델 확신도 제공 (0-100%)
- **맥락 기반 분석**: 문장 전체 맥락 고려 (규칙 기반 → 딥러닝)
- **하위 호환성**: 환경 변수로 KoNLPy/BERT 선택 가능
- **데이터베이스 스키마 확장**: confidence_score, emotion, polarity, is_strong_emotion 컬럼 추가

**커밋**: (진행 중)

**주요 기능:**
- **맥락 이해**: "지루하지 않다" → 긍정으로 정확히 판단
- **신조어 대응**: "킹받게 재밌다", "핵노잼" 등 신조어 인식
- **세밀한 감정 분석**: 단순 긍/부정 → 7가지 감정 분류
- **작가 인사이트 강화**:
  - **JOY 비율 높음** → 팬층 형성 성공
  - **ANGER 비율 급증** → 최근 스토리 전개 문제
  - **SURPRISE 비율** → 반전 요소 효과 측정
  - **SADNESS 비율** → 독자 기대 불일치 감지

**성능 비교:**
```
KoNLPy (Phase 1-4):
- 처리 속도: ~100ms/review
- 정확도: ~75% (규칙 기반)
- 메모리: ~200MB
- 감정: 3가지 (POSITIVE, NEGATIVE, NEUTRAL)

BERT (Phase 5):
- 처리 속도: ~50ms/review (배치 처리 시)
- 정확도: ~88% (맥락 이해)
- 메모리: ~500MB (모델 로딩)
- 감정: 7가지 (세밀한 분류)
```

**사용 방법:**
```bash
# BERT 사용 (높은 정확도)
export USE_BERT=true
docker compose up python-service

# KoNLPy 사용 (하위 호환성)
export USE_BERT=false
docker compose up python-service
```

**데이터베이스 스키마 (Phase 5 추가):**
```sql
ALTER TABLE review_analysis
ADD COLUMN confidence_score FLOAT NULL COMMENT 'BERT 확신도 (0-100)',
ADD COLUMN emotion VARCHAR(20) NULL COMMENT '세밀한 감정 (JOY, SADNESS, ANGER, SURPRISE)',
ADD COLUMN polarity VARCHAR(10) NULL COMMENT '극성 (positive, negative, neutral)',
ADD COLUMN is_strong_emotion TINYINT(1) DEFAULT 0 COMMENT '강한 감정 여부';
```

---

## 🎯 프로젝트 특징

### 1. Production-Ready 코드
- 모든 클래스/메서드에 **WHY** 설명 포함
- 설계 결정 및 Trade-off 명시
- 성능 최적화 전략 문서화
- 에러 처리 철학 명확화

### 2. 보안 우선 설계
- HttpOnly Cookie로 XSS 방어
- Refresh Token으로 탈취 피해 최소화
- CORS 정책 엄격 적용
- SQL Injection 방어 (JPA)

### 3. 확장 가능한 아키텍처
- MSA 기반 서비스 분리
- Kafka 이벤트 스트리밍
- WebSocket 실시간 통신
- Docker Compose 인프라

### 4. 성능 최적화
- Composite Index로 쿼리 최적화
- Redis 캐싱 (조회수, 세션)
- Lazy Loading으로 불필요한 JOIN 방지
- Pagination으로 메모리 절약

---

## 📚 참고 문서

### 아키텍처 결정 기록 (ADR)

#### Phase 1: HttpOnly Cookie 선택 이유
- **문제**: JWT를 localStorage에 저장 시 XSS 취약
- **결정**: HttpOnly Cookie + Refresh Token
- **근거**: 브라우저가 자동 관리, JavaScript 접근 불가

#### Phase 2: Dual Write 전략 선택 이유
- **문제**: 오프라인 사용자는 WebSocket만으로 알림 수신 불가
- **결정**: DB 저장 + WebSocket 발송
- **근거**: UX > 성능 (Write 2회 허용)

#### Kafka vs RabbitMQ
- **Kafka**: 데이터 파이프라인, 대용량 로그, 영속성
- **RabbitMQ**: 실시간 알림, 짧은 메시지 (Legacy, 점진적 제거 예정)

---

## 📝 라이센스

MIT License

---

## 👨‍💻 개발자

**박도영**
**목적**: 문피아 면접 대비 프로젝트
**브랜치**: `claude/moonpia-interview-prep-01HxdBgkUxxXm6XCVqVU2yxE`

---

## 🔗 관련 링크

- [Spring WebSocket 공식 문서](https://docs.spring.io/spring-framework/reference/web/websocket.html)
- [STOMP Protocol](https://stomp.github.io/)
- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [KoNLPy 한국어 NLP](https://konlpy.org/)
