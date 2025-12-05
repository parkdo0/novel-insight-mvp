"""
Novel Insight - 리뷰 감성 분석 서비스 (Phase 5: BERT 통합)

Kafka에서 실시간으로 리뷰 로그를 수집하여 감성 분석을 수행하고,
분석 결과를 MySQL 데이터베이스에 저장하는 Python 마이크로서비스입니다.

주요 구성요소:
1. FastAPI: REST API 서버 (헬스 체크, 수동 분석 API)
2. Kafka Consumer: 백그라운드에서 리뷰 메시지 구독 및 처리
3. Sentiment Analyzer: KoNLPy + BERT 기반 한국어 감성 분석 엔진 ⭐ Phase 5
4. Database Manager: MySQL에 분석 결과 저장

## Phase 5 주요 변경사항
- BERT 기반 감성 분석 추가 (KcELECTRA 모델)
- 7가지 감정 분류 (POSITIVE, NEGATIVE, NEUTRAL, JOY, SADNESS, ANGER, SURPRISE)
- 배치 처리로 성능 최적화
- Confidence Score 제공 (모델 확신도)
- 환경 변수로 분석기 선택 가능 (USE_BERT=true/false)

실행 흐름:
[Spring Boot] --Kafka 메시지--> [이 서비스] --BERT 감성 분석--> [MySQL 저장]
"""

# ===== 표준 라이브러리 임포트 =====
import json  # JSON 파싱 및 직렬화
import logging  # 로깅 시스템
from threading import Thread  # 백그라운드 스레드 실행

# ===== 외부 라이브러리 임포트 =====
# Kafka Consumer: Apache Kafka에서 메시지를 가져오는 클라이언트
# Kafka는 대용량 실시간 데이터 스트리밍 플랫폼
from kafka import KafkaConsumer

# FastAPI: 빠른 성능의 Python 웹 프레임워크 (Node.js Express 같은 역할)
# HTTPException: HTTP 에러 응답을 위한 예외 클래스
from fastapi import FastAPI, HTTPException

# Pydantic BaseModel: 데이터 유효성 검증을 위한 DTO 기본 클래스
# 타입 힌트를 기반으로 자동으로 검증 수행
from pydantic import BaseModel

# ===== 프로젝트 내부 모듈 임포트 =====
# config.py에서 환경 설정 싱글톤 가져오기
from config import settings

# sentiment_analyzer.py에서 KoNLPy 감성 분석기 싱글톤 가져오기
from sentiment_analyzer import get_analyzer as get_konlpy_analyzer

# Phase 5: BERT 감성 분석기 싱글톤 가져오기
from bert_sentiment_analyzer import get_bert_analyzer

# database.py에서 데이터베이스 매니저 싱글톤 가져오기
from database import get_db_manager

# ===== 환경 변수 =====
import os


# ===== 로깅 설정 =====
# Python 표준 logging 모듈 설정
# 로그는 애플리케이션 동작 상태를 추적하는 중요한 도구
logging.basicConfig(
    # 로그 레벨 설정 (DEBUG < INFO < WARNING < ERROR < CRITICAL)
    # settings.log_level은 환경변수로 설정 (기본값: "INFO")
    # getattr(logging, "INFO")는 logging.INFO 상수를 가져옴
    level=getattr(logging, settings.log_level),

    # 로그 출력 형식 정의
    # %(asctime)s: 타임스탬프 (예: 2025-12-03 10:30:45,123)
    # %(levelname)s: 로그 레벨 (INFO, ERROR 등)
    # %(name)s: 로거 이름 (__main__, database 등)
    # %(message)s: 실제 로그 메시지
    format='%(asctime)s [%(levelname)s] %(name)s - %(message)s'
)

# 이 모듈 전용 로거 생성
# __name__은 모듈 이름 (main.py에서는 "__main__"이 됨)
logger = logging.getLogger(__name__)


