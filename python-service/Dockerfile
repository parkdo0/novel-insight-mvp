# ===================================
# Phase 5: BERT 감성 분석 Dockerfile
# ===================================
# Python 3.9 Slim 이미지 사용 (경량화)
FROM python:3.9-slim

# 메타데이터
LABEL maintainer="Novel Insight Team"
LABEL version="2.0-phase5"
LABEL description="Novel Insight Analysis Service with BERT Support"

# ===================================
# 1. 시스템 의존성 설치
# ===================================
# JDK: KoNLPy 의존성 (하위 호환성)
# wget, curl: 디버깅 및 헬스 체크
RUN apt-get update && apt-get install -y \
    default-jdk \
    wget \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 작업 디렉토리 설정
WORKDIR /app

# ===================================
# 2. Python 의존성 설치
# ===================================
# requirements.txt 먼저 복사 (Docker 레이어 캐싱 최적화)
# requirements.txt가 변경되지 않으면 이 레이어 재사용
COPY requirements.txt .

# pip 업그레이드 및 의존성 설치
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt

# ===================================
# 3. Phase 5: BERT 모델 캐시 설정
# ===================================
# Hugging Face Transformers 캐시 디렉토리 설정
# 모델을 로컬에 다운로드하여 재사용 (네트워크 비용 절감)
ENV TRANSFORMERS_CACHE=/app/.cache/huggingface
ENV HF_HOME=/app/.cache/huggingface

# 캐시 디렉토리 생성
RUN mkdir -p /app/.cache/huggingface

# ===================================
# 4. 소스 코드 복사
# ===================================
# 애플리케이션 파일 복사
COPY config.py .
COPY sentiment_analyzer.py .
COPY bert_sentiment_analyzer.py .
COPY database.py .
COPY main.py .

# ===================================
# 5. Phase 5: BERT 모델 사전 다운로드 (선택사항)
# ===================================
# WHY? 첫 실행 시 모델 다운로드 시간 절약 (1-2분)
# 프로덕션 환경에서는 사전 다운로드 권장
#
# 주석 해제 시 빌드 시간 증가 (약 2-3분)
# 하지만 런타임 시작 시간 단축 (즉시 서비스 가능)
#
# RUN python -c "from transformers import AutoTokenizer, AutoModelForSequenceClassification; \
#     tokenizer = AutoTokenizer.from_pretrained('beomi/KcELECTRA-base-v2022'); \
#     model = AutoModelForSequenceClassification.from_pretrained('beomi/KcELECTRA-base-v2022', num_labels=7);"

# ===================================
# 6. 환경 변수 설정
# ===================================
# Python 버퍼링 비활성화 (로그 즉시 출력)
ENV PYTHONUNBUFFERED=1

# 로그 레벨 (INFO, DEBUG, WARNING, ERROR)
ENV LOG_LEVEL=INFO

# Phase 5: BERT 사용 여부 (기본값: false, 하위 호환성)
# docker-compose.yml에서 오버라이드 가능
ENV USE_BERT=false

# ===================================
# 7. 헬스 체크 설정
# ===================================
# 30초마다 헬스 체크 실행
# 연속 3회 실패 시 컨테이너 unhealthy 상태
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8000/health || exit 1

# ===================================
# 8. FastAPI 실행
# ===================================
# uvicorn: ASGI 서버 (FastAPI 실행)
# --host 0.0.0.0: 모든 네트워크 인터페이스에서 접속 허용
# --port 8000: FastAPI 포트
# --workers 1: 워커 프로세스 개수 (CPU 코어 수에 따라 조정)
#   - BERT 사용 시: 1 (메모리 부족 방지)
#   - KoNLPy 사용 시: 2-4 (병렬 처리)
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]

# ===================================
# 빌드 명령어
# ===================================
# docker build -t novel-insight-python-service:phase5 .
#
# 실행 명령어 (BERT 사용)
# docker run -e USE_BERT=true -p 8000:8000 novel-insight-python-service:phase5
#
# 실행 명령어 (KoNLPy 사용, 하위 호환성)
# docker run -e USE_BERT=false -p 8000:8000 novel-insight-python-service:phase5
