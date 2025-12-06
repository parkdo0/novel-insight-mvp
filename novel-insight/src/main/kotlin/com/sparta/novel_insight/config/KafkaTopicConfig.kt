package com.sparta.novel_insight.config

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

/**
 * # Kafka Topic 설정 (Phase 3: 파티션 기반 병렬 처리)
 *
 * ## 목적
 * - Kafka Topic의 파티션 수를 명시적으로 설정하여 병렬 처리 활성화
 * - Novel ID 기반 파티셔닝으로 이벤트 순서 보장 + 처리 속도 향상
 *
 * ## Phase 3 구현 목표
 * 1. **파티션 수 증가**: 기본 1개 → 3개로 확장
 * 2. **부하 분산**: 여러 Consumer가 독립적으로 파티션 처리
 * 3. **순서 보장**: 같은 Novel ID는 같은 파티션 → FIFO 순서 유지
 * 4. **수평 확장**: 파티션 추가로 처리량 선형 증가 가능
 *
 * ---
 *
 * ## Kafka Partitioning 이해하기
 *
 * ### 파티션이란?
 * ```
 * Kafka Topic "novel-reviews" (3 Partitions)
 * ┌────────────────────────────────────────┐
 * │ Partition 0 │ [Msg1] [Msg2] [Msg3] ... │
 * ├────────────────────────────────────────┤
 * │ Partition 1 │ [Msg4] [Msg5] [Msg6] ... │
 * ├────────────────────────────────────────┤
 * │ Partition 2 │ [Msg7] [Msg8] [Msg9] ... │
 * └────────────────────────────────────────┘
 * ```
 * - 파티션: 토픽을 물리적으로 나눈 단위
 * - 각 파티션은 독립적인 순서 보장 (FIFO)
 * - 파티션 간에는 순서 보장 안 됨 (병렬 처리 가능)
 *
 * ### 메시지 키(Key)와 파티션 할당
 * ```kotlin
 * // Producer 전송 시:
 * kafkaTemplate.send(topic, key, value)
 *                            ↓
 *                    key.hashCode() % partitionCount
 *                            ↓
 *           같은 key는 항상 같은 파티션으로!
 * ```
 *
 * **예시: Novel ID 기반 파티셔닝**
 * ```
 * Novel ID 1 → hash(1) % 3 = 1 → Partition 1
 * Novel ID 2 → hash(2) % 3 = 2 → Partition 2
 * Novel ID 3 → hash(3) % 3 = 0 → Partition 0
 * Novel ID 4 → hash(4) % 3 = 1 → Partition 1  (Novel 1과 같은 파티션)
 * ```
 *
 * ### WHY Novel ID를 Key로 사용하는가?
 * 1. **순서 보장**
 *    - 같은 소설의 리뷰는 같은 파티션에 순차 저장
 *    - 예: 소설 1의 리뷰가 시간 순으로 도착하면, 분석도 시간 순으로 처리됨
 *    - 중요: 감성 추이 분석 시 순서가 바뀌면 결과 왜곡
 *
 * 2. **부하 분산**
 *    - 인기 소설 여러 개가 있으면 각각 다른 파티션으로 분산
 *    - 한 소설에 리뷰가 집중되어도 다른 소설 처리에 영향 없음
 *    - 예: 소설 1에 리뷰 1000개, 소설 2에 리뷰 10개 → 각자 독립 처리
 *
 * 3. **Consumer 병렬 처리**
 *    - Consumer 3개를 띄우면 각자 파티션 1개씩 담당
 *    - 처리 속도 3배 향상 (이론적)
 *    - 예: Consumer A(P0), Consumer B(P1), Consumer C(P2)
 *
 * ---
 *
 * ## Phase 3 시나리오: 병렬 처리 효과
 *
 * ### Before Phase 3 (파티션 1개)
 * ```
 * [Producer] → [Partition 0] → [Consumer 1개]
 *                                     ↓
 *                              순차 처리 (느림)
 *                              1000 msg/sec
 * ```
 * - 모든 메시지가 1개 파티션으로 몰림
 * - Consumer 1개만 처리 가능 (병렬화 불가)
 * - 처리 속도 제한 (Single Thread Bottleneck)
 *
 * ### After Phase 3 (파티션 3개)
 * ```
 * [Producer]
 *     ├─→ [Partition 0] → [Consumer A]
 *     ├─→ [Partition 1] → [Consumer B]
 *     └─→ [Partition 2] → [Consumer C]
 *                   ↓
 *            병렬 처리 (빠름)
 *            3000 msg/sec (3배)
 * ```
 * - Novel ID 해시로 파티션 분산
 * - Consumer 3개가 독립 처리
 * - 처리 속도 3배 향상 (수평 확장)
 *
 * ---
 *
 * ## 파티션 수 결정 기준
 *
 * ### WHY 3개?
 * 1. **적정 수준**
 *    - 소규모 프로젝트: 3-5개 권장
 *    - 대규모 프로젝트: 10-100개
 *    - 현재 MVP: 3개로 충분
 *
 * 2. **Consumer 수와 매칭**
 *    - 파티션 3개 = Consumer 최대 3개까지 효율적
 *    - Consumer > 파티션이면 일부 Consumer는 유휴 상태
 *    - Consumer < 파티션이면 1개 Consumer가 여러 파티션 처리
 *
 * 3. **리소스 비용**
 *    - 파티션마다 메모리/디스크 사용
 *    - 파티션이 너무 많으면 Zookeeper 부담 증가
 *    - Trade-off: 성능 vs 리소스
 *
 * ### 확장 시나리오
 * - **소규모** (현재): 3 partitions, 1-3 consumers
 * - **중규모** (트래픽 증가): 10 partitions, 5-10 consumers
 * - **대규모** (전국 서비스): 50 partitions, 20-50 consumers
 *
 * ---
 *
 * ## Replication Factor (복제 계수)
 *
 * ### 복제본이란?
 * ```
 * Replication Factor = 2
 * ┌─────────────────────────────────┐
 * │ Broker 1 (Leader)   │ [P0] [P1] │ ← 메인 데이터
 * ├─────────────────────────────────┤
 * │ Broker 2 (Follower) │ [P0] [P1] │ ← 백업 데이터
 * └─────────────────────────────────┘
 * ```
 * - Leader: 실제 읽기/쓰기 처리
 * - Follower: Leader 데이터 복제 (백업)
 * - Leader 장애 시 Follower가 Leader로 승격
 *
 * ### WHY Replication Factor = 1?
 * - **단일 Broker**: docker-compose에서 Kafka 1개만 실행
 * - **로컬 개발 환경**: 데이터 유실 위험 낮음
 * - **리소스 절약**: 복제본 없으면 디스크/메모리 절약
 *
 * ### Production 환경에서는?
 * - **Replication Factor ≥ 3 권장**
 * - Broker 3개 이상 구성 (고가용성)
 * - 1개 Broker 장애에도 서비스 정상 운영
 * - 예: AWS MSK, Confluent Cloud
 *
 * ---
 *
 * ## 성능 최적화 고려사항
 *
 * ### 1. Consumer Group
 * ```kotlin
 * // application.yaml
 * spring:
 *   kafka:
 *     consumer:
 *       group-id: novel-insight-group
 * ```
 * - 같은 Group ID의 Consumer는 파티션 분배
 * - 다른 Group ID면 모든 메시지 중복 수신
 * - 예: Group A(감성 분석), Group B(통계 집계)
 *
 * ### 2. Consumer Lag 모니터링
 * - Lag: Producer가 보낸 메시지 - Consumer가 읽은 메시지
 * - Lag이 계속 증가하면 Consumer 처리 속도 < Producer 전송 속도
 * - 해결: Consumer 추가 또는 파티션 추가
 *
 * ### 3. 파티션 재조정 (Rebalancing)
 * - Consumer 추가/제거 시 파티션 재할당
 * - 잠깐 처리 중단 (보통 수 초)
 * - 최소화: session.timeout.ms, heartbeat.interval.ms 튜닝
 *
 * ---
 *
 * ## 실무 베스트 프랙티스
 *
 * ### 1. 토픽 생성 전략
 * - ✅ 애플리케이션 코드에서 명시적 생성 (현재 방식)
 * - ❌ auto.create.topics.enable=true (운영 환경 비권장)
 * - 이유: 파티션 수, 복제 계수를 명확히 제어
 *
 * ### 2. 파티션 수 변경
 * - 증가: 가능 (kafka-topics.sh --alter --partitions 5)
 * - 감소: **불가능** (데이터 유실 위험)
 * - 처음부터 적절한 수로 설정 중요
 *
 * ### 3. 메시지 키 설계
 * - Key가 null이면 Round-robin으로 파티션 할당
 * - Key가 있으면 Hash로 파티션 할당
 * - Key 선택: 데이터 특성에 따라 결정
 *   - 순서 보장 필요: User ID, Order ID, Novel ID
 *   - 순서 무관: null (최대 부하 분산)
 *
 * ### 4. Hot Partition 문제
 * - 특정 Key에 메시지 집중 → 1개 파티션만 과부하
 * - 예: 인기 소설 1개에 리뷰 90% 집중
 * - 해결:
 *   1. Key에 랜덤 접미사 추가 (novelId + random(0-9))
 *   2. Custom Partitioner 구현
 *   3. 파티션 수 증가
 *
 * ---
 *
 * ## 모니터링 및 문제 해결
 *
 * ### Kafka Topic 확인
 * ```bash
 * # 토픽 목록 조회
 * docker exec -it novel-kafka-container kafka-topics \
 *   --list --bootstrap-server localhost:9092
 *
 * # 토픽 상세 정보 (파티션 수 확인)
 * docker exec -it novel-kafka-container kafka-topics \
 *   --describe --topic novel-reviews \
 *   --bootstrap-server localhost:9092
 * ```
 *
 * **예상 출력:**
 * ```
 * Topic: novel-reviews  PartitionCount: 3  ReplicationFactor: 1
 *   Topic: novel-reviews  Partition: 0  Leader: 1  Replicas: 1  Isr: 1
 *   Topic: novel-reviews  Partition: 1  Leader: 1  Replicas: 1  Isr: 1
 *   Topic: novel-reviews  Partition: 2  Leader: 1  Replicas: 1  Isr: 1
 * ```
 *
 * ### Consumer Group 상태
 * ```bash
 * docker exec -it novel-kafka-container kafka-consumer-groups \
 *   --describe --group novel-insight-group \
 *   --bootstrap-server localhost:9092
 * ```
 *
 * ### 메시지 전송 테스트
 * ```bash
 * # 파티션별 메시지 확인
 * docker exec -it novel-kafka-container kafka-console-consumer \
 *   --topic novel-reviews \
 *   --from-beginning \
 *   --bootstrap-server localhost:9092 \
 *   --property print.partition=true
 * ```
 *
 * ---
 *
 * ## 참고 자료
 * - [Kafka Partitioning 공식 문서](https://kafka.apache.org/documentation/#intro_concepts_and_terms)
 * - [Spring Kafka TopicBuilder](https://docs.spring.io/spring-kafka/reference/kafka/configuring-topics.html)
 * - [Kafka Performance Tuning](https://kafka.apache.org/documentation/#producerconfigs)
 *
 * @Configuration
 * - Spring이 이 클래스를 설정 클래스로 인식
 * - @Bean 메서드로 Spring Container에 빈 등록
 */
