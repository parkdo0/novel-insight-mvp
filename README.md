# Novel Insight MVP

> 소설 플랫폼 토이 프로젝트

## 프로젝트 소개

웹소설 플랫폼을 구현한 풀스택 토이 프로젝트입니다.

**학습 목적**으로 다양한 기술 스택과 아키텍처 패턴을 직접 적용해보기 위해 진행했습니다.

### AI 활용 안내

이 프로젝트는 **AI(Claude)와 함께 개발**했습니다.
- 코드 작성, 아키텍처 설계, 문제 해결 과정에서 AI의 도움을 받았습니다
- 학습과 실험을 목적으로 여러 기술을 폭넓게 적용해봤습니다

---

## 기술 스택

### Backend (Kotlin + Spring Boot)
- **Framework**: Spring Boot 3.4, Kotlin
- **ORM**: Spring Data JPA, QueryDSL
- **Security**: Spring Security, JWT 인증
- **Cache**: Redis (Redisson 분산 락)
- **Message Queue**: RabbitMQ
- **Batch**: Spring Batch
- **External API**: OpenFeign (Python 서비스 연동)
- **Database**: MySQL 8.0

### Frontend (Vue.js)
- **Framework**: Vue.js 3, Composition API
- **Build Tool**: Vite
- **State Management**: Pinia
- **Language**: TypeScript
- **HTTP Client**: Axios

### Python Service (FastAPI)
- **Framework**: FastAPI
- 텍스트 분석 API 담당

### Infrastructure
- **Container**: Docker, Docker Compose
- **Services**: MySQL, Redis, RabbitMQ

---

## 프로젝트 구조

```
novel-insight-mvp/
├── novel-insight/          # Spring Boot 백엔드
│   └── src/main/kotlin/
│       ├── controller/     # REST API 컨트롤러
│       ├── service/        # 비즈니스 로직
│       ├── repository/     # 데이터 접근 계층
│       ├── domain/         # 엔티티
│       ├── dto/            # 요청/응답 DTO
│       ├── config/         # 설정 클래스
│       ├── security/       # 보안 관련
│       ├── batch/          # 배치 작업
│       └── client/         # 외부 API 클라이언트
├── novel-insight-front/    # Vue.js 프론트엔드
│   └── src/
│       ├── views/          # 페이지 컴포넌트
│       ├── components/     # 재사용 컴포넌트
│       ├── api/            # API 호출
│       ├── stores/         # Pinia 스토어
│       └── router/         # 라우팅
├── python-service/         # FastAPI 서비스
└── docker-compose.yml      # 인프라 설정
```

---

## 주요 기능

### 회원 시스템
- 회원가입/로그인 (JWT 기반 인증)
- 마이페이지

### 소설 서비스
- 소설 목록 조회 (검색, 장르 필터링)
- 소설 상세 정보
- 에피소드 목록/뷰어

### 결제 & 포인트
- 포인트 충전
- 에피소드 구매 (분산 락 적용)
- 구매 내역 조회

### 리뷰 시스템
- 리뷰 작성/조회
- 별점 시스템

### 기타
- RabbitMQ 기반 비동기 알림
- Spring Batch 통계 집계
- Python 서비스 연동 (텍스트 분석)

---

## 실행 방법

### 1. 인프라 실행 (Docker)
```bash
docker-compose up -d
```

### 2. 백엔드 실행
```bash
cd novel-insight
./gradlew bootRun
```

### 3. 프론트엔드 실행
```bash
cd novel-insight-front
npm install
npm run dev
```

### 4. Python 서비스 실행
```bash
cd python-service
pip install fastapi uvicorn pydantic
uvicorn main:app --reload --port 8001
```

---

## 환경 변수

`.env` 파일 생성 필요:
```
# Database
MYSQL_ROOT_PASSWORD=root1234!
MYSQL_DATABASE=novel_insight
MYSQL_USER=novel_user
MYSQL_PASSWORD=novel1234!

# JWT
JWT_SECRET=your-secret-key
```

---

## 학습 포인트

이 프로젝트를 통해 다음 내용을 학습/적용했습니다:

- **Spring Security + JWT**: 토큰 기반 인증 구현
- **QueryDSL**: 동적 쿼리 작성
- **Redisson**: 분산 락을 이용한 동시성 제어
- **RabbitMQ**: 메시지 큐 기반 비동기 처리
- **Spring Batch**: 대용량 데이터 배치 처리
- **OpenFeign**: 마이크로서비스 간 통신
- **Vue 3 Composition API**: 모던 Vue 개발 패턴
- **Pinia**: Vue 상태 관리
- **Docker Compose**: 멀티 컨테이너 환경 구성

---

## 참고

- 토이 프로젝트로, 실제 서비스 수준의 완성도는 아닙니다
- 학습 목적으로 다양한 기술을 넣어봤기 때문에 오버엔지니어링된 부분이 있습니다
