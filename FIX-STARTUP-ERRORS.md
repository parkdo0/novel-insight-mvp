# 🔧 서버 시작 에러 수정 가이드

## 🚨 발견된 문제

### 1. MySQL DateTime 마이그레이션 에러 ❌
```
Data truncation: Incorrect datetime value: '0000-00-00 00:00:00' for column 'created_at'
```

**원인**:
- Hibernate가 기존 테이블에 `created_at` 컬럼 추가 시도
- 기존 데이터에 잘못된 날짜 값 존재
- NOT NULL 제약조건 위반

**영향받는 테이블**:
- `episode`
- `member`
- `review`

### 2. Kafka 연결 실패 ⚠️
```
Connection to node -1 (localhost/127.0.0.1:9092) could not be established
```

**원인**: Kafka 서버가 실행되지 않음

---

## ✅ 해결 방법

### Step 1: MySQL 마이그레이션 실행

#### 방법 A: MySQL CLI (권장)

```bash
# 1. Docker MySQL 컨테이너 접속
docker exec -it novel-mysql-container mysql -u novel_user -p
# 비밀번호 입력: novel1234!

# 2. 마이그레이션 스크립트 실행
USE novel_insight;
source /path/to/fix-datetime-migration.sql;
```

#### 방법 B: Docker exec으로 직접 실행

```bash
# Windows
type fix-datetime-migration.sql | docker exec -i novel-mysql-container mysql -u novel_user -pnovel1234! novel_insight

# Linux/Mac
cat fix-datetime-migration.sql | docker exec -i novel-mysql-container mysql -u novel_user -pnovel1234! novel_insight
```

#### 방법 C: MySQL Workbench

1. MySQL Workbench 실행
2. 연결: `localhost:3306`, 사용자: `novel_user`, 비밀번호: `novel1234!`
3. `fix-datetime-migration.sql` 파일 열기
4. Execute (번개 아이콘) 클릭

---

### Step 2: Kafka 서비스 시작

```bash
# 1. Docker Compose로 모든 인프라 서비스 시작
cd D:\novel-insight-mvp
docker-compose up -d

# 2. Kafka 서비스 상태 확인
docker-compose ps

# 예상 출력:
# novel-kafka-container      Up
# novel-zookeeper-container  Up
# novel-mysql-container      Up
# novel-redis-container      Up
# novel-rabbitmq-container   Up
# python-service-container   Up

# 3. Kafka 로그 확인 (정상 작동 확인)
docker-compose logs -f kafka
```

---

### Step 3: Spring Boot 재시작

```bash
# IntelliJ IDEA에서 애플리케이션 재시작
# 또는 Gradle로 실행:
cd D:\novel-insight-mvp\novel-insight
gradlew bootRun
```

---

## 🎯 성공 확인

### 1. 에러 없이 시작되어야 함

```
Started NovelInsightApplicationKt in XX seconds
```

### 2. Hibernate DDL 에러 없음

```
Hibernate: alter table episode add column created_at datetime(6) not null
✅ 에러 없이 실행됨
```

### 3. Kafka 연결 성공

```
✅ "Could not configure topics" 에러 없음
```

---

## 🔍 트러블슈팅

### Q1: MySQL 컨테이너가 실행되지 않음

```bash
# MySQL 컨테이너만 재시작
docker-compose restart novel-mysql

# 로그 확인
docker-compose logs novel-mysql
```

### Q2: Kafka 연결이 여전히 실패

```bash
# Kafka와 Zookeeper 재시작
docker-compose restart zookeeper kafka

# Kafka 토픽 목록 확인
docker exec -it novel-kafka-container kafka-topics --list --bootstrap-server localhost:9092
```

### Q3: 마이그레이션 스크립트 실패

수동으로 각 명령 실행:

```sql
USE novel_insight;

-- episode 테이블만 수정
ALTER TABLE episode DROP COLUMN IF EXISTS created_at;
ALTER TABLE episode ADD COLUMN created_at DATETIME NULL;
UPDATE episode SET created_at = NOW() WHERE created_at IS NULL;
ALTER TABLE episode MODIFY COLUMN created_at DATETIME NOT NULL;

-- 나머지 테이블도 동일하게...
```

---

## 📊 데이터 확인

마이그레이션 후 데이터 확인:

```sql
-- 각 테이블의 created_at 확인
SELECT COUNT(*), MIN(created_at), MAX(created_at) FROM episode;
SELECT COUNT(*), MIN(created_at), MAX(created_at) FROM member;
SELECT COUNT(*), MIN(created_at), MAX(created_at) FROM review;
```

---

## 🚀 빠른 해결 (요약)

```bash
# 1. MySQL 마이그레이션 (Windows)
type fix-datetime-migration.sql | docker exec -i novel-mysql-container mysql -u novel_user -pnovel1234! novel_insight

# 2. Kafka 시작
docker-compose up -d

# 3. Spring Boot 재시작
# IntelliJ에서 재시작 또는:
cd novel-insight && gradlew bootRun
```

---

## ⚠️ 주의사항

1. **프로덕션 환경**: 이 스크립트는 개발용입니다. 프로덕션에서는 백업 필수!
2. **데이터 손실 없음**: 기존 데이터를 삭제하지 않고 날짜만 업데이트합니다.
3. **DDL AUTO**: `application.yaml`의 `ddl-auto: update` 설정 유지 가능

---

## 📝 향후 방지 방법

### 옵션 1: Flyway/Liquibase 사용 (권장)

```gradle
// build.gradle.kts
implementation("org.flywaydb:flyway-core")
implementation("org.flywaydb:flyway-mysql")
```

### 옵션 2: nullable로 컬럼 정의

```kotlin
@Column(nullable = true)  // 처음에는 nullable
var createdAt: LocalDateTime? = null
```

---

## ✅ 완료 체크리스트

- [ ] `fix-datetime-migration.sql` 실행 완료
- [ ] Docker Compose 서비스 모두 Up 상태
- [ ] Spring Boot 애플리케이션 에러 없이 시작
- [ ] Kafka 연결 경고 없음
- [ ] MySQL datetime 에러 없음
- [ ] Actuator 헬스 체크 성공: `curl http://localhost:8080/actuator/health`

---

## 📞 추가 도움

문제가 계속되면 다음 정보와 함께 문의:

1. `docker-compose ps` 출력
2. `docker-compose logs <service-name>` 출력
3. Spring Boot 전체 로그
4. MySQL 버전: `docker exec novel-mysql-container mysql --version`
