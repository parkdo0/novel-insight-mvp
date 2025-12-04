# Novel Insight - Python 감정 분석 서비스

웹소설 리뷰의 **실시간 감정 분석**을 수행하는 Python 마이크로서비스입니다.

---

## 📋 목차

1. [프로젝트 개요](#프로젝트-개요)
2. [프로젝트 구조](#프로젝트-구조)
3. [기술 스택](#기술-스택)
4. [핵심 개념](#핵심-개념)
5. [개발 패턴](#개발-패턴)
6. [환경 설정 및 실행](#환경-설정-및-실행)

---

## 프로젝트 개요

FastAPI 기반의 **비동기 이벤트 드리븐 아키텍처**로 설계된 감정 분석 마이크로서비스입니다.

### 주요 기능

- **실시간 리뷰 분석**: Kafka Consumer로 리뷰 이벤트 구독
- **한국어 감정 분석**: KoNLPy + 형태소 분석
- **결과 저장**: SQLAlchemy ORM으로 MySQL 연동
- **REST API**: FastAPI로 수동 분석 API 제공
- **헬스 체크**: /health 엔드포인트

### 시스템 흐름

```
[Spring Boot 백엔드]
    ↓ (리뷰 작성 시 Kafka 메시지 발행)
[Kafka Topic: review.created]
    ↓ (이 서비스가 메시지 구독)
[Python Kafka Consumer]
    ↓ (KoNLPy로 감정 분석)
[Sentiment Analyzer]
    ↓ (분석 결과 저장)
[MySQL Database]
```

---

## 프로젝트 구조

```
python-service/
├── main.py                 # FastAPI 앱 + Kafka Consumer 진입점
├── config.py               # 환경 설정 (Pydantic Settings)
├── sentiment_analyzer.py   # 감정 분석 엔진 (KoNLPy)
├── database.py             # SQLAlchemy ORM + DB 연결 관리
├── requirements.txt        # Python 의존성 목록
├── Dockerfile              # Docker 이미지 빌드 설정
└── README.md               # 이 문서
```

### 파일별 역할

| 파일 | 역할 | 주요 책임 |
|------|------|-----------|
| **main.py** | 애플리케이션 진입점 | FastAPI 앱 초기화, Kafka Consumer 실행, API 엔드포인트 정의 |
| **config.py** | 환경 설정 관리 | .env 파일 로드, Pydantic Settings로 타입 안전한 설정 |
| **sentiment_analyzer.py** | 감정 분석 로직 | KoNLPy Okt 형태소 분석기, 긍정/부정 단어 사전 기반 스코어링 |
| **database.py** | 데이터베이스 연동 | SQLAlchemy ORM, Connection Pool 관리, CRUD 연산 |
| **requirements.txt** | 의존성 관리 | pip로 설치할 라이브러리 목록 |
| **Dockerfile** | 컨테이너화 | Docker 이미지 빌드 스크립트 |

---

## 기술 스택

### 핵심 기술

| 기술 | 버전 | 용도 | 비고 |
|------|------|------|------|
| **Python** | 3.11+ | 주 언어 | 타입 힌트 지원 |
| **FastAPI** | 0.104.1 | 웹 프레임워크 | 비동기 처리, 자동 문서화 |
| **Uvicorn** | 0.24.0 | ASGI 서버 | FastAPI 실행 엔진 |
| **Pydantic** | 2.5.0 | 데이터 검증 | DTO, Settings 관리 |

### 데이터 처리

| 기술 | 용도 | 설명 |
|------|------|------|
| **KoNLPy** | 한국어 NLP | 형태소 분석 (Okt 엔진) |
| **Pandas** | 데이터 분석 | DataFrame 처리 |
| **NumPy** | 수치 연산 | 배열 연산 |

### 메시징 & 데이터베이스

| 기술 | 용도 | 특징 |
|------|------|------|
| **Kafka** | 이벤트 스트리밍 | kafka-python 라이브러리 |
| **SQLAlchemy** | ORM | Connection Pool, 트랜잭션 관리 |
| **PyMySQL** | MySQL 드라이버 | SQLAlchemy의 DB 어댑터 |

---

## 핵심 개념

### 1. FastAPI - 고성능 비동기 웹 프레임워크

**왜 Flask가 아닌 FastAPI인가?**

| 비교 항목 | Flask | FastAPI |
|-----------|-------|---------|
| **성능** | 동기 (WSGI) | 비동기 (ASGI) |
| **속도** | 느림 | **빠름 (Node.js/Go 수준)** |
| **타입 힌트** | 미지원 | **완벽 지원** |
| **자동 문서화** | 없음 | **Swagger UI 자동 생성** |
| **데이터 검증** | 수동 | **Pydantic 자동 검증** |

**실무 적용**:

```python
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

# FastAPI 앱 생성
app = FastAPI(
    title="Novel Insight Analysis Service",
    description="리뷰 감정 분석 API",
    version="1.0.0"
)

# Pydantic 모델: 자동으로 타입 검증
class AnalysisRequest(BaseModel):
    text: str  # 필수 필드
    novel_id: int = None  # Optional 필드

class AnalysisResponse(BaseModel):
    sentiment: str  # "긍정" | "부정" | "중립"
    score: float
    keywords: list[str]

# API 엔드포인트 정의
@app.post("/analyze", response_model=AnalysisResponse)
async def analyze_text(request: AnalysisRequest):
    """
    리뷰 텍스트를 분석하여 감정을 반환합니다.

    - **text**: 분석할 리뷰 텍스트 (필수)
    - **novel_id**: 소설 ID (선택)
    """
    if not request.text:
        raise HTTPException(status_code=400, detail="텍스트가 비어있습니다")

    # 감정 분석 수행
    analyzer = get_analyzer()
    result = analyzer.analyze(request.text)

    return AnalysisResponse(
        sentiment=result["sentiment"],
        score=result["score"],
        keywords=result["keywords"]
    )
```

**FastAPI의 강점**:
1. **자동 문서화**: http://localhost:8000/docs 접속 시 Swagger UI 자동 생성
2. **타입 안전성**: Pydantic으로 요청/응답 자동 검증
3. **비동기 처리**: `async def`로 동시 요청 처리 (I/O Bound 작업에 유리)
4. **의존성 주입**: `Depends()`로 DI 패턴 지원

**참고 파일**: `main.py:80-120`

---

### 2. KoNLPy - 한국어 자연어 처리

**왜 한국어 NLP가 필요한가?**

영어와 달리 한국어는:
- **교착어**: 조사/어미가 붙어서 의미가 변함 (예: "좋다" → "좋아요", "좋았어요")
- **띄어쓰기 모호**: "아버지가방에들어가신다" → "아버지가 방에 들어가신다" vs "아버지 가방에 들어가신다"
- **형태소 분석 필수**: 단어를 의미 단위로 쪼개야 감정 분석 가능

**KoNLPy Okt 엔진**:

```python
from konlpy.tag import Okt

# Okt: Open Korean Text의 약자 (트위터에서 개발)
# 빠르고 정확한 한국어 형태소 분석기
okt = Okt()

# 형태소 분석 예시
text = "이 소설 정말 재밌어요!"
morphs = okt.morphs(text)
print(morphs)
# 출력: ['이', '소설', '정말', '재밌어요', '!']

# 품사 태깅
pos = okt.pos(text)
print(pos)
# 출력: [('이', 'Determiner'), ('소설', 'Noun'), ('정말', 'Adverb'),
#        ('재밌어요', 'Adjective'), ('!', 'Punctuation')]

# 명사 추출
nouns = okt.nouns(text)
print(nouns)
# 출력: ['소설']
```

**감정 분석 프로세스**:

```python
class SentimentAnalyzer:
    def __init__(self):
        self.okt = Okt()

        # 감정 사전 (실무에서는 수천 개의 단어 사용)
        self.positive_words = {
            '좋다': 1.0, '재밌다': 1.0, '훌륭하다': 1.2, '최고': 1.5,
            '감동': 1.0, '멋지다': 1.0, '추천': 0.8
        }
        self.negative_words = {
            '싫다': -1.0, '지루하다': -1.0, '별로': -0.8, '최악': -1.5,
            '실망': -1.0, '아쉽다': -0.5
        }

    def analyze(self, text: str) -> dict:
        # 1단계: 형태소 분석
        morphs = self.okt.morphs(text)

        # 2단계: 감정 스코어 계산
        score = 0.0
        keywords = []

        for morph in morphs:
            if morph in self.positive_words:
                score += self.positive_words[morph]
                keywords.append(morph)
            elif morph in self.negative_words:
                score += self.negative_words[morph]
                keywords.append(morph)

        # 3단계: 감정 분류
        if score > 0.5:
            sentiment = "긍정"
        elif score < -0.5:
            sentiment = "부정"
        else:
            sentiment = "중립"

        return {
            "sentiment": sentiment,
            "score": score,
            "keywords": keywords
        }
```

**KoNLPy 엔진 비교**:

| 엔진 | 속도 | 정확도 | 용도 |
|------|------|--------|------|
| **Okt** | 빠름 | 보통 | 실시간 분석 (본 프로젝트) |
| **Komoran** | 보통 | 높음 | 정확도 중요한 분석 |
| **Mecab** | 매우 빠름 | 높음 | 대용량 처리 (별도 설치 필요) |
| **Hannanum** | 느림 | 높음 | 학술 연구 |

**참고 파일**: `sentiment_analyzer.py`

---

### 3. Kafka Consumer - 이벤트 드리븐 아키텍처

**왜 REST API가 아닌 Kafka인가?**

**REST API 방식의 문제점**:
```python
# Spring Boot에서 리뷰 저장 후 Python 호출 (동기 방식)
review = reviewRepository.save(review)
pythonClient.analyzeReview(review.id)  # 블로킹! (3초 소요)
return ResponseEntity.ok(review)  # 사용자는 3초 대기...
```

**Kafka 방식의 장점**:
```python
# Spring Boot: 리뷰 저장 후 즉시 응답 (비동기 방식)
review = reviewRepository.save(review)
kafkaProducer.send("review.created", review)  # 논블로킹! (1ms)
return ResponseEntity.ok(review)  # 사용자는 즉시 응답 받음

# Python: 백그라운드에서 메시지 소비
# (사용자와 무관하게 분석 수행)
```

**Kafka Consumer 구현**:

```python
from kafka import KafkaConsumer
import json

# Consumer 생성
consumer = KafkaConsumer(
    'review.created',  # 구독할 Topic
    bootstrap_servers=['localhost:9092'],  # Kafka 브로커 주소
    auto_offset_reset='latest',  # 최신 메시지부터 소비
    enable_auto_commit=True,  # Offset 자동 커밋
    group_id='python-analysis-group',  # Consumer Group
    value_deserializer=lambda x: json.loads(x.decode('utf-8'))  # JSON 파싱
)

# 메시지 소비 루프 (무한 루프)
for message in consumer:
    try:
        # 메시지 데이터 추출
        review_data = message.value
        review_id = review_data['id']
        text = review_data['content']

        print(f"[Kafka] 리뷰 수신: {review_id}")

        # 감정 분석 수행
        analyzer = get_analyzer()
        result = analyzer.analyze(text)

        # DB에 결과 저장
        db_manager = get_db_manager()
        db_manager.save_analysis(review_id, result)

        print(f"[Kafka] 분석 완료: {review_id} → {result['sentiment']}")

    except Exception as e:
        print(f"[ERROR] 메시지 처리 실패: {e}")
        # 메시지 처리 실패 시 로그만 남기고 다음 메시지 처리
```

**Consumer Group의 중요성**:

```
[Kafka Topic: review.created]
      ↓
   Partition 0 → Consumer A (group: python-analysis-group)
   Partition 1 → Consumer B (group: python-analysis-group)
   Partition 2 → Consumer C (group: python-analysis-group)
```

- **스케일 아웃**: 파티션 수만큼 Consumer 인스턴스 추가 가능
- **고가용성**: 한 Consumer가 다운되면 다른 Consumer가 파티션 재할당
- **메시지 순서 보장**: 같은 파티션 내에서는 순서 보장

**Offset 관리**:
- `enable_auto_commit=True`: 메시지 소비 후 자동으로 Offset 커밋
- Offset: "어디까지 읽었는지" 저장하는 포인터 (Kafka가 관리)
- 재시작 시 마지막 Offset부터 이어서 소비

**참고 파일**: `main.py:130-180`

---

### 4. SQLAlchemy ORM - 데이터베이스 추상화

**왜 직접 SQL이 아닌 ORM인가?**

**SQL 직접 사용의 문제점**:
```python
# SQL Injection 위험
cursor.execute(f"SELECT * FROM users WHERE id = {user_id}")  # 위험!

# 타입 안전성 없음
result = cursor.fetchone()
print(result[0])  # 컬럼 순서 바뀌면 버그!

# 보일러플레이트 코드
connection = pymysql.connect(host='localhost', user='root', ...)
try:
    cursor = connection.cursor()
    cursor.execute("INSERT INTO ...")
    connection.commit()
finally:
    cursor.close()
    connection.close()
```

**SQLAlchemy ORM의 장점**:
```python
from sqlalchemy import Column, Integer, String, Float, DateTime
from sqlalchemy.orm import declarative_base, Session

Base = declarative_base()

# ORM 모델 정의 (Python 클래스 = DB 테이블)
class ReviewAnalysis(Base):
    __tablename__ = 'review_analysis'  # 테이블 명

    id = Column(Integer, primary_key=True, autoincrement=True)
    review_id = Column(Integer, nullable=False, unique=True)
    sentiment = Column(String(20), nullable=False)
    score = Column(Float, nullable=False)
    keywords = Column(String(500))
    analyzed_at = Column(DateTime, nullable=False)

    def __repr__(self):
        return f"<ReviewAnalysis(id={self.id}, sentiment={self.sentiment})>"

# CRUD 연산 (타입 안전)
class DatabaseManager:
    def __init__(self, session: Session):
        self.session = session

    def save_analysis(self, review_id: int, result: dict):
        # ORM으로 데이터 삽입
        analysis = ReviewAnalysis(
            review_id=review_id,
            sentiment=result['sentiment'],
            score=result['score'],
            keywords=','.join(result['keywords']),
            analyzed_at=datetime.now()
        )

        self.session.add(analysis)  # INSERT
        self.session.commit()  # 트랜잭션 커밋

        print(f"[DB] 저장 완료: review_id={review_id}")

    def get_analysis(self, review_id: int) -> ReviewAnalysis:
        # ORM으로 데이터 조회 (SQL Injection 불가능)
        return self.session.query(ReviewAnalysis)\
            .filter(ReviewAnalysis.review_id == review_id)\
            .first()  # SELECT * FROM review_analysis WHERE review_id = ?
```

**Connection Pool 관리**:

```python
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

# Engine 생성 (Connection Pool 자동 관리)
engine = create_engine(
    'mysql+pymysql://root:1234@localhost:3306/novel_insight',
    pool_size=10,  # 최대 10개 커넥션 유지
    max_overflow=5,  # 초과 시 최대 5개 추가 생성
    pool_recycle=3600,  # 1시간마다 커넥션 재생성 (MySQL timeout 방지)
    echo=False  # SQL 로그 출력 (디버깅 시 True)
)

# SessionMaker: Session Factory
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# Session 사용 패턴 (Context Manager)
def save_data():
    session = SessionLocal()
    try:
        # 데이터베이스 작업
        analysis = ReviewAnalysis(...)
        session.add(analysis)
        session.commit()  # 성공 시 커밋
    except Exception as e:
        session.rollback()  # 실패 시 롤백
        print(f"[ERROR] DB 저장 실패: {e}")
    finally:
        session.close()  # 커넥션 반환 (Pool로 돌아감)
```

**ORM vs Raw SQL**:

| 비교 항목 | ORM (SQLAlchemy) | Raw SQL |
|-----------|------------------|---------|
| **타입 안전성** | ✅ Python 클래스로 보장 | ❌ 문자열 쿼리 |
| **SQL Injection** | ✅ 자동 방어 | ❌ 수동 이스케이프 필요 |
| **가독성** | ✅ Python 코드처럼 읽힘 | ❌ SQL 문자열 복잡 |
| **DB 변경** | ✅ 코드 수정 최소 | ❌ 전체 SQL 수정 |
| **성능** | ❌ 약간 느림 | ✅ 최적화 가능 |

**언제 Raw SQL을 쓰나?**
- 복잡한 통계 쿼리 (GROUP BY, HAVING, 서브쿼리)
- 대용량 Bulk Insert (10만 건 이상)
- 성능 최적화가 극도로 중요한 경우

**참고 파일**: `database.py`

---

### 5. Pydantic Settings - 환경 설정 관리

**왜 환경 변수를 사용하는가?**

**하드코딩의 문제점**:
```python
# ❌ 나쁜 예: 하드코딩
DATABASE_URL = "mysql://root:1234@localhost:3306/novel_insight"
KAFKA_SERVERS = ["localhost:9092"]

# 문제:
# 1. 개발/스테이징/프로덕션 환경마다 다른 값
# 2. 민감 정보 (비밀번호) 코드에 노출
# 3. 변경 시 코드 수정 필요
```

**Pydantic Settings 패턴**:

```python
# config.py
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    # 데이터베이스 설정
    DB_HOST: str = "localhost"
    DB_PORT: int = 3306
    DB_USER: str = "root"
    DB_PASSWORD: str
    DB_NAME: str = "novel_insight"

    # Kafka 설정
    KAFKA_BOOTSTRAP_SERVERS: str = "localhost:9092"
    KAFKA_TOPIC: str = "review.created"
    KAFKA_GROUP_ID: str = "python-analysis-group"

    # 애플리케이션 설정
    LOG_LEVEL: str = "INFO"

    # .env 파일 자동 로드
    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"

    # 계산된 속성 (Property)
    @property
    def database_url(self) -> str:
        return f"mysql+pymysql://{self.DB_USER}:{self.DB_PASSWORD}@{self.DB_HOST}:{self.DB_PORT}/{self.DB_NAME}"

# 싱글톤 패턴으로 전역 설정 객체 생성
settings = Settings()

# 사용
print(settings.database_url)  # 자동으로 .env 값 로드
```

**.env 파일**:
```env
# 개발 환경 (.env.development)
DB_HOST=localhost
DB_PORT=3306
DB_USER=root
DB_PASSWORD=dev1234
DB_NAME=novel_insight_dev

KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# 프로덕션 환경 (.env.production)
DB_HOST=prod-db.example.com
DB_PORT=3306
DB_USER=prod_user
DB_PASSWORD=prod_secure_password_123!
DB_NAME=novel_insight_prod

KAFKA_BOOTSTRAP_SERVERS=kafka1.prod.com:9092,kafka2.prod.com:9092
```

**Pydantic의 장점**:
1. **타입 검증**: `DB_PORT`가 문자열이면 자동으로 int로 변환
2. **기본값**: 값이 없으면 기본값 사용
3. **필수 값 체크**: `DB_PASSWORD`처럼 기본값 없으면 에러
4. **IDE 자동완성**: `settings.` 입력 시 모든 속성 표시

**참고 파일**: `config.py`

---

### 6. 백그라운드 스레드 - Kafka Consumer 실행

**왜 별도 스레드가 필요한가?**

FastAPI는 HTTP 요청을 처리하는 메인 스레드가 있음. Kafka Consumer는 **무한 루프**로 메시지를 기다리므로, 메인 스레드에서 실행하면 FastAPI가 멈춤!

**잘못된 구현**:
```python
app = FastAPI()

@app.get("/health")
def health():
    return {"status": "ok"}

# ❌ 메인 스레드에서 실행하면 FastAPI가 블로킹됨!
for message in consumer:  # 무한 루프
    process(message)

# 이 아래 코드는 절대 실행되지 않음!
```

**올바른 구현 (백그라운드 스레드)**:
```python
from threading import Thread

app = FastAPI()

def kafka_consumer_loop():
    """백그라운드에서 실행될 Kafka Consumer"""
    consumer = KafkaConsumer(...)

    print("[Kafka] Consumer 시작!")

    for message in consumer:  # 무한 루프 (별도 스레드에서 실행)
        try:
            process(message)
        except Exception as e:
            print(f"[ERROR] {e}")

# FastAPI 앱 시작 시 백그라운드 스레드 실행
@app.on_event("startup")
def startup_event():
    """FastAPI 서버 시작 시 호출"""
    # 데몬 스레드: 메인 프로세스 종료 시 자동 종료
    thread = Thread(target=kafka_consumer_loop, daemon=True)
    thread.start()
    print("[INFO] Kafka Consumer 스레드 시작!")

@app.get("/health")
def health():
    return {"status": "ok"}  # 정상 동작!
```

**데몬 스레드 vs 일반 스레드**:

| 구분 | 데몬 스레드 (`daemon=True`) | 일반 스레드 |
|------|----------------------------|------------|
| **종료 시점** | 메인 프로세스 종료 시 자동 종료 | 작업 완료까지 대기 |
| **사용 사례** | 백그라운드 작업 (로깅, 모니터링) | 중요한 작업 (파일 저장) |

**참고 파일**: `main.py:200-240`

---

## 개발 패턴

### 1. 싱글톤 패턴 (Singleton Pattern)

**왜 싱글톤인가?**
- Sentiment Analyzer: 한 번만 초기화 (Okt 엔진 로딩 시간 절약)
- Database Manager: Connection Pool은 전역에서 하나만 존재해야 함
- Settings: 환경 설정은 전역 상태

**구현 예시**:
```python
# sentiment_analyzer.py
_analyzer_instance = None

def get_analyzer() -> SentimentAnalyzer:
    """싱글톤 패턴: 전역에서 하나의 인스턴스만 사용"""
    global _analyzer_instance
    if _analyzer_instance is None:
        print("[INFO] SentimentAnalyzer 초기화 중...")
        _analyzer_instance = SentimentAnalyzer()
        print("[INFO] SentimentAnalyzer 준비 완료!")
    return _analyzer_instance

# 사용
analyzer = get_analyzer()  # 첫 호출 시 초기화
analyzer2 = get_analyzer()  # 두 번째 호출은 기존 인스턴스 반환
assert analyzer is analyzer2  # True (같은 객체)
```

---

### 2. 의존성 주입 (Dependency Injection)

FastAPI에서 `Depends()`를 사용한 DI 패턴:

```python
from fastapi import Depends

def get_db_session():
    """DB Session을 생성하고 자동으로 정리하는 의존성"""
    session = SessionLocal()
    try:
        yield session  # 컨트롤러에 전달
    finally:
        session.close()  # 요청 종료 시 자동 닫기

@app.post("/analyze")
def analyze(request: AnalysisRequest, db: Session = Depends(get_db_session)):
    """
    db 파라미터는 FastAPI가 자동으로 주입
    요청 종료 시 자동으로 session.close() 호출
    """
    result = analyzer.analyze(request.text)

    # DB에 저장
    analysis = ReviewAnalysis(...)
    db.add(analysis)
    db.commit()

    return result
```

---

### 3. 에러 핸들링

**단계별 에러 처리**:

```python
def kafka_consumer_loop():
    consumer = KafkaConsumer(...)

    for message in consumer:
        try:
            # 메시지 파싱
            review_data = message.value

            # 감정 분석
            result = analyzer.analyze(review_data['content'])

            # DB 저장
            db_manager.save_analysis(review_data['id'], result)

        except KeyError as e:
            # 메시지 포맷 오류
            print(f"[ERROR] 메시지 필드 누락: {e}")
        except Exception as e:
            # 알 수 없는 오류
            print(f"[ERROR] 처리 실패: {e}")
            # 메시지는 건너뛰고 다음 메시지 처리 (무한 재시도 방지)
```

---

## 환경 설정 및 실행

### 1. 사전 요구사항

- Python 3.11+
- Java 11+ (KoNLPy의 형태소 분석기가 JVM 필요)
- MySQL 8.0
- Kafka 3.x

### 2. 의존성 설치

```bash
# 프로젝트 디렉토리로 이동
cd python-service

# 가상환경 생성 (권장)
python -m venv venv

# 가상환경 활성화
# Windows
venv\Scripts\activate
# Linux/Mac
source venv/bin/activate

# 의존성 설치
pip install -r requirements.txt
```

### 3. 환경 변수 설정

`.env` 파일 생성:
```env
# Database
DB_HOST=localhost
DB_PORT=3306
DB_USER=root
DB_PASSWORD=1234
DB_NAME=novel_insight

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_TOPIC=review.created
KAFKA_GROUP_ID=python-analysis-group

# Application
LOG_LEVEL=INFO
```

### 4. Java 설치 확인

```bash
# KoNLPy는 Java가 필요합니다
java -version
# 출력 예: openjdk version "11.0.12"

# Java가 없으면 설치
# Ubuntu
sudo apt install openjdk-11-jdk

# macOS
brew install openjdk@11
```

### 5. 애플리케이션 실행

```bash
# Uvicorn으로 FastAPI 실행
uvicorn main:app --host 0.0.0.0 --port 8000 --reload

# 또는
python main.py
```

### 6. API 문서 확인

브라우저에서 접속:
- Swagger UI: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc

### 7. Docker로 실행

```bash
# Docker 이미지 빌드
docker build -t python-analysis-service .

# 컨테이너 실행
docker run -d \
  --name python-service \
  -p 8000:8000 \
  --env-file .env \
  python-analysis-service
```

---

## API 엔드포인트

### 1. 헬스 체크

```bash
GET /health

# 응답
{
  "status": "ok",
  "timestamp": "2025-01-15T10:30:00"
}
```

### 2. 수동 감정 분석

```bash
POST /analyze
Content-Type: application/json

{
  "text": "이 소설 정말 재밌어요! 강력 추천합니다.",
  "novel_id": 123
}

# 응답
{
  "sentiment": "긍정",
  "score": 2.3,
  "keywords": ["재밌어요", "추천"]
}
```

### 3. 리뷰 분석 결과 조회

```bash
GET /analysis/{review_id}

# 응답
{
  "review_id": 456,
  "sentiment": "긍정",
  "score": 1.5,
  "keywords": ["좋아요", "감동"],
  "analyzed_at": "2025-01-15T10:25:30"
}
```

---

## 성능 최적화

### 1. Connection Pool 설정

```python
# database.py
engine = create_engine(
    settings.database_url,
    pool_size=10,  # 기본 10개 커넥션
    max_overflow=5,  # 최대 15개까지 확장
    pool_pre_ping=True,  # 커넥션 유효성 자동 체크
    pool_recycle=3600  # 1시간마다 재생성
)
```

### 2. Kafka Consumer 병렬 처리

```bash
# 3개 인스턴스 실행 (Kafka 파티션 3개인 경우)
docker-compose up --scale python-service=3
```

### 3. 비동기 DB 저장 (선택적)

```python
# asyncio + aiomysql 사용 시
async def save_analysis_async(review_id, result):
    async with async_session() as session:
        analysis = ReviewAnalysis(...)
        session.add(analysis)
        await session.commit()
```

---

## 트러블슈팅

### 1. KoNLPy 설치 오류

```
ERROR: Failed to build JPype1
```

**해결**: Java 및 빌드 도구 설치
```bash
# Ubuntu
sudo apt install openjdk-11-jdk python3-dev build-essential

# macOS
brew install openjdk@11

# Windows
# Java 11 설치 후 JAVA_HOME 환경변수 설정
```

### 2. Kafka 연결 실패

```
NoBrokersAvailable: NoBrokersAvailable
```

**해결**: Kafka 서버 확인
```bash
# Kafka 컨테이너 상태 확인
docker-compose ps kafka

# Kafka 로그 확인
docker-compose logs kafka

# Kafka가 준비될 때까지 대기 (30초)
```

### 3. MySQL 연결 오류

```
OperationalError: (2003, "Can't connect to MySQL server")
```

**해결**:
```bash
# MySQL 실행 확인
docker-compose ps mysql

# .env 파일 확인
cat .env | grep DB_

# MySQL 접속 테스트
mysql -h localhost -u root -p
```

---

## 모니터링 및 로깅

### 로그 레벨 설정

```python
# .env
LOG_LEVEL=DEBUG  # DEBUG, INFO, WARNING, ERROR, CRITICAL
```

### 로그 포맷

```
2025-01-15 10:30:15 | INFO | [Kafka] 리뷰 수신: 123
2025-01-15 10:30:15 | INFO | [Analysis] 감정 분석 시작: 123
2025-01-15 10:30:16 | INFO | [DB] 저장 완료: review_id=123
```

---

## 참고 자료

- [FastAPI 공식 문서](https://fastapi.tiangolo.com/)
- [SQLAlchemy 공식 문서](https://docs.sqlalchemy.org/)
- [KoNLPy 공식 문서](https://konlpy.org/)
- [Kafka Python 문서](https://kafka-python.readthedocs.io/)
- [Pydantic 공식 문서](https://docs.pydantic.dev/)

---

## 라이선스

이 프로젝트는 학습 목적으로 작성되었습니다.
