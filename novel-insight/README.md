# Novel Insight - 백엔드 서비스

웹소설 리뷰 감정 분석 및 인사이트 제공 플랫폼의 백엔드 서비스입니다.

---

## 📋 목차

1. [프로젝트 개요](#프로젝트-개요)
2. [프로젝트 구조](#프로젝트-구조)
3. [기술 스택](#기술-스택)
4. [핵심 개념 (5-6년차 필수)](#핵심-개념-5-6년차-필수)
5. [개발 패턴 및 아키텍처](#개발-패턴-및-아키텍처)
6. [환경 설정 및 실행](#환경-설정-및-실행)

---

## 프로젝트 개요

Novel Insight는 **웹소설 플랫폼에서 발생하는 대량의 리뷰 데이터를 실시간으로 분석**하여 작가와 독자에게 유용한 인사이트를 제공하는 MSA 기반 서비스입니다.

### 주요 기능

- **회원 관리**: JWT 기반 인증/인가 시스템
- **웹소설/에피소드 관리**: CRUD + 동적 검색 (QueryDSL)
- **포인트 시스템**: 분산 락을 활용한 동시성 제어
- **리뷰 감정 분석**: Python 서비스와 연동 (Feign Client)
- **실시간 알림**: RabbitMQ 기반 비동기 메시징
- **데이터 파이프라인**: Kafka를 통한 이벤트 스트리밍

---

## 프로젝트 구조

```
src/main/kotlin/com/sparta/novel_insight/
├── NovelInsightApplication.kt          # Spring Boot 애플리케이션 진입점
├── batch/                              # Spring Batch 작업
│   └── ReviewAnalysisJobConfig.kt      # 리뷰 분석 배치 작업
├── client/                             # 외부 서비스 연동
│   └── PythonClient.kt                 # Feign Client (Python 서비스 호출)
├── common/                             # 공통 컴포넌트
│   └── MDCLoggingFilter.kt             # 분산 추적용 Filter (TraceId)
├── config/                             # 설정 클래스
│   ├── QueryDslConfig.kt               # QueryDSL 설정
│   ├── SecurityConfig.kt               # Spring Security + JWT
│   ├── KafkaConfig.kt                  # Kafka Producer/Consumer
│   └── RedisConfig.kt                  # Redis/Redisson 설정
├── controller/                         # REST API 컨트롤러
│   ├── AuthController.kt               # 인증 (로그인/회원가입)
│   ├── NovelController.kt              # 소설 CRUD
│   ├── EpisodeController.kt            # 에피소드 CRUD
│   ├── PurchaseController.kt           # 포인트 결제
│   ├── ReviewController.kt             # 리뷰 작성/조회
│   ├── AnalysisController.kt           # 감정 분석 API
│   └── InsightController.kt            # 인사이트 조회
├── domain/                             # JPA 엔티티
│   ├── Member.kt                       # 회원
│   ├── Novel.kt                        # 소설
│   ├── Episode.kt                      # 에피소드
│   ├── Review.kt                       # 리뷰
│   ├── Purchase.kt                     # 구매 이력
│   ├── Point.kt                        # 포인트 지갑
│   └── ReviewAnalysis.kt               # 리뷰 분석 결과
├── dto/                                # 데이터 전송 객체
│   ├── request/                        # 요청 DTO
│   └── response/                       # 응답 DTO
├── repository/                         # 데이터 접근 계층
│   ├── MemberRepository.kt
│   ├── NovelRepository.kt
│   ├── NovelCustomRepository.kt        # QueryDSL 커스텀 인터페이스
│   └── NovelRepositoryImpl.kt          # QueryDSL 구현체
├── security/                           # 보안 관련
│   ├── JwtTokenProvider.kt             # JWT 생성/검증
│   └── JwtAuthenticationFilter.kt      # JWT 인증 필터
└── service/                            # 비즈니스 로직
    ├── AuthService.kt                  # 인증 서비스
    ├── NovelService.kt                 # 소설 서비스
    ├── PurchaseService.kt              # 구매 서비스 (분산 락)
    ├── ReviewService.kt                # 리뷰 서비스
    ├── InsightService.kt               # 인사이트 서비스
    ├── RabbitProducer.kt               # RabbitMQ 메시지 발행
    ├── RabbitConsumer.kt               # RabbitMQ 메시지 소비
    └── KafkaProducer.kt                # Kafka 이벤트 발행
```

### 패키지 설계 원칙

- **계층형 아키텍처**: Controller → Service → Repository → Domain
- **단방향 의존성**: 상위 계층이 하위 계층을 참조 (역방향 의존 금지)
- **관심사 분리**: 각 패키지는 명확한 책임을 가짐
- **인터페이스 분리**: Repository는 interface로 추상화 (Spring Data JPA)

---

## 기술 스택

### 핵심 기술

| 기술 | 버전 | 용도 | 비고 |
|------|------|------|------|
| **Kotlin** | 1.9.25 | 주 언어 | Null Safety, Data Class, Extension Functions |
| **Spring Boot** | 3.4.12 | 프레임워크 | 최신 Jakarta EE 9 기반 |
| **JDK** | 17 | 런타임 | LTS 버전 |
| **Gradle** | Kotlin DSL | 빌드 도구 | build.gradle.kts |

### 데이터베이스 & ORM

| 기술 | 용도 | 설명 |
|------|------|------|
| **MySQL** | 주 데이터베이스 | 트랜잭션 보장, ACID 준수 |
| **JPA/Hibernate** | ORM | 객체-관계 매핑, Lazy Loading |
| **QueryDSL** | 동적 쿼리 | 타입 세이프한 쿼리 빌더 (Q클래스) |
| **Spring Data JPA** | Repository 추상화 | 보일러플레이트 제거 |

### 메시징 & 이벤트

| 기술 | 용도 | 특징 |
|------|------|------|
| **Kafka** | 이벤트 스트리밍 | 대용량 데이터 파이프라인, 영속성 보장 |
| **RabbitMQ** | 메시지 큐 | 실시간 알림, Exchange/Queue 라우팅 |

### 캐싱 & 분산 락

| 기술 | 용도 | 특징 |
|------|------|------|
| **Redis** | 인메모리 캐시 | Session, Cache, Pub/Sub |
| **Redisson** | 분산 락 | 동시성 제어 (Lock, Semaphore) |

### 보안 & 인증

| 기술 | 용도 | 설명 |
|------|------|------|
| **Spring Security** | 인증/인가 | Filter Chain 기반 보안 |
| **JWT** | 토큰 인증 | Stateless 인증, JJWT 라이브러리 |

### MSA & 통신

| 기술 | 용도 | 설명 |
|------|------|------|
| **Spring Cloud OpenFeign** | HTTP Client | 선언적 REST 클라이언트 |
| **Spring Batch** | 배치 처리 | 대용량 데이터 일괄 처리 |

### 모니터링

| 기술 | 용도 | 설명 |
|------|------|------|
| **Spring Actuator** | 헬스체크 | /actuator/health 엔드포인트 |
| **MDC (SLF4J)** | 분산 추적 | TraceId 기반 로그 추적 |

---

## 핵심 개념 (5-6년차 필수)

이 섹션은 **5-6년차 백엔드 개발자가 반드시 알아야 할 개념**을 프로젝트 코드와 함께 설명합니다.

### 1. IoC (Inversion of Control) & DI (Dependency Injection)

**개념**: 객체의 생성과 의존성 주입을 Spring 컨테이너가 담당

**실무 적용 예시**:
```kotlin
@Service  // IoC: Spring이 이 클래스를 Bean으로 등록
class PurchaseService(
    // DI: 생성자 주입 (권장 방식)
    private val pointRepository: PointRepository,
    private val episodeRepository: EpisodeRepository,
    private val redissonClient: RedissonClient
) {
    // 비즈니스 로직...
}
```

**왜 생성자 주입인가?**
- **불변성 보장**: `val`로 선언 가능 (런타임 중 변경 불가)
- **순환 참조 방지**: 컴파일 타임에 순환 참조 감지
- **테스트 용이성**: Mock 객체 주입이 간편

**참고 파일**:
- `service/PurchaseService.kt`
- `service/RabbitProducer.kt`

---

### 2. AOP (Aspect-Oriented Programming)

**개념**: 횡단 관심사(Cross-Cutting Concerns)를 모듈화하여 코드 중복 제거

**프로젝트 내 AOP 적용 사례**:

#### (1) @Transactional - 트랜잭션 관리
```kotlin
@Transactional(
    propagation = Propagation.REQUIRED,      // 트랜잭션 전파 방식
    isolation = Isolation.REPEATABLE_READ,   // 격리 수준
    timeout = 10,                            // 타임아웃 (초)
    rollbackFor = [Exception::class]         // 롤백 대상 예외
)
fun purchaseEpisode(userId: Long, episodeId: Long) {
    // 트랜잭션 내에서 실행
}
```

**동작 원리**:
1. Spring이 CGLIB를 사용하여 프록시 객체 생성
2. 프록시가 메서드 호출을 가로채서 트랜잭션 시작
3. 실제 메서드 실행
4. 성공 시 Commit, 예외 발생 시 Rollback

**참고 파일**: `service/PurchaseService.kt:50-80`

#### (2) @RabbitListener - 메시지 소비
```kotlin
@RabbitListener(queues = ["novel.notification"])  // AOP 기반 어노테이션
fun receiveMessage(message: String) {
    // 메시지 처리 로직
}
```

**동작 원리**:
- Spring AOP가 프록시 생성
- Before: 메시지 수신 전 로깅
- After: 메시지 처리 후 ACK 전송
- AfterThrowing: 예외 발생 시 Nack + Requeue

**참고 파일**: `service/RabbitConsumer.kt:30-45`

---

### 3. 트랜잭션 격리 수준 (Isolation Level)

**왜 중요한가?**
동시성 환경에서 데이터 일관성을 보장하기 위해 반드시 이해해야 함

| 격리 수준 | Dirty Read | Non-Repeatable Read | Phantom Read |
|-----------|------------|---------------------|--------------|
| READ_UNCOMMITTED | O | O | O |
| READ_COMMITTED | X | O | O |
| **REPEATABLE_READ** | X | X | O (InnoDB는 X) |
| SERIALIZABLE | X | X | X |

**프로젝트 선택**: `REPEATABLE_READ`

**이유**:
- MySQL InnoDB의 기본값
- MVCC(Multi-Version Concurrency Control)로 Phantom Read 방지
- 성능과 일관성의 균형점

**실무 적용**:
```kotlin
@Transactional(isolation = Isolation.REPEATABLE_READ)
fun purchaseEpisode(userId: Long, episodeId: Long) {
    // 트랜잭션 시작 시점의 스냅샷으로 읽기
    val point = pointRepository.findByMemberId(userId)
    // 다른 트랜잭션이 변경해도 이 트랜잭션에서는 같은 값을 읽음
}
```

**참고 파일**: `service/PurchaseService.kt:50`

---

### 4. Dirty Checking (변경 감지)

**개념**: JPA 영속성 컨텍스트가 엔티티 변경을 자동으로 감지하여 UPDATE 쿼리 실행

**실무 적용**:
```kotlin
@Transactional
fun purchaseEpisode(userId: Long, episodeId: Long) {
    val point = pointRepository.findByMemberId(userId)  // SELECT

    point.amount -= 100  // 단순 필드 변경

    // pointRepository.save(point) 불필요!
    // 트랜잭션 커밋 시점에 자동으로 UPDATE 실행
}
```

**동작 원리**:
1. `findByMemberId()` 실행 시 엔티티가 **영속 상태**로 1차 캐시에 저장
2. JPA가 **스냅샷** 생성 (최초 조회 상태)
3. 트랜잭션 커밋 시점에 스냅샷과 현재 엔티티 비교
4. 변경 사항이 있으면 UPDATE 쿼리 자동 생성

**주의사항**:
- `@Transactional` 없으면 작동 안함!
- 영속 상태 엔티티만 가능 (준영속/비영속 상태는 불가)

**참고 파일**: `service/PurchaseService.kt:70`

---

### 5. QueryDSL - 타입 세이프 동적 쿼리

**왜 필요한가?**
- JPQL 문자열: 컴파일 타임 오류 검증 불가
- Criteria API: 너무 복잡하고 가독성 나쁨
- QueryDSL: 타입 안전 + 가독성 + IDE 자동완성

**Q클래스 생성 원리**:
```kotlin
// build.gradle.kts
kapt("com.querydsl:querydsl-apt:5.0.0:jakarta")

// 빌드 시 Q클래스 자동 생성
// Novel.kt → QNovel.java (build/generated/source/kapt)
```

**동적 쿼리 패턴 (BooleanExpression)**:
```kotlin
class NovelRepositoryImpl(
    private val queryFactory: JPAQueryFactory
) : NovelCustomRepository {

    override fun search(title: String?, author: String?, pageable: Pageable): Page<Novel> {
        val novel = QNovel.novel

        val content = queryFactory
            .selectFrom(novel)
            .where(
                eqTitle(title),    // null이면 조건 무시
                eqAuthor(author)   // null이면 조건 무시
            )
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()

        return PageImpl(content, pageable, countQuery())
    }

    // BooleanExpression 반환 (null 허용)
    private fun eqTitle(title: String?): BooleanExpression? {
        if (title.isNullOrEmpty()) return null  // null 반환 시 조건 무시
        return QNovel.novel.title.contains(title)
    }
}
```

**핵심 포인트**:
- `BooleanExpression?` 반환 (nullable)
- `null` 반환 시 WHERE 조건에서 자동으로 제외됨
- 동적 쿼리를 깔끔하게 조합 가능

**참고 파일**:
- `config/QueryDslConfig.kt`
- `repository/NovelRepositoryImpl.kt`

---

### 6. 분산 락 (Distributed Lock)

**왜 필요한가?**
MSA 환경에서 여러 인스턴스가 동시에 같은 자원에 접근할 때 동시성 제어 필요

**비관적 락의 한계**:
```kotlin
// 단일 DB 환경에서만 작동
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Point p WHERE p.memberId = :memberId")
fun findByMemberIdWithLock(memberId: Long): Point?
```
- 문제: 여러 DB 샤드가 있으면 작동 안함
- 문제: 애플리케이션 레벨 동시성 제어 불가

**Redisson 분산 락**:
```kotlin
@Service
class PurchaseService(
    private val redissonClient: RedissonClient
) {
    fun purchaseEpisode(userId: Long, episodeId: Long) {
        val lockKey = "purchase:user:$userId"
        val lock = redissonClient.getLock(lockKey)

        try {
            // 락 획득 시도 (최대 5초 대기, 10초 후 자동 해제)
            if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                // 임계 영역: 동시 접근 방지
                processPayment(userId, episodeId)
            } else {
                throw RuntimeException("다른 결제가 진행 중입니다")
            }
        } finally {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()  // 락 해제
            }
        }
    }
}
```

**Redisson vs Lettuce**:
- Lettuce: `SETNX` 직접 구현 필요 (spin lock 문제)
- Redisson: Pub/Sub 기반 대기 (CPU 효율적)

**참고 파일**: `service/PurchaseService.kt:90-120`

---

### 7. Filter vs Interceptor vs AOP

**실행 시점과 용도 차이**

| 구분 | 실행 시점 | 용도 | 예시 |
|------|-----------|------|------|
| **Filter** | DispatcherServlet 전/후 | 요청/응답 변환, 인코딩, 보안 | MDC TraceId, CORS |
| **Interceptor** | Controller 전/후 | 인증/인가, 로깅 | JWT 검증, 권한 체크 |
| **AOP** | 메서드 실행 전/후 | 트랜잭션, 캐싱, 로깅 | @Transactional |

**왜 MDC는 Filter인가?**

MDC(Mapped Diagnostic Context)는 **모든 요청에 TraceId를 부여**하여 분산 추적을 가능하게 함.

```kotlin
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)  // 가장 먼저 실행
class MDCLoggingFilter : Filter {
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val traceId = UUID.randomUUID().toString().substring(0, 8)
        MDC.put("traceId", traceId)  // ThreadLocal에 저장

        try {
            chain.doFilter(request, response)  // 다음 필터 체인 실행
        } finally {
            MDC.clear()  // ThreadLocal 정리 (메모리 누수 방지)
        }
    }
}
```

**왜 Interceptor가 아닌 Filter인가?**
- Filter: DispatcherServlet 이전에 실행 → **모든 요청**에 TraceId 부여 가능
- Interceptor: Controller 실행 전 → 정적 리소스(/css, /js)는 제외됨

**ThreadLocal 메모리 누수 주의**:
- Tomcat은 Thread Pool 재사용
- `MDC.clear()` 누락 시 이전 요청의 TraceId가 다음 요청에 영향

**참고 파일**: `common/MDCLoggingFilter.kt`

---

### 8. Kafka vs RabbitMQ

**언제 Kafka를 쓰고, 언제 RabbitMQ를 쓰나?**

| 비교 항목 | Kafka | RabbitMQ |
|-----------|-------|----------|
| **용도** | 이벤트 스트리밍, 로그 수집 | 태스크 큐, 실시간 알림 |
| **데이터 보관** | 디스크에 영구 저장 (retention) | 소비되면 삭제 |
| **처리량** | 초당 수백만 건 | 초당 수만 건 |
| **메시지 순서** | 파티션 내 보장 | 큐 내 보장 |
| **소비 방식** | Pull (Consumer가 가져감) | Push (브로커가 전송) |
| **재처리** | Offset 조정으로 재소비 가능 | 재처리 어려움 |

**프로젝트 설계 선택**:
- **Kafka**: 리뷰 데이터 → Python 분석 서비스 (대용량 데이터 파이프라인)
- **RabbitMQ**: 작가 알림 (실시간성 중요, 한번만 전송)

**참고 파일**:
- `service/KafkaProducer.kt`
- `service/RabbitProducer.kt`

---

### 9. Feign Client - MSA 간 통신

**개념**: 선언적 HTTP 클라이언트 (RestTemplate 대체)

**실무 적용**:
```kotlin
@FeignClient(name = "python-service", url = "\${python.service.url}")
interface PythonClient {
    @PostMapping("/analyze")
    fun analyzeText(@RequestBody request: AnalysisRequest): AnalysisResponse
}

// 사용
@Service
class AnalysisService(
    private val pythonClient: PythonClient  // 자동 주입
) {
    fun analyze(text: String): AnalysisResponse {
        return pythonClient.analyzeText(AnalysisRequest(text))
    }
}
```

**Feign의 장점**:
- 인터페이스만 정의하면 구현체 자동 생성
- RestTemplate보다 코드 간결
- Spring Cloud 생태계와 통합 (Ribbon, Hystrix)

**실무 고려사항**:
- Timeout 설정 필수 (connectTimeout, readTimeout)
- Circuit Breaker 연동 (Resilience4j)
- Retry 정책 설정

**참고 파일**: `client/PythonClient.kt`, `controller/AnalysisController.kt`

---

### 10. Spring Batch - 대용량 배치 처리

**왜 필요한가?**
- 매일 자정 전체 리뷰 재분석
- 수백만 건의 데이터를 메모리 효율적으로 처리

**Chunk 기반 처리**:
```kotlin
@Configuration
class ReviewAnalysisJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager
) {
    @Bean
    fun reviewAnalysisJob(): Job {
        return JobBuilder("reviewAnalysisJob", jobRepository)
            .start(reviewAnalysisStep())
            .build()
    }

    @Bean
    fun reviewAnalysisStep(): Step {
        return StepBuilder("reviewAnalysisStep", jobRepository)
            .chunk<Review, ReviewAnalysis>(1000, transactionManager)  // 1000건씩 처리
            .reader(reviewReader())     // DB에서 읽기
            .processor(reviewProcessor())  // 분석 로직
            .writer(reviewWriter())     // 결과 저장
            .build()
    }
}
```

**Chunk 동작 원리**:
1. Reader가 1000건 읽기
2. Processor가 각 아이템 처리
3. Writer가 1000건 한번에 저장 (Bulk Insert)
4. Commit → 다음 Chunk

**참고 파일**: `batch/ReviewAnalysisJobConfig.kt`

---

## 개발 패턴 및 아키텍처

### 1. 계층형 아키텍처 (Layered Architecture)

```
Controller (Presentation Layer)
    ↓ 요청/응답 DTO
Service (Business Layer)
    ↓ Domain Entity
Repository (Persistence Layer)
    ↓ SQL
Database
```

**각 계층의 책임**:
- **Controller**: HTTP 요청 처리, 유효성 검증, DTO 변환
- **Service**: 비즈니스 로직, 트랜잭션 경계
- **Repository**: 데이터 접근, CRUD 추상화
- **Domain**: 엔티티, 핵심 비즈니스 규칙

### 2. Repository 패턴

**Spring Data JPA + QueryDSL 통합**:
```kotlin
// 1. Spring Data JPA 인터페이스
interface NovelRepository : JpaRepository<Novel, Long>, NovelCustomRepository

// 2. QueryDSL 커스텀 인터페이스
interface NovelCustomRepository {
    fun search(title: String?, author: String?, pageable: Pageable): Page<Novel>
}

// 3. QueryDSL 구현체 (네이밍 규칙: {EntityName}RepositoryImpl)
class NovelRepositoryImpl(
    private val queryFactory: JPAQueryFactory
) : NovelCustomRepository {
    override fun search(...) { /* 구현 */ }
}
```

**Spring Data JPA가 자동으로 통합**:
- `NovelRepository` 빈 생성 시 `NovelRepositoryImpl` 자동 결합
- 네이밍 규칙 준수 필수: `{EntityName}RepositoryImpl`

### 3. DTO 패턴

**왜 DTO가 필요한가?**
- Entity를 직접 노출하면 민감 정보 유출 위험
- 순환 참조 문제 (양방향 연관관계)
- API 스펙 변경이 Entity 구조에 영향

**Request/Response 분리**:
```kotlin
// 요청 DTO
data class NovelCreateRequest(
    val title: String,
    val author: String,
    val genre: String
)

// 응답 DTO
data class NovelResponse(
    val id: Long,
    val title: String,
    val author: String,
    val createdAt: LocalDateTime
)
```

### 4. Custom Repository 패턴

**언제 사용하나?**
- Spring Data JPA 메서드 네이밍으로 표현 불가능한 복잡한 쿼리
- 동적 쿼리 필요 (조건에 따라 WHERE 절 변경)
- 성능 최적화 필요 (N+1 문제 해결, Fetch Join)

**패턴 구조**:
1. Custom 인터페이스 정의
2. Impl 클래스에서 QueryDSL로 구현
3. 메인 Repository가 Custom 인터페이스 상속

---

## 환경 설정 및 실행

### 1. 사전 요구사항

- JDK 17
- Docker & Docker Compose
- MySQL 8.0
- Redis 7.0
- Kafka 3.x
- RabbitMQ 3.x

### 2. 환경 변수 설정

`src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/novel_insight
    username: root
    password: 1234

  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        format_sql: true
        default_batch_fetch_size: 100  # N+1 문제 해결

  kafka:
    bootstrap-servers: localhost:9092

  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

  data:
    redis:
      host: localhost
      port: 6379

jwt:
  secret: your-secret-key-min-256-bits
  expiration: 86400000  # 24시간
```

### 3. Docker Compose 실행

```bash
# 프로젝트 루트에서 실행
docker-compose up -d

# 확인
docker-compose ps
```

### 4. 빌드 & 실행

```bash
# Gradle 빌드
./gradlew clean build

# 애플리케이션 실행
./gradlew bootRun

# 또는 JAR 실행
java -jar build/libs/novel-insight-0.0.1-SNAPSHOT.jar
```

### 5. API 테스트

```bash
# 헬스체크
curl http://localhost:8080/actuator/health

# 회원가입
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"1234","email":"test@example.com"}'

# 로그인
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"1234"}'
```

---

## 성능 최적화 체크리스트

- [x] **N+1 문제 해결**: `default_batch_fetch_size` 설정
- [x] **Lazy Loading 최적화**: Fetch Join 사용
- [x] **트랜잭션 최적화**: `readOnly=true` 설정 (Dirty Checking 비활성화)
- [x] **분산 락**: Redisson으로 동시성 제어
- [x] **Connection Pool**: HikariCP 기본 설정 (최대 10개)
- [x] **Query 최적화**: QueryDSL로 count 쿼리 분리
- [x] **캐싱**: Redis 활용 (향후 @Cacheable 추가 예정)

---

## 트러블슈팅 가이드

### 1. MySQL 연결 실패
```
Caused by: java.sql.SQLException: Access denied for user 'root'@'localhost'
```
**해결**: application.yml의 username/password 확인

### 2. QueryDSL Q클래스 생성 안됨
```
Unresolved reference: QNovel
```
**해결**:
```bash
./gradlew clean build
# Q클래스 확인
ls build/generated/source/kapt/main/com/sparta/novel_insight/domain
```

### 3. Kafka 연결 실패
```
org.apache.kafka.common.errors.TimeoutException
```
**해결**:
```bash
# Kafka 컨테이너 상태 확인
docker-compose ps kafka
# 재시작
docker-compose restart kafka
```

### 4. RabbitMQ Queue 생성 안됨
**해결**:
```bash
# RabbitMQ Management Console 접속
http://localhost:15672 (guest/guest)
# Queue 수동 생성: novel.notification
```

---

## 참고 자료

- [Spring Boot 공식 문서](https://spring.io/projects/spring-boot)
- [QueryDSL 레퍼런스](http://querydsl.com/static/querydsl/5.0.0/reference/html_single/)
- [Redisson 공식 문서](https://github.com/redisson/redisson/wiki)
- [Kotlin 공식 문서](https://kotlinlang.org/docs/home.html)

---

## 라이선스

이 프로젝트는 학습 목적으로 작성되었습니다.
