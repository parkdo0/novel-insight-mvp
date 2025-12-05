# Novel Insight MVP - 인프라 설정 가이드

이 문서는 Novel Insight 프로젝트의 **환경 설정, 보안, 배포**에 대한 종합 가이드입니다.

---

## 📋 목차

1. [보안 및 환경 변수 관리](#보안-및-환경-변수-관리)
2. [로컬 개발 환경 설정](#로컬-개발-환경-설정)
3. [Docker 환경 설정](#docker-환경-설정)
4. [프로덕션 배포 준비](#프로덕션-배포-준비)
5. [Git 보안 체크리스트](#git-보안-체크리스트)
6. [트러블슈팅](#트러블슈팅)

---

## 보안 및 환경 변수 관리

### 🔐 중요: Git에 절대 커밋하면 안되는 파일

```
# 민감 정보가 포함된 파일들 (이미 .gitignore에 포함됨)
.env
.env.local
.env.*.local
*.env

# 각 서비스의 환경 변수 파일
/novel-insight/.env
/novel-insight-front/.env.development
/novel-insight-front/.env.production
/python-service/.env

# 데이터 볼륨
/data/
/volumes/
```

### 환경 변수 파일 구조

#### 1. Docker Compose 환경 변수 (루트 디렉토리)

```bash
# 위치: /.env
# 용도: Docker Compose 인프라 설정
# 포함 내용: MySQL, RabbitMQ 비밀번호
```

**설정 방법**:
```bash
# 1. 템플릿 복사
cp .env.example .env

# 2. 실제 비밀번호로 수정
vi .env  # 또는 nano, code 등
```

#### 2. 백엔드 Spring Boot 환경 변수

```bash
# 위치: /novel-insight/.env
# 용도: Spring Boot 애플리케이션 설정
# 포함 내용: DB 비밀번호, JWT Secret
```

**설정 방법**:
```bash
cd novel-insight
cp .env.example .env
vi .env

# 반드시 수정해야 할 항목:
# - DB_PASSWORD: MySQL 비밀번호
# - JWT_SECRET: JWT 서명 키 (최소 32자)
```

**JWT Secret 생성 방법**:
```bash
# OpenSSL 사용 (권장)
openssl rand -base64 32

# 또는 Node.js 사용
node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"
```

#### 3. 프론트엔드 Vue.js 환경 변수

```bash
# 위치: /novel-insight-front/.env.development
# 용도: Vite 빌드 설정 (개발 환경)
# 포함 내용: API URL, 기능 플래그
```

**주의사항**:
⚠️ Vite 환경 변수는 **빌드 시점에 번들에 포함**되므로 민감 정보를 넣지 마세요!
- ✅ API URL, 타임아웃, 기능 플래그
- ❌ API 키, 비밀번호, 토큰

#### 4. Python 분석 서비스 환경 변수

```bash
# 위치: /python-service/.env
# 용도: FastAPI 애플리케이션 설정
# 포함 내용: DB 비밀번호, Kafka 설정
```

---

## 로컬 개발 환경 설정

### 1. 사전 요구사항

```bash
# 필수 도구 설치 확인
docker --version          # Docker 20.10+
docker-compose --version  # Docker Compose 2.0+
java -version            # JDK 17+
node --version           # Node.js 18+
python --version         # Python 3.11+
```

### 2. 환경 변수 파일 준비

```bash
# 1. 루트 디렉토리에서 Docker 환경 변수 설정
cp .env.example .env
vi .env

# 2. 백엔드 환경 변수 설정
cd novel-insight
cp .env.example .env
vi .env
cd ..

# 3. 프론트엔드 환경 변수 설정
cd novel-insight-front
cp .env.example .env.development
vi .env.development
cd ..

# 4. Python 서비스 환경 변수 설정
cd python-service
cp .env.example .env
vi .env
cd ..
```

### 3. Docker 인프라 시작

```bash
# 프로젝트 루트 디렉토리에서 실행
docker-compose up -d

# 실행 확인
docker-compose ps

# 로그 확인
docker-compose logs -f  # Ctrl+C로 종료
```

**포트 확인**:
- MySQL: `localhost:3306`
- Redis: `localhost:6379`
- RabbitMQ AMQP: `localhost:5672`
- RabbitMQ Management: `http://localhost:15672` (guest/guest)
- Kafka: `localhost:9092`
- Python Service: `http://localhost:8000`

### 4. 백엔드 실행

```bash
cd novel-insight

# Gradle 빌드
./gradlew clean build

# 애플리케이션 실행
./gradlew bootRun

# 또는 JAR 실행
java -jar build/libs/novel-insight-0.0.1-SNAPSHOT.jar
```

**확인**:
- API: `http://localhost:8080`
- Health Check: `http://localhost:8080/actuator/health`

### 5. 프론트엔드 실행

```bash
cd novel-insight-front

# 의존성 설치
npm install

# 개발 서버 실행
npm run dev
```

**확인**:
- UI: `http://localhost:5173`

### 6. Python 서비스 실행

```bash
cd python-service

# 가상환경 생성 (처음 한 번만)
python -m venv venv

# 가상환경 활성화
# Windows
venv\Scripts\activate
# Mac/Linux
source venv/bin/activate

# 의존성 설치
pip install -r requirements.txt

# 애플리케이션 실행
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

**확인**:
- API: `http://localhost:8000`
- Swagger Docs: `http://localhost:8000/docs`

---

## Docker 환경 설정

### Docker Compose 서비스 구성

```yaml
services:
  novel-mysql      # MySQL 8.0 데이터베이스
  novel-redis      # Redis (캐싱, 분산 락)
  novel-rabbitmq   # RabbitMQ (실시간 알림)
  novel-zookeeper  # Kafka 브로커 관리
  novel-kafka      # Kafka 이벤트 스트리밍
  python-service   # FastAPI 감정 분석 서비스
```

### 자주 사용하는 Docker 명령어

```bash
# 모든 서비스 시작 (백그라운드)
docker-compose up -d

# 특정 서비스만 시작
docker-compose up -d novel-mysql novel-redis

# 서비스 재시작
docker-compose restart novel-kafka

# 로그 확인 (실시간)
docker-compose logs -f python-service

# 컨테이너 내부 접속
docker-compose exec novel-mysql bash
docker-compose exec python-service /bin/sh

# 모든 서비스 중지
docker-compose down

# 모든 서비스 및 볼륨 삭제 (주의!)
docker-compose down -v
```

### Docker 네트워크 구성

```
┌─────────────────────────────────────────────┐
│  Docker Network: novel-net (Bridge)         │
│                                             │
│  ┌─────────┐  ┌─────────┐  ┌──────────┐  │
│  │  MySQL  │  │  Redis  │  │ RabbitMQ │  │
│  └─────────┘  └─────────┘  └──────────┘  │
│       ↑            ↑             ↑         │
│       └────────────┴─────────────┘         │
│                    │                       │
│          ┌─────────┴──────────┐           │
│          │  Spring Boot App   │           │
│          │  (호스트에서 실행)   │           │
│          └────────────────────┘           │
└─────────────────────────────────────────────┘
```

**특징**:
- 같은 네트워크의 컨테이너는 **서비스 이름으로 통신**
  - 예: `jdbc:mysql://novel-mysql:3306/novel_insight`
- 호스트에서 컨테이너 접근: `localhost:포트번호`
  - 예: `jdbc:mysql://localhost:3306/novel_insight`

---

## 프로덕션 배포 준비

### 1. 환경 변수 분리

**개발/스테이징/프로덕션 환경별 파일 관리**:

```bash
# 개발 환경
.env.development

# 스테이징 환경
.env.staging

# 프로덕션 환경
.env.production
```

**프로덕션 체크리스트**:
- [ ] 모든 비밀번호를 강력한 랜덤 문자열로 변경
- [ ] JWT Secret을 최소 64자 이상으로 설정
- [ ] DB 사용자 권한을 최소화 (root 사용 금지)
- [ ] `show-sql: false`로 설정 (SQL 로그 비활성화)
- [ ] `ddl-auto: validate` 또는 `none`으로 설정
- [ ] HTTPS 사용 (Let's Encrypt, AWS Certificate Manager)
- [ ] CORS 허용 도메인 명시
- [ ] Rate Limiting 설정
- [ ] 로그 레벨을 `INFO` 또는 `WARN`으로 설정

### 2. 프로덕션용 application.yaml

```yaml
# application-prod.yaml (별도 파일로 관리)
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

  jpa:
    show-sql: false  # 프로덕션: SQL 로그 비활성화
    hibernate:
      ddl-auto: validate  # 프로덕션: 스키마 검증만

logging:
  level:
    root: INFO
    com.sparta.novel_insight: WARN
```

**실행 방법**:
```bash
# 프로파일 지정 실행
java -jar app.jar --spring.profiles.active=prod

# 또는 환경 변수로 설정
export SPRING_PROFILES_ACTIVE=prod
java -jar app.jar
```

### 3. Docker 프로덕션 배포

**docker-compose.prod.yml** (프로덕션용):
```yaml
version: '3.8'

services:
  backend:
    image: your-registry/novel-insight-backend:latest
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_URL: ${DB_URL}
      DB_USERNAME: ${DB_USERNAME}
      DB_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
    restart: always
    deploy:
      replicas: 3  # 고가용성
      resources:
        limits:
          cpus: '1'
          memory: 1G
```

**실행**:
```bash
docker-compose -f docker-compose.prod.yml up -d
```

### 4. CI/CD 파이프라인

**GitHub Actions 예시** (`.github/workflows/deploy.yml`):

```yaml
name: Deploy to Production

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Build Docker Image
        run: docker build -t novel-insight:${{ github.sha }} .

      - name: Deploy to Server
        env:
          DB_PASSWORD: ${{ secrets.DB_PASSWORD }}
          JWT_SECRET: ${{ secrets.JWT_SECRET }}
        run: |
          # 배포 스크립트 실행
```

**GitHub Secrets 설정**:
1. GitHub Repository → Settings → Secrets and variables → Actions
2. New repository secret 클릭
3. 민감 정보 등록:
   - `DB_PASSWORD`
   - `JWT_SECRET`
   - `MYSQL_ROOT_PASSWORD`

---

## Git 보안 체크리스트

### 커밋 전 확인사항

```bash
# 1. .gitignore 확인
cat .gitignore | grep .env

# 2. 커밋 대상 파일 확인 (민감 정보 포함 여부)
git status

# 3. 변경 내용 검토
git diff

# 4. .env 파일이 추적되지 않는지 확인
git ls-files | grep .env  # 출력 없어야 정상
```

### 만약 .env 파일을 실수로 커밋했다면?

```bash
# ⚠️ 주의: 이미 push된 경우 히스토리에 남아있으므로
# 비밀번호를 즉시 변경하고 다음 조치를 취하세요

# 1. 최근 커밋 취소 (아직 push 안했을 때)
git reset --soft HEAD~1

# 2. 특정 파일만 언스테이징
git restore --staged .env

# 3. Git 히스토리에서 완전 삭제 (신중하게!)
git filter-branch --force --index-filter \
  "git rm --cached --ignore-unmatch .env" \
  --prune-empty --tag-name-filter cat -- --all

# 4. 강제 push (협업 시 팀원에게 알려야 함!)
git push origin --force --all
```

### Private Repository를 Public으로 전환할 때

1. **민감 정보가 Git 히스토리에 없는지 확인**:
```bash
# 모든 커밋에서 "password" 검색
git log -S"password" --all

# 모든 커밋에서 파일 검색
git log --all --full-history -- "*.env"
```

2. **발견된 경우 히스토리 정리** (위 filter-branch 명령어 사용)

3. **새 저장소로 클린 마이그레이션** (권장):
```bash
# 1. 새 저장소 생성
mkdir novel-insight-public
cd novel-insight-public
git init

# 2. 현재 코드만 복사 (히스토리 제외)
cp -r ../novel-insight-mvp/* .

# 3. 커밋
git add .
git commit -m "Initial commit"

# 4. Public 저장소로 push
git remote add origin https://github.com/username/novel-insight-public.git
git push -u origin main
```

---

## 트러블슈팅

### 1. Docker Compose 서비스가 시작되지 않을 때

```bash
# 로그 확인
docker-compose logs novel-mysql

# 포트 충돌 확인
lsof -i :3306  # Mac/Linux
netstat -ano | findstr :3306  # Windows

# 컨테이너 재생성
docker-compose down
docker-compose up -d --force-recreate
```

### 2. Spring Boot 환경 변수를 못 읽을 때

```bash
# 환경 변수 확인
printenv | grep DB_  # Mac/Linux
set | findstr DB_    # Windows

# IntelliJ Run Configuration에서 환경 변수 설정
# Edit Configurations → Environment variables → Add

# 또는 .env 파일 위치 확인
ls -la novel-insight/.env
```

### 3. Python 서비스가 Kafka에 연결 못할 때

```bash
# Kafka 상태 확인
docker-compose logs novel-kafka

# Python 서비스 환경 변수 확인
docker-compose exec python-service env | grep KAFKA

# 네트워크 연결 테스트
docker-compose exec python-service ping novel-kafka
```

### 4. Frontend 빌드 시 환경 변수가 안 들어갈 때

```bash
# Vite 환경 변수는 반드시 VITE_ 접두사 필요
# ❌ API_BASE_URL=http://localhost:8080
# ✅ VITE_API_BASE_URL=http://localhost:8080

# 빌드 시 환경 변수 확인
npm run build -- --mode development

# .env 파일 위치 확인 (프로젝트 루트에 있어야 함)
ls -la novel-insight-front/.env.development
```

---

## 부록: 환경별 설정 요약

| 구분 | 로컬 개발 | Docker | 스테이징 | 프로덕션 |
|------|-----------|--------|----------|----------|
| **DB Host** | localhost | novel-mysql | db.staging.com | db.prod.com |
| **show-sql** | true | true | false | false |
| **ddl-auto** | update | update | validate | validate |
| **Log Level** | DEBUG | INFO | INFO | WARN |
| **HTTPS** | ❌ | ❌ | ✅ | ✅ |
| **CORS** | * | * | 특정 도메인 | 특정 도메인 |

---

## 참고 자료

- [Spring Boot 환경 변수](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [Docker Compose 환경 변수](https://docs.docker.com/compose/environment-variables/)
- [Vite 환경 변수](https://vitejs.dev/guide/env-and-mode.html)
- [Pydantic Settings](https://docs.pydantic.dev/latest/concepts/pydantic_settings/)
- [12 Factor App](https://12factor.net/ko/)

---

## 연락처

문제가 발생하거나 질문이 있으시면 이슈를 등록해주세요.
