# 📚 Novel Insight MVP - 전체 실행 가이드

**"문피아에서 받들고 싶은 것" - 데이터 기반 작가 인사이트 시스템**

---

## 🎯 시스템 개요

### **핵심 기능:**
1. **리뷰 수집**: 사용자가 리뷰 작성 시 Kafka로 로그 전송
2. **실시간 분석**: Python이 Kafka에서 로그를 수집하여 감성 분석 (긍정/부정/중립)
3. **작가 인사이트**: 작가는 자기 소설에 대한 독자 반응을 실시간으로 확인 가능
4. **배치 집계**: Spring Batch로 일일 통계 자동 집계

### **기술 스택:**
- **Backend**: Spring Boot 3 + Kotlin, JPA, Security(JWT), Kafka Producer
- **Infra**: Docker Compose (MySQL, Redis, RabbitMQ, Kafka, Zookeeper)
- **Analysis**: Python (FastAPI, Kafka Consumer, 감성 분석)
- **Batch**: Spring Batch (일일 통계)

---

## 🚀 전체 실행 방법

### **1단계: Git Pull**
```bash
cd /your/project/path/novel-insight-mvp
git pull origin claude/moonpia-interview-prep-01HxdBgkUxxXm6XCVqVU2yxE
```

---

### **2단계: 전체 인프라 실행 (Docker Compose)**

```bash
# 전체 서비스 실행 (MySQL, Redis, RabbitMQ, Kafka, Zookeeper, Python)
docker compose up -d

# 또는 서비스별로 실행
docker compose up -d novel-mysql novel-redis novel-rabbitmq
docker compose up -d novel-zookeeper novel-kafka
docker compose up -d python-service
```

**컨테이너 상태 확인:**
```bash
docker compose ps
```

**예상 출력:**
```
NAME                          STATUS    PORTS
novel-mysql-container         Up        0.0.0.0:3306->3306/tcp
novel-redis-container         Up        0.0.0.0:6379->6379/tcp
novel-rabbitmq-container      Up        0.0.0.0:5672->5672/tcp, 0.0.0.0:15672->15672/tcp
novel-zookeeper-container     Up        0.0.0.0:2181->2181/tcp
novel-kafka-container         Up        0.0.0.0:9092->9092/tcp, 0.0.0.0:29092->29092/tcp
python-service-container      Up        0.0.0.0:8000->8000/tcp
```

---

### **3단계: Spring Boot 실행 (IntelliJ / VS Code)**

```bash
cd novel-insight
./gradlew clean build  # 빌드

# 실행
./gradlew bootRun
```

**정상 실행 로그:**
```
2024-12-03 14:00:00 INFO  Started NovelInsightApplicationKt in 5.0 seconds
2024-12-03 14:00:01 INFO  Kafka Producer 준비 완료!
```

---

### **4단계: Python 로그 확인 (Kafka Consumer 동작 확인)**

```bash
# Python 서비스 로그 실시간 확인
docker compose logs -f python-service
```

**정상 동작 로그:**
```
2024-12-03 14:00:05 [INFO] 🔥 Kafka Consumer 시작...
2024-12-03 14:00:06 [INFO] ✅ Kafka Consumer 연결 성공! 메시지 대기 중...
2024-12-03 14:00:07 [INFO] 🚀 Python 서비스 시작 완료!
```

---

## 🧪 전체 시스템 테스트

### **시나리오: 리뷰 작성 → Kafka → Python 분석**

#### **1. 회원가입**
```bash
curl -X POST http://localhost:8080/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "author@test.com",
    "password": "test1234!",
    "nickname": "작가1"
  }'
```

#### **2. 로그인 (JWT 발급)**
```bash
curl -X POST http://localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "author@test.com",
    "password": "test1234!"
  }'
```

**응답에서 JWT 토큰 복사:**
```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

#### **3. 소설 생성 (DB에 직접 INSERT 또는 API 구현 필요)**
```sql
-- MySQL에 직접 접속해서 테스트용 소설 생성
INSERT INTO novel (title, author, author_id, view_count, created_at)
VALUES ('테스트 소설', '작가1', 1, 0, NOW());
```

#### **4. 리뷰 작성 (Kafka로 로그 전송)**
```bash
curl -X POST http://localhost:8080/reviews \
  -H "Content-Type: application/json" \
  -d '{
    "novelId": 1,
    "content": "이 소설 진짜 재미있어요! 완전 최고!"
  }'