# ===== FastAPI 애플리케이션 생성 =====
# FastAPI 인스턴스 생성 (Express의 app = express()와 유사)
# title: API 문서에 표시될 제목
# version: API 버전 정보
# FastAPI는 자동으로 /docs 경로에 Swagger UI 제공
app = FastAPI(title="Novel Insight Analysis Service", version="1.0.0")


# ===================================
# Phase 5: 감성 분석기 선택 (환경 변수 기반)
# ===================================
# 환경 변수 USE_BERT로 분석기 선택 가능
# - USE_BERT=true: BERT 분석기 사용 (높은 정확도, 느림)
# - USE_BERT=false: KoNLPy 분석기 사용 (빠름, 낮은 정확도)
# - 기본값: false (하위 호환성)
USE_BERT = os.getenv("USE_BERT", "false").lower() == "true"

# 감성 분석기 싱글톤 인스턴스
# 애플리케이션 전체에서 하나의 인스턴스만 사용 (메모리 절약)
if USE_BERT:
    logger.info("🚀 Phase 5: BERT 감성 분석기 로딩 중...")
    analyzer = get_bert_analyzer()
    analyzer_type = "BERT (KcELECTRA)"

    # BERT 모델 정보 로깅
    model_info = analyzer.get_model_info()
    logger.info(f"✅ BERT 모델 로딩 완료!")
    logger.info(f"   - 모델: {model_info['model_name']}")
    logger.info(f"   - 디바이스: {model_info['device']}")
    logger.info(f"   - 파라미터: {model_info['parameter_count']:,}")
    logger.info(f"   - 메모리: {model_info['model_size_mb']} MB")
    logger.info(f"   - 감정 레이블: {', '.join(model_info['emotion_labels'])}")
else:
    logger.info("📚 KoNLPy 감성 분석기 사용 (Legacy)")
    analyzer = get_konlpy_analyzer()
    analyzer_type = "KoNLPy (Okt)"

logger.info(f"감성 분석기: {analyzer_type}")

# 데이터베이스 매니저 싱글톤 인스턴스
# 데이터베이스 커넥션 풀을 공유하여 효율적으로 관리
db_manager = get_db_manager()


def process_review_log(log_data: dict) -> bool:
    """
    Kafka에서 수신한 리뷰 로그를 처리하는 핵심 함수

    처리 단계:
    1. 로그 데이터에서 필수 필드 추출 (novelId, authorId, content)
    2. 감성 분석기로 리뷰 내용 분석
    3. 분석 결과를 MySQL 데이터베이스에 저장

    Args:
        log_data: Kafka에서 받은 JSON 형태의 로그 데이터
                  예시: {
                      "traceId": "abc123",
                      "timestamp": 1733202000000,
                      "novelId": 1,
                      "authorId": 1,
                      "content": "이 소설 정말 재미있어요!"
                  }

    Returns:
        bool: 처리 성공 여부 (True: 성공, False: 실패)
    """
    try:
        # 1단계: 필수 데이터 추출
        # dict.get()은 키가 없어도 에러가 나지 않고 None 반환
        novel_id = log_data.get('novelId')  # 소설 ID
        author_id = log_data.get('authorId')  # 작가 ID
        content = log_data.get('content', '')  # 리뷰 내용 (없으면 빈 문자열)

        # 필수 데이터 검증
        # all()은 리스트의 모든 요소가 True일 때만 True 반환
        # None이나 빈 문자열은 False로 평가됨
        if not all([novel_id, author_id, content]):
            logger.warning(f"필수 데이터 누락: {log_data}")
            return False

        # 2단계: 감성 분석 수행
        # analyzer.analyze()는 dict 형태의 분석 결과 반환
        #
        # KoNLPy 결과:
        # {
        #     'sentiment': 'POSITIVE',
        #     'score': 75.5,
        #     'positive_count': 3,
        #     'negative_count': 0
        # }
        #
        # Phase 5: BERT 결과:
        # {
        #     'sentiment': 'JOY',
        #     'confidence': 95.8,
        #     'score': 95.8,
        #     'polarity': 'positive',
        #     'is_strong_emotion': True,
        #     'all_scores': {...},
        #     ...
        # }
        analysis_result = analyzer.analyze(content)

        # Phase 5: 분석 결과 로그 출력 (모니터링 용도)
        # BERT는 추가 정보 포함 (emotion, confidence)
        if USE_BERT:
            # BERT 분석 결과 로깅
            emotion_info = f", emotion={analysis_result.get('sentiment', 'N/A')}"
            confidence_info = f", confidence={analysis_result.get('confidence', 0):.1f}%"
            strong_emotion_marker = " 🔥" if analysis_result.get('is_strong_emotion', False) else ""

            logger.info(
                f"[BERT] 분석 완료 - novelId:{novel_id}"
                f"{emotion_info}"
                f"{confidence_info}"
                f", score:{analysis_result['score']:.2f}"
                f"{strong_emotion_marker}"
            )
        else:
            # KoNLPy 분석 결과 로깅 (기존 방식)
            logger.info(
                f"[KoNLPy] 분석 완료 - novelId:{novel_id}, "
                f"sentiment:{analysis_result['sentiment']}, "
                f"score:{analysis_result['score']:.2f}"
            )

        # 3단계: 데이터베이스에 저장
        # save_analysis()는 저장 성공 시 생성된 레코드 ID 반환, 실패 시 None 반환
        saved_id = db_manager.save_analysis(
            novel_id=novel_id,
            author_id=author_id,
            content=content,
            analysis_result=analysis_result
        )

        # 저장 성공 여부 반환
        # saved_id가 None이 아니면 True, None이면 False
        return saved_id is not None

    except Exception as e:
        # 예외 발생 시 에러 로그 출력
        # exc_info=True: 스택 트레이스(Stack Trace) 포함
        # 스택 트레이스는 에러가 발생한 코드의 위치를 추적하는 정보
        logger.error(f"리뷰 처리 실패: {e}", exc_info=True)
        return False


