package com.sparta.novel_insight.config

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

/**
 * # WebSocketConfig - WebSocket + STOMP 설정
 *
 * ## 목적
 * - 실시간 양방향 통신을 위한 WebSocket 설정
 * - STOMP 프로토콜을 사용한 메시지 브로커 구성
 * - 작가에게 실시간 알림 전송
 *
 * ## 실무 권장사항 (Production Best Practices)
 *
 * ### 1. STOMP (Simple Text Oriented Messaging Protocol)
 * - **WHY STOMP?**
 *   - Raw WebSocket보다 고수준 프로토콜
 *   - pub/sub 패턴 기본 지원
 *   - 메시지 라우팅, 헤더, 구독 관리 등 표준화
 *
 * - **대안 비교**:
 *   - Raw WebSocket: 메시지 포맷, 라우팅 직접 구현 필요
 *   - STOMP: 표준화된 프레임 포맷, Spring 통합 우수
 *   - SockJS: STOMP 위에서 fallback 지원 (WebSocket 미지원 브라우저)
 *
 * ### 2. 메시지 브로커 (Message Broker)
 * - **Simple Broker** (현재 사용)
 *   - 메모리 기반, 단일 서버 환경
 *   - 간단한 pub/sub 구현
 *   - **단점**: 서버 재시작 시 연결 끊김, 스케일아웃 불가
 *
 * - **External Broker** (프로덕션 권장)
 *   - RabbitMQ, ActiveMQ 등
 *   - 다중 서버 환경에서 메시지 공유
 *   - 메시지 영속성, 클러스터링 지원
 *   - **예시**:
 *     ```kotlin
 *     registry.enableStompBrokerRelay("/topic", "/queue")
 *         .setRelayHost("localhost")
 *         .setRelayPort(61613)  // RabbitMQ STOMP 포트
 *     ```
 *
 * ### 3. SockJS Fallback
 * - 구형 브라우저에서 WebSocket 미지원 시 HTTP long-polling 등으로 fallback
 * - 사용자 경험 향상
 *
 * ## 메시지 흐름 (Flow)
 *
 * ### 구독 (Subscribe)
 * ```
 * Client → Server
 * STOMP CONNECT /ws
 * STOMP SUBSCRIBE /topic/notifications/{userId}
 * ```
 *
 * ### 발송 (Send)
 * ```
 * Server → Client
 * STOMP SEND /topic/notifications/{userId}
 * Body: { "type": "NEW_REVIEW", "content": "..." }
 * ```
 *
 * ## 엔드포인트 설계
 *
 * ### 연결 엔드포인트
 * - `/ws`: WebSocket 연결 (SockJS 포함)
 * - 클라이언트가 처음 연결할 때 사용
 *
 * ### 메시지 목적지 (Destination)
 * - `/app/**`: 클라이언트 → 서버 메시지 전송
 *   - 예: `/app/chat` (채팅 메시지 전송)
 *   - Controller의 @MessageMapping이 처리
 *
 * - `/topic/**`: 서버 → 다수 클라이언트 브로드캐스트
 *   - 예: `/topic/notifications/123` (사용자 123에게 알림)
 *   - 1:N 관계 (한 명이 발송, 여러 명 수신)
 *
 * - `/queue/**`: 서버 → 특정 클라이언트 1:1 메시지
 *   - 예: `/queue/errors` (특정 사용자에게 에러 메시지)
 *
 * ## 프로덕션 고려사항
 *
 * ### 1. 스케일아웃 (Scale-Out)
 * - Simple Broker는 단일 서버만 지원
 * - 다중 서버 환경에서는 RabbitMQ/Redis Pub/Sub 사용
 * - **예시**: 서버 A의 연결을 서버 B에서 메시지 발송 불가
 *
 * ### 2. 인증/인가
 * - WebSocket 연결 시 JWT 토큰 검증
 * - STOMP 헤더에 토큰 포함 또는 쿼리 파라미터 사용
 * - **예시**:
 *   ```javascript
 *   const socket = new SockJS('/ws?token=' + accessToken)
 *   ```
 *
 * ### 3. 성능 최적화
 * - 연결 수 제한 (DoS 방지)
 * - 메시지 크기 제한
 * - Heartbeat 설정 (연결 유지 확인)
 *
 * ## 클라이언트 사용 예시 (JavaScript)
 * ```javascript
 * // 1. SockJS + STOMP 클라이언트 연결
 * const socket = new SockJS('/ws')
 * const stompClient = Stomp.over(socket)
 *
 * // 2. 연결 및 구독
 * stompClient.connect({}, function(frame) {
 *     console.log('Connected: ' + frame)
 *
 *     // 3. 알림 구독 (userId = 123)
 *     stompClient.subscribe('/topic/notifications/123', function(message) {
 *         const notification = JSON.parse(message.body)
 *         console.log('새 알림:', notification.content)
 *         // UI 업데이트 (토스트, 배지 등)
 *     })
 * })
 *
 * // 4. 메시지 전송 (옵션)
 * stompClient.send('/app/read', {}, JSON.stringify({notificationId: 456}))
 * ```
 *
 * ## 설계 패턴
 * - **Pub/Sub Pattern**: 발행자와 구독자 분리
 * - **Observer Pattern**: 이벤트 기반 알림
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {

    /**
     * STOMP 엔드포인트 등록
     *
     * ## 연결 엔드포인트: `/ws`
     * - 클라이언트가 WebSocket 연결을 시작하는 엔드포인트
     * - SockJS fallback 활성화 (구형 브라우저 지원)
     *
     * ## CORS 설정
     * - 프론트엔드(localhost:5173)에서 연결 허용
     * - 프로덕션 배포 시 실제 도메인으로 변경 필요
     *
     * ## SockJS Fallback
     * - WebSocket 미지원 브라우저에서 자동으로 다음 순서로 시도:
     *   1. WebSocket (최우선)
     *   2. HTTP Streaming
     *   3. HTTP Long Polling
     *
     * ## 사용 예시
     * ```javascript
     * // Native WebSocket
     * const ws = new WebSocket('ws://localhost:8080/ws')
     *
     * // SockJS (권장 - fallback 지원)
     * const socket = new SockJS('http://localhost:8080/ws')
     * ```
     */
    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
            // CORS 설정: 프론트엔드 도메인 허용
            .setAllowedOrigins(
                "http://localhost:5173",  // Vite dev server
                "http://localhost",
                "http://127.0.0.1"
            )
            // SockJS fallback 활성화 (WebSocket 미지원 브라우저 대응)
            .withSockJS()
    }

    /**
     * 메시지 브로커 설정
     *
     * ## Application Destination Prefix: `/app`
     * - 클라이언트 → 서버 메시지 전송 시 사용
     * - @MessageMapping("/chat")는 `/app/chat`로 매핑
     * - Controller가 처리
     *
     * ## Broker Destinations
     * ### `/topic` (브로드캐스트)
     * - 1:N 메시지 (한 명이 발송, 여러 명 수신)
     * - 예: `/topic/notifications/123` (사용자 123에게 알림)
     * - 사용 사례: 공지사항, 실시간 업데이트
     *
     * ### `/queue` (개인 메시지)
     * - 1:1 메시지 (특정 사용자에게만 전송)
     * - 예: `/queue/errors` (에러 메시지)
     * - 사용 사례: 개인 알림, 에러 피드백
     *
     * ## Simple Broker vs External Broker
     *
     * ### Simple Broker (현재 사용)
     * - **장점**:
     *   - 설정 간단, 별도 인프라 불필요
     *   - 메모리 기반, 빠른 응답 속도
     *   - MVP/개발 환경에 적합
     *
     * - **단점**:
     *   - 단일 서버만 지원 (스케일아웃 불가)
     *   - 서버 재시작 시 모든 연결 끊김
     *   - 메시지 영속성 없음
     *
     * ### External Broker (프로덕션 권장)
     * - **RabbitMQ**:
     *   ```kotlin
     *   registry.enableStompBrokerRelay("/topic", "/queue")
     *       .setRelayHost("rabbitmq.example.com")
     *       .setRelayPort(61613)  // STOMP over RabbitMQ
     *       .setClientLogin("guest")
     *       .setClientPasscode("guest")
     *   ```
     * - **장점**:
     *   - 다중 서버 지원 (로드 밸런싱)
     *   - 메시지 영속성 (서버 재시작 시에도 유지)
     *   - 클러스터링, HA (High Availability)
     *
     * ## 프로덕션 마이그레이션 가이드
     * 1. RabbitMQ 설치 및 STOMP 플러그인 활성화
     * 2. `enableSimpleBroker()` → `enableStompBrokerRelay()` 변경
     * 3. 연결 정보 환경변수로 외부화
     * 4. 클러스터 구성 (Optional)
     */
    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        // 클라이언트 → 서버 메시지 prefix
        registry.setApplicationDestinationPrefixes("/app")

        // 서버 → 클라이언트 메시지 broker
        // Simple Broker (메모리 기반, 단일 서버)
        registry.enableSimpleBroker("/topic", "/queue")

        /* ===================================
         * [프로덕션] External Broker 설정 예시
         * ===================================
         * registry.enableStompBrokerRelay("/topic", "/queue")
         *     .setRelayHost(System.getenv("RABBITMQ_HOST") ?: "localhost")
         *     .setRelayPort(61613)  // STOMP port
         *     .setClientLogin(System.getenv("RABBITMQ_USER") ?: "guest")
         *     .setClientPasscode(System.getenv("RABBITMQ_PASSWORD") ?: "guest")
         *     .setSystemLogin("admin")
         *     .setSystemPasscode("admin")
         */
    }
}
