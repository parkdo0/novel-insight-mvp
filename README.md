# Novel Insight MVP

**웹소설 플랫폼 작가를 위한 실시간 독자 반응 분석 시스템**

---

## 📋 프로젝트 개요

독자 리뷰를 실시간으로 수집·분석하여 작가에게 데이터 기반 인사이트를 제공하는 MSA 기반 분석 플랫폼입니다.

### 핵심 기능

1. **실시간 감성 분석**: 리뷰 작성 시 Kafka를 통해 Python 분석 엔진으로 전달하여 긍정/부정/중립 자동 판별
2. **작가 대시보드**: 소설별/작가별 독자 반응 통계 제공 (긍정률, 부정률, 평균 점수 등)
3. **이벤트 스트리밍**: Kafka 기반 확장 가능한 데이터 파이프라인

---

## 🏗️ 시스템 아키텍처

```
[사용자] → [Spring Boot] → [Kafka] → [Python] → [MySQL]
              ↓                          ↓
          [RabbitMQ]                 [감성 분석]
              ↓                          ↓
          [작가 알림]              [통계 저장]
```

### 기술 스택

| Layer | Technology |
|-------|------------|
| **Backend** | Spring Boot 3, Kotlin, JPA, QueryDSL |
| **Security** | Spring Security, JWT |
| **Messaging** | Kafka, RabbitMQ |
| **Analysis** | Python 3.9, FastAPI, KoNLPy (Okt) |
| **Database** | MySQL 8.0, Redis |
| **Infra** | Docker Compose, Zookeeper |
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

### 리뷰 작성

```bash
POST /reviews
Content-Type: application/json

{
  "novelId": 1,
  "content": "이 소설 정말 재미있어요!"
}
```

**처리 흐름:**
1. DB에 리뷰 저장
2. RabbitMQ로 작가 알림 전송
3. Kafka로 로그 전송
4. Python이 감성 분석 후 MySQL에 저장

### 작가 인사이트 조회

```bash
# 소설별 통계
GET /api/insights/novels/{novelId}

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

# 2. 로그인
curl -X POST http://localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"test1234!"}'

# 3. 리뷰 작성
curl -X POST http://localhost:8080/reviews \
  -H "Content-Type: application/json" \
  -d '{"novelId":1,"content":"완전 재미있어요!"}'

# 4. Python 로그 확인 (감성 분석 결과)
docker compose logs -f python-service | grep "분석 완료"

# 5. 통계 조회
curl http://localhost:8080/api/insights/novels/1
```

---

## 🔧 개발 가이드

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
│   ├── ReviewController.kt      # 리뷰 API
│   └── InsightController.kt     # 인사이트 API
├── service/
│   ├── KafkaProducerService.kt  # Kafka 전송
│   └── InsightService.kt        # 통계 조회
├── domain/
│   └── ReviewAnalysis.kt        # 분석 결과 Entity
└── repository/
    └── ReviewAnalysisRepository.kt
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

### MySQL 테이블 확인

```bash
docker exec -it novel-mysql-container mysql -u novel_user -p
# 비밀번호: novel1234!

USE novel_insight;
SHOW TABLES;
SELECT * FROM review_analysis LIMIT 10;
```

---

## 📈 확장 가능성

1. **Kafka 파티셔닝**: 소설 ID 기반 파티션 분산으로 처리 속도 향상
2. **ML 모델 적용**: BERT 기반 한국어 감성 분석 모델로 정확도 개선
3. **실시간 대시보드**: Grafana + Prometheus로 메트릭 시각화
4. **작가 알림**: WebSocket 기반 실시간 알림

---

## 📝 라이센스

MIT License

---

**개발자:** 박도영
**목적:** 문피아 면접 대비 프로젝트