def start_kafka_consumer():
    """
    Kafka Consumer를 시작하여 메시지를 지속적으로 수신하는 함수

    이 함수는 백그라운드 스레드에서 실행되며, 애플리케이션이 종료될 때까지
    계속해서 Kafka 토픽을 모니터링합니다.

    처리 흐름:
    1. Kafka 브로커에 연결 시도 (최대 5회 재시도)
    2. 메시지 수신 대기 (무한 루프)
    3. 메시지 수신 시 process_review_log() 호출
    4. 연결 실패 시 지수 백오프(Exponential Backoff) 전략으로 재시도

    재시도 전략 (Exponential Backoff):
    - 1회 실패: 5초 대기 후 재시도
    - 2회 실패: 10초 대기 후 재시도
    - 3회 실패: 15초 대기 후 재시도
    - ...
    - 5회 실패: 포기
    """
    logger.info("Kafka Consumer 시작...")

    # 재시도 로직을 위한 변수 초기화
    retry_count = 0  # 현재 재시도 횟수
    max_retries = 5  # 최대 재시도 횟수

    # 최대 재시도 횟수에 도달할 때까지 반복
    while retry_count < max_retries:
        try:
            # Kafka Consumer 객체 생성
            # Consumer는 Kafka 토픽에서 메시지를 가져오는 클라이언트
            consumer = KafkaConsumer(
                # 구독할 토픽 이름
                # settings.kafka_topic은 환경변수로 설정 (기본값: "novel-reviews")
                settings.kafka_topic,

                # Kafka 브로커 서버 주소
                # Docker 네트워크에서는 서비스 이름으로 접근
                # 예: "novel-kafka:29092"
                bootstrap_servers=settings.kafka_bootstrap_servers,

                # Consumer Group ID
                # 같은 그룹의 Consumer들은 메시지를 분산 처리
                # 예를 들어 3개의 Consumer가 같은 그룹이면, 메시지를 나눠서 처리
                group_id=settings.kafka_group_id,

                # 메시지 역직렬화 함수 (Deserializer)
                # Kafka는 메시지를 바이트 형태로 저장하므로,
                # 바이트 -> 문자열 -> JSON 객체로 변환 필요
                # lambda m: JSON.loads(m.decode('utf-8'))
                # 1. m.decode('utf-8'): 바이트를 UTF-8 문자열로 변환
                # 2. json.loads(): JSON 문자열을 Python dict로 파싱
                value_deserializer=lambda m: json.loads(m.decode('utf-8')),

                # 오프셋(Offset) 초기화 전략
                # 오프셋: Kafka 토픽에서 메시지의 위치를 나타내는 번호
                # 'earliest': 토픽의 맨 처음 메시지부터 읽기 (기존 메시지도 모두 처리)
                # 'latest': 새로 들어오는 메시지만 읽기
                auto_offset_reset='earliest',

                # 오프셋 자동 커밋 여부
                # True: 메시지를 읽으면 자동으로 오프셋 저장 (처리 여부와 무관)
                # False: 수동으로 commit() 호출 필요 (메시지 손실 방지)
                # 여기서는 간단하게 True 사용 (실무에서는 False 권장)
                enable_auto_commit=True,

                # 한 번에 가져올 최대 메시지 개수
                # 10개씩 묶어서 처리 (배치 처리로 성능 향상)
                max_poll_records=10
            )

            # 연결 성공 로그
            logger.info("Kafka Consumer 연결 성공!")

            # 메시지 수신 루프 (무한 루프)
            # consumer는 iterable 객체로, for문으로 메시지를 계속 가져옴
            for message in consumer:
                try:
                    # message.value: 역직렬화된 JSON 객체 (dict)
                    log_data = message.value

                    # 메시지 수신 로그 (어떤 소설의 리뷰인지 표시)
                    logger.info(f"메시지 수신: novelId={log_data.get('novelId')}")

                    # 리뷰 로그 처리 (감성 분석 + DB 저장)
                    process_review_log(log_data)

                except Exception as e:
                    # 개별 메시지 처리 중 에러 발생 시
                    # 한 메시지가 실패해도 다음 메시지는 계속 처리
                    logger.error(f"메시지 처리 에러: {e}")

        except Exception as e:
            # Kafka 연결 실패 시 (네트워크 오류, Kafka 서버 다운 등)
            retry_count += 1

            # 재시도 정보 로그 출력
            logger.error(
                f"Kafka 연결 실패 ({retry_count}/{max_retries}): {e}"
            )

            # 최대 재시도 횟수에 도달하지 않았으면 재시도
            if retry_count < max_retries:
                # 지수 백오프: 재시도 횟수에 비례하여 대기 시간 증가
                # 1회: 5초, 2회: 10초, 3회: 15초, ...
                import time
                wait_seconds = 5 * retry_count
                logger.info(f"{wait_seconds}초 후 재시도...")
                time.sleep(wait_seconds)
            else:
                # 최대 재시도 횟수 초과 시 포기
                logger.critical("Kafka Consumer 최대 재시도 초과")
                break