@Configuration
class KafkaTopicConfig {

    /**
     * "novel-reviews" 토픽 생성 빈
     *
     * Spring Boot 시작 시 KafkaAdmin이 자동으로 이 빈을 감지하여
     * Kafka 서버에 토픽을 생성합니다. (이미 존재하면 스킵)
     *
     * ## TopicBuilder 설명
     * ```kotlin
     * TopicBuilder.name("topic-name")     // 토픽 이름
     *   .partitions(3)                    // 파티션 수
     *   .replicas(1)                      // 복제 계수
     *   .config(key, value)               // 토픽 설정 (선택사항)
     *   .build()
     * ```
     *
     * ## 파티션 3개 설정 이유
     * 1. **병렬 처리**: Consumer 최대 3개까지 독립 처리
     * 2. **부하 분산**: Novel ID 해시로 균등 분배
     * 3. **순서 보장**: 같은 Novel ID는 같은 파티션 (FIFO)
     * 4. **확장 가능**: 트래픽 증가 시 Consumer 추가 가능
     *
     * ## Replication Factor 1 설정 이유
     * - 로컬 개발 환경: Kafka Broker 1개만 실행
     * - 복제본 없음: 디스크/메모리 절약
     * - Production: 3 이상 권장 (고가용성)
     *
     * ## 토픽 설정 옵션 (선택사항)
     * ```kotlin
     * .config("retention.ms", "604800000")         // 7일 보관
     * .config("cleanup.policy", "delete")          // 오래된 메시지 삭제
     * .config("compression.type", "gzip")          // 메시지 압축
     * .config("max.message.bytes", "1048576")      // 최대 메시지 크기 1MB
     * ```
     *
     * @return NewTopic 객체 (KafkaAdmin이 감지하여 토픽 생성)
     */
    @Bean
    fun novelReviewsTopic(): NewTopic {
        return TopicBuilder
            .name("novel-reviews")  // 토픽 이름 (KafkaProducerService.REVIEW_TOPIC과 동일)
            .partitions(3)          // ★ Phase 3: 파티션 3개로 병렬 처리 활성화
            .replicas(1)            // 복제 계수 1 (로컬 단일 Broker)
            .build()

        // Spring Boot 시작 시 자동 실행:
        // 1. KafkaAdmin이 이 빈 감지
        // 2. Kafka 서버 연결 (application.yaml의 bootstrap-servers)
        // 3. 토픽 존재 여부 확인
        // 4. 없으면 생성, 있으면 스킵
        // 5. 로그 출력: "Created topic novel-reviews with 3 partitions"
    }
}

/*
 * Phase 3 구현 완료 체크리스트:
 *
 * ✅ 1. KafkaTopicConfig 생성 (파티션 3개 설정)
 * ✅ 2. 상세한 WHY 문서화 (파티셔닝 원리, 병렬 처리 효과)
 * 🔄 3. KafkaProducerService 문서화 강화 (Phase 3 관점 추가)
 * ⏳ 4. Python Consumer 파티션 처리 확인
 * ⏳ 5. README 업데이트 (Phase 3 완료 반영)
 * ⏳ 6. 커밋 및 Push
 *
 * 다음 단계:
 * - KafkaProducerService.kt에 Phase 3 관련 주석 추가
 * - 파티션 분산 효과 및 모니터링 방법 문서화
 * - README에 Phase 3 완료 상태 업데이트
 */
