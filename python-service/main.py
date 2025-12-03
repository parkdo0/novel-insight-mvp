"""
Novel Insight - 리뷰 감성 분석 서비스

실시간으로 Kafka에서 리뷰 로그를 수집하여 감성 분석 후 MySQL에 저장
"""
import json
import logging
from threading import Thread
from kafka import KafkaConsumer
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from config import settings
from sentiment_analyzer import get_analyzer
from database import get_db_manager

# 로깅 설정
logging.basicConfig(
    level=getattr(logging, settings.log_level),
    format='%(asctime)s [%(levelname)s] %(name)s - %(message)s'
)
logger = logging.getLogger(__name__)

app = FastAPI(title="Novel Insight Analysis Service", version="1.0.0")

# 싱글톤 인스턴스
analyzer = get_analyzer()
db_manager = get_db_manager()


def process_review_log(log_data: dict) -> bool:
    """
    리뷰 로그 처리 (감성 분석 + DB 저장)

    Args:
        log_data: Kafka에서 받은 로그 데이터

    Returns:
        성공 여부
    """
    try:
        novel_id = log_data.get('novelId')
        author_id = log_data.get('authorId')
        content = log_data.get('content', '')

        if not all([novel_id, author_id, content]):
            logger.warning(f"필수 데이터 누락: {log_data}")
            return False

        # 감성 분석
        analysis_result = analyzer.analyze(content)

        logger.info(
            f"✨ 분석 완료 - novelId:{novel_id}, "
            f"sentiment:{analysis_result['sentiment']}, "
            f"score:{analysis_result['score']}"
        )

        # DB 저장
        saved_id = db_manager.save_analysis(
            novel_id=novel_id,
            author_id=author_id,
            content=content,
            analysis_result=analysis_result
        )

        return saved_id is not None

    except Exception as e:
        logger.error(f"❌ 리뷰 처리 실패: {e}", exc_info=True)
        return False


def start_kafka_consumer():
    """Kafka Consumer 실행 (백그라운드)"""
    logger.info("🔥 Kafka Consumer 시작...")

    retry_count = 0
    max_retries = 5

    while retry_count < max_retries:
        try:
            consumer = KafkaConsumer(
                settings.kafka_topic,
                bootstrap_servers=settings.kafka_bootstrap_servers,
                group_id=settings.kafka_group_id,
                value_deserializer=lambda m: json.loads(m.decode('utf-8')),
                auto_offset_reset='earliest',
                enable_auto_commit=True,
                max_poll_records=10
            )

            logger.info("✅ Kafka Consumer 연결 성공!")

            for message in consumer:
                try:
                    log_data = message.value
                    logger.info(f"📩 메시지 수신: novelId={log_data.get('novelId')}")
                    process_review_log(log_data)
                except Exception as e:
                    logger.error(f"❌ 메시지 처리 에러: {e}")

        except Exception as e:
            retry_count += 1
            logger.error(
                f"❌ Kafka 연결 실패 ({retry_count}/{max_retries}): {e}"
            )
            if retry_count < max_retries:
                import time
                time.sleep(5 * retry_count)  # 지수 백오프
            else:
                logger.critical("❌ Kafka Consumer 최대 재시도 초과")
                break


@app.on_event("startup")
def startup_event():
    """서버 시작 시 Kafka Consumer 백그라운드 실행"""
    kafka_thread = Thread(target=start_kafka_consumer, daemon=True)
    kafka_thread.start()
    logger.info("🚀 서비스 시작 완료")


# ===== API Endpoints =====

class AnalysisRequest(BaseModel):
    """분석 요청 DTO"""
    text: str


class NovelStatsRequest(BaseModel):
    """소설 통계 요청 DTO"""
    novel_id: int


@app.post("/api/analyze")
def analyze_text(request: AnalysisRequest):
    """수동 감성 분석 API"""
    try:
        result = analyzer.analyze(request.text)
        return {
            "status": "SUCCESS",
            "data": result
        }
    except Exception as e:
        logger.error(f"분석 실패: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/api/statistics")
def get_statistics(request: NovelStatsRequest):
    """소설 감성 분석 통계 조회"""
    try:
        stats = db_manager.get_novel_statistics(request.novel_id)
        return {
            "status": "SUCCESS",
            "data": stats
        }
    except Exception as e:
        logger.error(f"통계 조회 실패: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/health")
def health_check():
    """헬스 체크"""
    return {
        "status": "OK",
        "service": "Novel Insight Analysis"
    }