@app.on_event("startup")
def startup_event():
    """
    FastAPI 애플리케이션 시작 시 자동 실행되는 이벤트 핸들러

    FastAPI 서버가 시작되면 이 함수가 호출되어,
    Kafka Consumer를 백그라운드 스레드로 실행합니다.

    백그라운드 스레드 사용 이유:
    - FastAPI 서버는 HTTP 요청을 처리하는 메인 스레드에서 실행
    - Kafka Consumer는 별도의 스레드에서 무한 루프로 메시지 수신
    - 두 작업을 동시에 수행하기 위해 멀티스레딩 사용

    daemon=True 의미:
    - 데몬 스레드: 메인 프로그램이 종료되면 자동으로 종료되는 스레드
    - 메인 스레드(FastAPI)가 종료되면 Kafka Consumer 스레드도 함께 종료
    """
    # Thread 객체 생성
    # target: 스레드에서 실행할 함수
    # daemon=True: 데몬 스레드로 설정 (메인 종료 시 함께 종료)
    kafka_thread = Thread(target=start_kafka_consumer, daemon=True)

    # 스레드 시작
    # start() 호출 시 새로운 스레드가 생성되어 start_kafka_consumer() 실행
    kafka_thread.start()

    # 서비스 시작 로그
    logger.info("서비스 시작 완료")