```

#### **5. Python 로그에서 감성 분석 결과 확인**
```bash
docker compose logs python-service | grep "감성 분석"
```

**예상 출력:**
```
2024-12-03 14:05:30 [INFO] 📩 Kafka에서 메시지 수신: {'traceId': '1a2b3c4d', ...}
2024-12-03 14:05:30 [INFO] ✨ 감성 분석 결과: {'sentiment': 'POSITIVE', 'score': 85.71, ...}
2024-12-03 14:05:30 [INFO]    - novelId: 1
2024-12-03 14:05:30 [INFO]    - authorId: 1
2024-12-03 14:05:30 [INFO]    - sentiment: POSITIVE
2024-12-03 14:05:30 [INFO]    - score: 85.71
```

---

## 🔍 개별 서비스 테스트

### **Kafka 토픽 확인**
```bash
# Kafka 컨테이너 접속
docker exec -it novel-kafka-container bash

# 토픽 목록 확인
kafka-topics --list --bootstrap-server localhost:9092

# 예상 출력:
# novel-reviews

# 메시지 실시간 확인 (Consumer 테스트)
kafka-console-consumer --bootstrap-server localhost:9092 --topic novel-reviews --from-beginning
```

### **Python 감성 분석 API 직접 호출 (테스트용)**
```bash
curl -X POST http://localhost:8000/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "text": "이 소설 너무 지루해요. 별로예요."
  }'
```

**예상 응답:**
```json
{
  "status": "SUCCESS",
  "result": "감성: NEGATIVE, 점수: 66.67",
  "detail": {
    "sentiment": "NEGATIVE",
    "score": 66.67,
    "positive_count": 0,
    "negative_count": 2
  }
}
```

---

## 📊 Spring Batch 실행 (일일 통계 집계)

```bash
# Batch Job 수동 실행 (테스트용)
curl -X POST http://localhost:8080/batch/statistics
```

---

## ⚠️ 문제 해결

### **1. Kafka 접속 실패 (Connection refused)**
```bash
# Kafka 재시작
docker compose restart novel-kafka

# 로그 확인
docker compose logs novel-kafka | grep "started"
```

### **2. Python Kafka Consumer 연결 안 됨**
```bash
# Python 컨테이너 재시작
docker compose restart python-service

# 네트워크 확인
docker network inspect novel-insight-mvp_novel-net
```

### **3. MySQL 접속 실패**
```bash
# MySQL 접속 테스트
docker exec -it novel-mysql-container mysql -u novel_user -p
# 비밀번호: novel1234!
```

---

## 🎓 학습 포인트

### **1. Kafka vs RabbitMQ 차이 체험**
- **RabbitMQ**: 알림 전송 (읽으면 삭제)
- **Kafka**: 로그 수집 (7일간 보관, 여러 Consumer가 읽기 가능)

### **2. MSA 통신 패턴**
- **동기 (Feign)**: Spring ↔ Python (분석 API 호출)
- **비동기 (Kafka)**: Spring → Kafka → Python (이벤트 스트리밍)

### **3. 실시간 데이터 파이프라인**
```
사용자 리뷰 작성
  ↓
Spring Boot (Producer)
  ↓
Kafka (메시지 저장)
  ↓
Python (Consumer + 감성 분석)
  ↓
작가에게 인사이트 제공
```

---

## 📝 다음 단계 (Optional)

1. **MySQL에 분석 결과 저장** (Python에서 INSERT)
2. **작가 대시보드 API** (Spring Boot에서 통계 조회)
3. **Kafka 파티션 분산** (처리 속도 향상)
4. **KoNLPy 실전 적용** (Okt 형태소 분석기)
5. **Grafana 대시보드** (실시간 모니터링)

---

## 🚨 주의사항

1. **Kafka는 메모리를 많이 사용**하므로 Docker Desktop 메모리를 최소 4GB 이상 할당하세요.
2. **로컬 환경에서 포트 충돌** 주의 (3306, 6379, 5672, 9092, 8000, 8080)
3. **Production 환경**에서는 반드시 `.env` 파일로 비밀번호 관리!

---

## 📚 참고 자료

- [Kafka 공식 문서](https://kafka.apache.org/documentation/)
- [Spring Kafka](https://docs.spring.io/spring-kafka/reference/html/)
- [KoNLPy 한국어 NLP](https://konlpy.org/)

---

**면접 준비 완료! 화이팅! 🔥**
