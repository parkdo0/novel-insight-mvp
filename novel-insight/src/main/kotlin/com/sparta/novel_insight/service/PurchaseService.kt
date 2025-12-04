package com.sparta.novel_insight.service

import com.sparta.novel_insight.domain.PointHistory
import com.sparta.novel_insight.domain.Purchase
import com.sparta.novel_insight.domain.TransactionType
import com.sparta.novel_insight.repository.EpisodeRepository
import com.sparta.novel_insight.repository.MemberRepository
import com.sparta.novel_insight.repository.PointHistoryRepository
import com.sparta.novel_insight.repository.PointRepository
import com.sparta.novel_insight.repository.PurchaseRepository
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

/**
 * ========================================
 * PurchaseService - 에피소드 구매 서비스
 * ========================================
 *
 * [핵심 개념]
 * 1. @Transactional을 사용한 트랜잭션 관리
 * 2. AOP 프록시 패턴을 통한 선언적 트랜잭션
 * 3. Dirty Checking을 통한 자동 UPDATE
 * 4. 동시성 제어 (분산 락 Redisson)
 *
 * [5~6년차 개발자가 반드시 알아야 할 필수 개념]
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 *
 * 1. @Transactional AOP 동작 원리 (매우 중요!)
 *    [1단계] Spring Boot 시작 시
 *    - @EnableTransactionManagement 자동 활성화
 *    - PurchaseService를 스캔하여 @Transactional 메소드 발견
 *    - Spring이 CGLIB 또는 JDK Dynamic Proxy로 프록시 객체 생성
 *
 *    [2단계] 프록시 객체 생성
 *    - PurchaseService가 아닌 PurchaseService$$EnhancerBySpringCGLIB 생성
 *    - 원본 클래스를 상속받은 프록시 클래스
 *    - 모든 public 메소드를 오버라이드
 *
 *    [3단계] 메소드 호출 시
 *    purchaseEpisode()를 호출하면:
 *    a. 프록시가 먼저 호출을 가로챔 (AOP Interceptor)
 *    b. TransactionInterceptor 실행:
 *       - EntityManager 획득
 *       - Transaction 시작 (BEGIN)
 *       - Connection 획득
 *       - Isolation Level 설정
 *    c. 원본 메소드 실행 (실제 비즈니스 로직)
 *    d. 트랜잭션 후처리:
 *       - 성공 시: COMMIT
 *       - RuntimeException 발생 시: ROLLBACK
 *       - Checked Exception 시: COMMIT (기본 동작)
 *    e. Connection 반환
 *
 *    [중요!] 같은 클래스 내부 메소드 호출은 프록시를 거치지 않음
 *    ❌ fun A() { B() }  // B의 @Transactional이 동작하지 않음!
 *    ✅ 다른 빈에서 호출해야 프록시가 동작함
 *
 * 2. CGLIB vs JDK Dynamic Proxy
 *    ┌────────────────────────────────────────────────────────────┐
 *    │ 방식          │ 조건          │ 방법                      │
 *    ├────────────────────────────────────────────────────────────┤
 *    │ JDK Proxy     │ 인터페이스 有 │ InvocationHandler 사용    │
 *    │ CGLIB (기본)  │ 구체 클래스   │ 바이트코드 조작, 상속      │
 *    └────────────────────────────────────────────────────────────┘
 *    - Spring Boot 기본: CGLIB (proxyTargetClass = true)
 *    - CGLIB는 final 클래스/메소드에 사용 불가
 *    - Kotlin은 기본이 final → open 키워드 또는 allOpen 플러그인 필요
 *
 * 3. @Transactional 파라미터 상세 설명
 *    a. propagation (전파 속성)
 *       - REQUIRED (기본): 진행 중인 트랜잭션 있으면 참여, 없으면 새로 시작
 *       - REQUIRES_NEW: 항상 새 트랜잭션 시작 (기존 트랜잭션 일시 정지)
 *       - MANDATORY: 진행 중인 트랜잭션 필수 (없으면 예외)
 *       - SUPPORTS: 트랜잭션 있으면 참여, 없어도 OK
 *       - NOT_SUPPORTED: 트랜잭션 없이 실행 (진행 중이면 일시 정지)
 *       - NEVER: 트랜잭션 있으면 예외 발생
 *       - NESTED: 중첩 트랜잭션 (세이브포인트 사용)
 *
 *    b. isolation (격리 수준)
 *       - DEFAULT (기본): DB 기본 격리 수준 따름 (MySQL: REPEATABLE_READ)
 *       - READ_UNCOMMITTED: 커밋 안 된 데이터 읽기 (Dirty Read 발생)
 *       - READ_COMMITTED: 커밋된 데이터만 읽기 (Oracle 기본)
 *       - REPEATABLE_READ: 같은 쿼리 반복 시 동일 결과 보장 (MySQL 기본)
 *       - SERIALIZABLE: 최고 격리 수준, 동시성 낮음 (성능 저하)
 *
 *    c. timeout
 *       - 트랜잭션 최대 실행 시간 (초 단위)
 *       - 기본: -1 (무제한)
 *       - 초과 시 TransactionTimedOutException 발생 후 롤백
 *
 *    d. readOnly
 *       - true: 읽기 전용 트랜잭션 (최적화)
 *       - false (기본): 쓰기 가능
 *       - readOnly = true 시:
 *         * Dirty Checking 비활성화 (성능 향상)
 *         * flush 모드 변경 (MANUAL)
 *         * DB에 힌트 전달 (일부 DB는 읽기 최적화)
 *
 *    e. rollbackFor / noRollbackFor
 *       - rollbackFor: 롤백할 예외 클래스 명시
 *       - noRollbackFor: 롤백하지 않을 예외 클래스 명시
 *       - 기본: RuntimeException, Error는 롤백
 *       - Checked Exception은 롤백 안 함 (비즈니스 예외로 간주)
 *
 * 4. Dirty Checking (변경 감지)
 *    - JPA의 핵심 기능
 *    - 영속성 컨텍스트가 엔티티 상태 변화를 자동 감지
 *    - 트랜잭션 커밋 시점에 변경된 필드만 UPDATE
 *
 *    [동작 원리]
 *    1. 엔티티 조회 시 스냅샷 저장 (1차 캐시)
 *    2. 엔티티 필드 변경 (setter 또는 비즈니스 메소드)
 *    3. 트랜잭션 커밋 시 스냅샷과 비교
 *    4. 변경된 필드만 UPDATE 쿼리 자동 생성
 *    5. DB에 반영
 *
 *    [예시]
 *    point.amount -= episode.price  // setter 호출
 *    → 트랜잭션 커밋 시 자동으로:
 *    UPDATE point SET amount = ? WHERE id = ?
 *
 * 5. 분산 환경에서의 동시성 제어
 *    [문제 상황]
 *    - 여러 서버에서 동시에 같은 에피소드 구매 요청
 *    - DB 격리 수준만으로는 완벽한 동시성 제어 불가
 *    - 예: 포인트 1000원, 에피소드 가격 1000원
 *      → 2개의 요청이 동시에 들어오면 둘 다 구매 성공 (데이터 무결성 깨짐)
 *
 *    [해결 방법]
 *    a. 비관적 락 (Pessimistic Lock)
 *       - SELECT ... FOR UPDATE
 *       - 다른 트랜잭션이 읽기도 쓰기도 못함 (blocking)
 *       - 데드락 위험
 *
 *    b. 낙관적 락 (Optimistic Lock)
 *       - @Version 컬럼 사용
 *       - 수정 시 버전 체크
 *       - 충돌 시 재시도
 *
 *    c. 분산 락 (Distributed Lock) - 권장!
 *       - Redisson (Redis 기반 락)
 *       - 여러 서버 간 동기화 보장
 *       - 이 예제에서는 주입만 하고 사용 안 함 (간소화)
 *       - 실무 코드:
 *         val lock = redissonClient.getLock("purchase:$userId:$episodeId")
 *         try {
 *             if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
 *                 // 구매 로직
 *             }
 *         } finally {
 *             lock.unlock()
 *         }
 *
 * 6. 트랜잭션 롤백 시나리오
 *    [자동 롤백되는 경우]
 *    - RuntimeException 발생 (예: IllegalArgumentException)
 *    - Error 발생 (예: OutOfMemoryError)
 *    - rollbackFor에 명시된 예외 발생
 *
 *    [롤백 안 되는 경우]
 *    - Checked Exception 발생 (예: IOException)
 *    - noRollbackFor에 명시된 예외 발생
 *    - 예외를 try-catch로 잡아서 정상 리턴
 *
 * 7. 성능 최적화
 *    - Batch Insert: saveAll() 사용
 *    - OSIV 끄기: spring.jpa.open-in-view=false
 *    - 쿼리 최적화: N+1 문제 해결 (fetch join)
 *    - 인덱스 활용: userId, episodeId에 복합 인덱스
 *
 * [실무 체크리스트]
 * ✓ @Transactional 파라미터 명시 (propagation, isolation, timeout, readOnly)
 * ✓ 롤백 조건 확인 (RuntimeException만 롤백)
 * ✓ 분산 락 고려 (Redisson, Zookeeper)
 * ✓ 트랜잭션 범위 최소화 (네트워크 I/O 제외)
 * ✓ readOnly = true 활용 (조회 메소드)
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 */
@Service  // IoC: Spring 컨테이너가 빈으로 관리 (싱글톤)
class PurchaseService(
    /**
     * [DI - 생성자 주입 방식]
     * - 모든 Repository는 JpaRepository를 상속받아 Spring Data JPA가 자동 구현
     * - RedissonClient는 Redisson 라이브러리가 제공하는 분산 락 클라이언트
     * - 불변 필드 (val)로 선언하여 Thread-safe 보장
     */
    private val pointRepository: PointRepository,
    private val episodeRepository: EpisodeRepository,
    private val purchaseRepository: PurchaseRepository,
    private val pointHistoryRepository: PointHistoryRepository,
    private val redissonClient: RedissonClient  // 분산 락 클라이언트 (실무 필수)
) {

    /**
     * [에피소드 구매 트랜잭션]
     *
     * @Transactional 파라미터 상세 설명:
     *
     * 1. propagation = REQUIRED (기본값, 명시적 작성)
     *    - 진행 중인 트랜잭션이 있으면 참여, 없으면 새로 시작
     *    - 부모 트랜잭션과 같은 트랜잭션 컨텍스트 공유
     *    - 예: Controller → Service A → Service B
     *      → 모두 하나의 트랜잭션에서 실행 (하나라도 실패 시 전체 롤백)
     *
     * 2. isolation = REPEATABLE_READ
     *    - MySQL InnoDB 기본 격리 수준과 동일하게 설정
     *    - Phantom Read 방지: 같은 조건의 SELECT 반복 시 동일 결과 보장
     *    - 중요: 포인트 차감 시 다른 트랜잭션의 변경을 보지 않음
     *    - 예: 포인트 1000원 → A, B 동시 구매 → 한 트랜잭션만 성공
     *
     *    [격리 수준별 문제]
     *    - READ_UNCOMMITTED: Dirty Read (커밋 안 된 데이터 읽음)
     *    - READ_COMMITTED: Non-Repeatable Read (같은 행 반복 조회 시 다른 값)
     *    - REPEATABLE_READ: Phantom Read (범위 조회 시 새 행 추가)
     *    - SERIALIZABLE: 완전 격리 (동시성 매우 낮음)
     *
     * 3. timeout = 10 (초 단위)
     *    - 트랜잭션 최대 실행 시간 10초
     *    - 초과 시 TransactionTimedOutException 발생 후 자동 롤백
     *    - 이유: 데드락 방지, 장시간 락 점유 방지
     *    - 권장값: 3~10초 (업무 특성에 따라 조정)
     *
     * 4. rollbackFor = [Exception::class]
     *    - 모든 Exception 발생 시 롤백 (Checked Exception 포함)
     *    - 기본값은 RuntimeException, Error만 롤백
     *    - 이유: 결제는 중요 로직 → 모든 예외에 롤백 필요
     *    - 예: IOException, SQLException 등도 롤백
     *
     * [AOP 프록시 동작 흐름]
     * 1. purchaseService.purchaseEpisode() 호출
     * 2. CGLIB 프록시가 호출 가로챔
     * 3. TransactionInterceptor.invoke() 실행:
     *    a. EntityManager 획득
     *    b. Transaction 시작: BEGIN
     *    c. Connection 획득 및 격리 수준 설정: SET TRANSACTION ISOLATION LEVEL REPEATABLE READ
     *    d. 타이머 시작 (timeout 체크)
     * 4. 원본 메소드 (purchaseEpisode) 실행
     *    - 이 안에서 발생하는 모든 쿼리는 하나의 트랜잭션 내에서 실행
     * 5. 성공 시:
     *    a. flush(): 영속성 컨텍스트 변경사항 DB 반영
     *    b. COMMIT: 트랜잭션 커밋
     *    c. Connection 반환
     * 6. 예외 발생 시:
     *    a. ROLLBACK: 모든 변경사항 취소
     *    b. Connection 반환
     *    c. 예외를 상위로 전파
     *
     * [실무 적용 시나리오]
     * - 포인트 차감과 구매 내역 저장은 원자성(Atomicity) 보장 필요
     * - 하나라도 실패하면 전체 롤백되어야 함
     * - 동시 구매 요청 시 데이터 정합성 보장
     */
    @Transactional(
        propagation = Propagation.REQUIRED,      // 트랜잭션 전파 속성 (기본값, 명시적 작성)
        isolation = Isolation.REPEATABLE_READ,   // 격리 수준 (Phantom Read 방지)
        timeout = 10,                             // 타임아웃 10초
        rollbackFor = [Exception::class]          // 모든 예외에 롤백
    )
    fun purchaseEpisode(userId: Long, episodeId: Long) {
        /**
         * [트랜잭션 시작 시점]
         * - 이 메소드 진입 시 프록시가 트랜잭션 시작
         * - 실행되는 SQL: BEGIN
         */

        // 1. 에피소드 조회
        val episode = episodeRepository.findById(episodeId)
            .orElseThrow { RuntimeException("존재하지 않는 에피소드입니다.") }
        // [실행 SQL] SELECT * FROM episode WHERE id = ?

        // 2. 검증
        if (episode.isFree || episode.price == 0)
            throw RuntimeException("무료 에피소드입니다.")
        if (purchaseRepository.existsByUserIdAndEpisodeId(userId, episodeId))
            throw RuntimeException("이미 구매한 에피소드입니다.")
        // [실행 SQL] SELECT COUNT(*) FROM purchase WHERE user_id = ? AND episode_id = ?

        // 3. 지갑 & 잔액 확인
        val point = pointRepository.findByMemberId(userId)
            ?: throw RuntimeException("포인트 지갑이 없습니다.")
        // [실행 SQL] SELECT * FROM point WHERE member_id = ?

        if (point.amount < episode.price)
            throw RuntimeException("포인트가 부족합니다.")

        /**
         * [4단계] 결제 (포인트 차감)
         *
         * Dirty Checking 동작:
         * 1. point 엔티티는 영속성 컨텍스트에 의해 관리됨 (Managed State)
         * 2. point.amount -= episode.price 실행 → 필드 값 변경
         * 3. 영속성 컨텍스트가 스냅샷과 비교하여 변경 감지
         * 4. 트랜잭션 커밋 시 자동으로 UPDATE 쿼리 생성
         *
         * 실행될 SQL:
         * UPDATE point SET amount = amount - ? WHERE id = ?
         *
         * save() 호출 불필요!
         * - JPA가 자동으로 UPDATE 쿼리 실행
         * - save()는 새 엔티티 persist 또는 merge용
         * - 이미 영속 상태인 엔티티는 Dirty Checking으로 충분
         */
        point.amount -= episode.price

        /**
         * [5단계] 구매 내역 저장
         *
         * save() 메소드 동작:
         * 1. Purchase는 새 엔티티 (id = null)
         * 2. EntityManager.persist() 호출
         * 3. 영속성 컨텍스트에 추가
         * 4. INSERT 쿼리 생성 (트랜잭션 커밋 시 실행)
         *
         * 실행될 SQL:
         * INSERT INTO purchase (user_id, episode_id, price, created_at)
         * VALUES (?, ?, ?, NOW())
         */
        val purchase = Purchase(userId = userId, episodeId = episodeId, price = episode.price)
        purchaseRepository.save(purchase)

        /**
         * [6단계] 포인트 사용 이력 저장
         *
         * 가계부 기능:
         * - 포인트 충전/사용 내역을 별도 테이블에 저장
         * - 감사(Audit) 추적 가능
         * - 환불 시 이력 기반 처리
         *
         * 실행될 SQL:
         * INSERT INTO point_history (user_id, type, amount, description, created_at)
         * VALUES (?, 'USE', ?, ?, NOW())
         */
        val history = PointHistory(
            userId = userId,
            type = TransactionType.USE,
            amount = episode.price.toLong(),
            description = "${episode.title} 구매"
        )
        pointHistoryRepository.save(history)

        /**
         * [7단계] 에피소드 구매수 증가
         *
         * Dirty Checking 재활용:
         * 1. episode.increasePurchase() → 비즈니스 메소드 호출
         * 2. 내부적으로 purchaseCount++ 실행
         * 3. 영속성 컨텍스트가 변경 감지
         * 4. 트랜잭션 커밋 시 자동 UPDATE
         *
         * 실행될 SQL:
         * UPDATE episode SET purchase_count = purchase_count + 1 WHERE id = ?
         *
         * 장점:
         * - save() 호출 불필요
         * - 의미 있는 메소드명으로 가독성 향상
         * - 캡슐화: episode 내부 로직 은닉
         */
        episode.increasePurchase()

        /**
         * [트랜잭션 커밋 시점]
         * - 메소드 정상 종료 시
         * - 프록시가 다음 작업 수행:
         *   1. EntityManager.flush() 호출
         *      → 영속성 컨텍스트의 모든 변경사항 SQL로 변환
         *      → 실제 DB에 반영 (아직 커밋 전)
         *   2. COMMIT 실행
         *      → 모든 변경사항 영구 저장
         *   3. Connection 반환
         *
         * [트랜잭션 롤백 시점]
         * - 예외 발생 시 (RuntimeException, Exception 등)
         * - 프록시가 다음 작업 수행:
         *   1. EntityManager.clear() 호출
         *      → 영속성 컨텍스트 초기화
         *   2. ROLLBACK 실행
         *      → 모든 변경사항 취소
         *   3. Connection 반환
         *   4. 예외를 상위로 전파 (Controller가 처리)
         */
    }
}