# ===== API 엔드포인트 정의 =====

# Pydantic 모델: 요청 데이터의 구조와 유효성 검증
# BaseModel을 상속받으면 자동으로 타입 검증 수행
class AnalysisRequest(BaseModel):
    """
    수동 감성 분석 API 요청 DTO

    POST /api/analyze 요청 시 사용
    """
    text: str  # 분석할 텍스트 (필수 필드, 문자열 타입)


class NovelStatsRequest(BaseModel):
    """
    소설 통계 조회 API 요청 DTO

    POST /api/statistics 요청 시 사용
    """
    novel_id: int  # 소설 ID (필수 필드, 정수 타입)


@app.post("/api/analyze")
def analyze_text(request: AnalysisRequest):
    """
    수동 감성 분석 API

    Kafka를 거치지 않고 직접 텍스트를 전달받아 감성 분석 수행
    테스트 및 디버깅 용도로 사용

    요청 예시:
    POST /api/analyze
    {
        "text": "이 소설 정말 재미있어요!"
    }

    응답 예시:
    {
        "status": "SUCCESS",
        "data": {
            "sentiment": "POSITIVE",
            "score": 75.5,
            "positive_count": 3,
            "negative_count": 0
        }
    }

    Args:
        request: AnalysisRequest 객체 (FastAPI가 자동으로 JSON을 객체로 변환)

    Returns:
        dict: 분석 결과를 담은 응답 객체

    Raises:
        HTTPException: 분석 중 에러 발생 시 500 에러 반환
    """
    try:
        # 감성 분석 수행
        result = analyzer.analyze(request.text)

        # 성공 응답 반환
        # FastAPI가 자동으로 dict를 JSON으로 변환
        return {
            "status": "SUCCESS",
            "data": result
        }
    except Exception as e:
        # 에러 발생 시 로그 기록
        logger.error(f"분석 실패: {e}")

        # HTTP 500 에러 응답 반환
        # HTTPException은 FastAPI의 예외 클래스
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/statistics")
def get_statistics(request: NovelStatsRequest):
    """
    소설별 감성 분석 통계 조회 API

    특정 소설에 대한 모든 리뷰의 감성 분석 통계를 반환
    (긍정률, 부정률, 평균 점수 등)

    요청 예시:
    POST /api/statistics
    {
        "novel_id": 1
    }

    응답 예시:
    {
        "status": "SUCCESS",
        "data": {
            "total_reviews": 150,
            "positive_rate": 68.7,
            "negative_rate": 18.0,
            "neutral_rate": 13.3,
            "avg_score": 72.5
        }
    }

    Args:
        request: NovelStatsRequest 객체

    Returns:
        dict: 통계 정보를 담은 응답 객체

    Raises:
        HTTPException: 통계 조회 중 에러 발생 시 500 에러 반환
    """
    try:
        # 데이터베이스에서 통계 조회
        stats = db_manager.get_novel_statistics(request.novel_id)

        # 성공 응답 반환
        return {
            "status": "SUCCESS",
            "data": stats
        }
    except Exception as e:
        # 에러 발생 시 로그 기록
        logger.error(f"통계 조회 실패: {e}")

        # HTTP 500 에러 응답 반환
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/health")
def health_check():
    """
    헬스 체크 API

    서비스가 정상적으로 실행 중인지 확인하기 위한 엔드포인트
    Docker 컨테이너의 HEALTHCHECK나 로드 밸런서의 헬스 체크에서 사용

    요청 예시:
    GET /health

    응답 예시:
    {
        "status": "OK",
        "service": "Novel Insight Analysis"
    }

    Returns:
        dict: 헬스 체크 응답
    """
    return {
        "status": "OK",
        "service": "Novel Insight Analysis"
    }
