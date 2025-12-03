FROM python:3.9-slim

# JDK 설치 (KoNLPy 의존성)
RUN apt-get update && apt-get install -y \
    default-jdk \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# requirements 설치
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 소스 코드 복사
COPY config.py .
COPY sentiment_analyzer.py .
COPY database.py .
COPY main.py .

# FastAPI 실행
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
