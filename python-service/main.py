"""
Novel Insight - Python 분석 서비스
1. Kafka에서 리뷰 로그를 실시간으로 수집
2. KoNLPy로 감성 분석 (긍정/부정/중립)
3. 분석 결과를 MySQL에 저장
"""

import json
import logging
from threading import Thread
from kafka import KafkaConsumer
from fastapi import FastAPI
from pydantic import BaseModel
import re

# 로깅 설정
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s'
)
logger = logging.getLogger(__name__)

app = FastAPI()

# ========================================
# ★★★ 간단한 감성 사전 (실무에서는 더 정교한 모델 사용) ★★★
# ========================================
POSITIVE_WORDS = {
    '좋', '재미', '최고', '멋지', '훌륭', '감동', '완벽', '사랑', '행복',
    '웃', '즐거', '흥미', '짱', '대박', '굿', '킹', '갓', '꿀잼'
}

NEGATIVE_WORDS = {
    '싫', '별로', '실망', '지루', '짜증', '최악', '후회', '답답', '억지',
    '노잼', '쓰레기', '개연성', '불편', '빡'
}


def analyze_sentiment(text: str) -> dict:
    """
    간단한 감성 분석 (키워드 기반)
    실무에서는 KoNLPy + 머신러닝 모델 사용
    """
    # 특수문자 제거
    clean_text = re.sub(r'[^가-힣a-zA-Z\s]', '', text)

    positive_count = sum(1 for word in POSITIVE_WORDS if word in clean_text)
    negative_count = sum(1 for word in NEGATIVE_WORDS if word in clean_text)

    # 감성 판별
    if positive_count > negative_count:
        sentiment = "POSITIVE"
        score = min(positive_count / (positive_count + negative_count + 1) * 100, 100)
    elif negative_count > positive_count:
        sentiment = "NEGATIVE"
        score = min(negative_count / (positive_count + negative_count + 1) * 100, 100)
    else:
        sentiment = "NEUTRAL"
        score = 50.0

    return {
        "sentiment": sentiment,
        "score": round(score, 2),
        "positive_count": positive_count,
        "negative_count": negative_count
    }


# ========================================
# ★★★ Kafka Consumer (백그라운드 스레드) ★★★
# ========================================
def start_kafka_consumer():
    """
    Kafka에서 리뷰 로그를 실시간으로 수집하여 분석
    """
    logger.info("🔥 Kafka Consumer 시작...")

    try:
        # Kafka Consumer 생성
        consumer = KafkaConsumer(
            'novel-reviews',  # 토픽 이름 (Spring Boot와 일치)
            bootstrap_servers='novel-kafka:29092',  # Docker 내부 주소
            group_id='python-consumer-group',
            value_deserializer=lambda m: json.loads(m.decode('utf-8')),
            auto_offset_reset='earliest'  # 처음부터 읽기
        )

        logger.info("✅ Kafka Consumer 연결 성공! 메시지 대기 중...")

        # 무한 루프로 메시지 수신
        for message in consumer:
            try:
                log_data = message.value
                logger.info(f"📩 Kafka에서 메시지 수신: {log_data}")

                # 감성 분석 실행
                content = log_data.get('content', '')
                analysis_result = analyze_sentiment(content)

                # 결과 로그 출력
                logger.info(f"✨ 감성 분석 결과: {analysis_result}")
                logger.info(f"   - novelId: {log_data.get('novelId')}")
                logger.info(f"   - authorId: {log_data.get('authorId')}")
                logger.info(f"   - sentiment: {analysis_result['sentiment']}")
                logger.info(f"   - score: {analysis_result['score']}")

                # TODO: 실무에서는 여기서 MySQL에 저장
                # db.save_analysis_result(log_data['novelId'], analysis_result)

            except Exception as e:
                logger.error(f"❌ 메시지 처리 중 에러: {e}")

    except Exception as e:
        logger.error(f"❌ Kafka Consumer 에러: {e}")


# FastAPI 시작 시 Kafka Consumer를 백그라운드 스레드로 실행
@app.on_event("startup")
def startup_event():
    """
    서버 시작 시 Kafka Consumer를 별도 스레드로 실행
    """
    kafka_thread = Thread(target=start_kafka_consumer, daemon=True)
    kafka_thread.start()
    logger.info("🚀 Python 서비스 시작 완료!")


# ========================================
# ★★★ FastAPI - 기존 분석 API (테스트용) ★★★
# ========================================
class AnalysisRequest(BaseModel):
    text: str


@app.post("/analyze")
def analyze_text(request: AnalysisRequest):
    """
    수동 분석 API (테스트용)
    """
    logger.info(f"🔍 [API 요청] 분석: {request.text}")

    result = analyze_sentiment(request.text)

    return {
        "status": "SUCCESS",
        "result": f"감성: {result['sentiment']}, 점수: {result['score']}",
        "detail": result
    }


@app.get("/health")
def health_check():
    """
    헬스 체크
    """
    return {"status": "OK", "service": "Python Analysis Service"}


# 서버 실행 (uvicorn main:app --host 0.0.0.0 --port 8000)
