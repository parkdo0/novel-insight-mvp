"""
MySQL 데이터베이스 연동 모듈

SQLAlchemy ORM을 사용하여 MySQL 데이터베이스와 연동합니다.
감성 분석 결과를 데이터베이스에 저장하고 조회하는 기능을 제공합니다.

주요 기능:
1. ReviewAnalysis 테이블 관리 (ORM 모델)
2. 분석 결과 저장
3. 소설별 감성 분석 통계 조회
"""
from datetime import datetime
from typing import Dict, Optional
from sqlalchemy import create_engine, Column, BigInteger, String, Float, DateTime, Integer, text
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, Session
from config import settings
import logging

# 로거 생성
# 이 모듈에서 발생하는 모든 로그는 이 로거를 통해 출력됨
logger = logging.getLogger(__name__)

# SQLAlchemy의 Base 클래스 생성
# 모든 ORM 모델은 이 Base를 상속받아 정의함
Base = declarative_base()


class ReviewAnalysis(Base):
    """
    리뷰 감성 분석 결과 테이블 (ORM 모델)

    Python에서 감성 분석한 결과를 MySQL에 저장하기 위한 모델입니다.
    SQLAlchemy ORM을 사용하여 테이블을 정의합니다.

    테이블 구조:
    - id: 기본키 (자동 증가)
    - novel_id: 소설 ID (인덱스)
    - author_id: 작가 ID (인덱스)
    - content: 리뷰 내용 (최대 1000자)
    - sentiment: 감성 (POSITIVE/NEGATIVE/NEUTRAL)
    - score: 감성 점수 (0-100)
    - positive_count: 긍정 단어 개수
    - negative_count: 부정 단어 개수
    - analyzed_at: 분석 일시
    """

    # 실제 MySQL 테이블 이름
    __tablename__ = 'review_analysis'

    # 기본키 (Primary Key)
    # BigInteger: 큰 정수형 (Java의 Long과 동일)
    # primary_key=True: 기본키로 지정
    # autoincrement=True: 자동 증가 (MySQL의 AUTO_INCREMENT)
    id = Column(BigInteger, primary_key=True, autoincrement=True)

    # 소설 ID
    # nullable=False: NOT NULL 제약조건
    # index=True: 인덱스 생성 (조회 성능 향상)
    novel_id = Column(BigInteger, nullable=False, index=True)

    # 작가 ID
    # 작가별 통계를 빠르게 조회하기 위해 인덱스 생성
    author_id = Column(BigInteger, nullable=False, index=True)

    # 리뷰 내용
    # String(1000): VARCHAR(1000)
    # 리뷰 전문을 저장 (최대 1000자로 제한)
    content = Column(String(1000), nullable=False)

    # 감성 분류 결과
    # POSITIVE (긍정), NEGATIVE (부정), NEUTRAL (중립) 중 하나
    sentiment = Column(String(20), nullable=False)

    # 감성 점수
    # Float: 실수형 (Java의 Double과 유사)
    # 0-100 사이의 값을 가짐
    score = Column(Float, nullable=False)

    # 발견된 긍정 단어 개수
    # default=0: 기본값 0
    positive_count = Column(Integer, default=0)

    # 발견된 부정 단어 개수
    negative_count = Column(Integer, default=0)

    # 분석 일시
    # DateTime: 날짜/시간 타입
    # default=datetime.now: 레코드 생성 시 현재 시간 자동 입력
    analyzed_at = Column(DateTime, default=datetime.now)


class DatabaseManager:
    """
    데이터베이스 연결 및 관리 클래스

    MySQL 데이터베이스 연결을 관리하고,
    CRUD 작업을 수행하는 메서드를 제공합니다.

    주요 기능:
    1. 데이터베이스 연결 관리
    2. 세션(Session) 생성
    3. 분석 결과 저장
    4. 통계 조회
    """

    def __init__(self):
        """
        데이터베이스 매니저 초기화

        엔진과 세션 팩토리를 초기화하고
        데이터베이스 연결을 수립합니다.
        """
        # SQLAlchemy 엔진 (데이터베이스 커넥션 풀 관리)
        self.engine = None

        # 세션 팩토리 (세션 생성용 클래스)
        self.SessionLocal = None

        # 데이터베이스 연결 시도
        self._connect()

    def _connect(self):
        """
        데이터베이스 연결 수립 (내부 메서드)

        처리 단계:
        1. 데이터베이스 URL 생성
        2. SQLAlchemy 엔진 생성 (커넥션 풀 설정 포함)
        3. 세션 팩토리 생성
        4. 테이블 자동 생성 (없을 경우)
        """
        try:
            # 데이터베이스 연결 URL 생성
            # 형식: mysql+pymysql://사용자:비밀번호@호스트:포트/데이터베이스명?옵션
            # pymysql: MySQL 접속을 위한 Python 드라이버
            # charset=utf8mb4: 한글 및 이모지를 포함한 모든 유니코드 문자 저장 가능
            db_url = (
                f"mysql+pymysql://{settings.db_user}:{settings.db_password}"
                f"@{settings.db_host}:{settings.db_port}/{settings.db_name}"
                f"?charset=utf8mb4"
            )

            # SQLAlchemy 엔진 생성
            # 엔진은 데이터베이스 커넥션 풀을 관리함
            self.engine = create_engine(
                db_url,
                # pool_pre_ping=True: 커넥션 사용 전에 유효성 체크
                # MySQL 서버가 연결을 끊은 경우 자동으로 재연결
                pool_pre_ping=True,

                # pool_recycle=3600: 3600초(1시간)마다 커넥션 재생성
                # MySQL의 wait_timeout 설정보다 작은 값으로 설정하여
                # 서버가 연결을 끊기 전에 미리 재연결
                pool_recycle=3600,

                # echo=False: SQL 쿼리 로그 출력 안 함
                # True로 설정하면 실행되는 모든 SQL이 로그에 출력됨 (디버깅용)
                echo=False
            )

            # 세션 팩토리 생성
            # 실제 데이터베이스 작업은 Session을 통해 수행됨
            self.SessionLocal = sessionmaker(
                # autocommit=False: 명시적으로 commit() 호출 필요
                # True로 설정하면 각 쿼리가 자동 커밋됨 (권장하지 않음)
                autocommit=False,

                # autoflush=False: 명시적으로 flush() 호출 필요
                # True로 설정하면 쿼리 실행 전 자동으로 flush됨
                autoflush=False,

                # bind=self.engine: 이 엔진을 사용하도록 바인딩
                bind=self.engine
            )

            # 테이블 자동 생성
            # Base를 상속받은 모든 모델(ReviewAnalysis)의 테이블을 생성
            # 테이블이 이미 존재하면 아무 동작도 하지 않음
            Base.metadata.create_all(bind=self.engine)

            logger.info("MySQL 연결 성공")

        except Exception as e:
            # 연결 실패 시 에러 로그 출력 후 예외 재발생
            logger.error(f"MySQL 연결 실패: {e}")
            raise

    def get_session(self) -> Session:
        """
        새로운 데이터베이스 세션 반환

        세션은 데이터베이스와의 대화(conversation)를 의미합니다.
        모든 데이터베이스 작업은 세션을 통해 수행됩니다.

        사용 후 반드시 session.close()를 호출해야 합니다.

        Returns:
            Session: SQLAlchemy 세션 객체
        """
        return self.SessionLocal()

    def save_analysis(
        self,
        novel_id: int,
        author_id: int,
        content: str,
        analysis_result: Dict
    ) -> Optional[int]:
        """
        감성 분석 결과를 데이터베이스에 저장

        처리 단계:
        1. 세션 생성
        2. ReviewAnalysis 객체 생성
        3. 세션에 추가
        4. 커밋 (실제 데이터베이스에 저장)
        5. 생성된 ID 반환

        Args:
            novel_id: 소설 ID
            author_id: 작가 ID
            content: 리뷰 내용
            analysis_result: 감성 분석 결과 딕셔너리

        Returns:
            Optional[int]: 저장된 레코드의 ID (실패 시 None)
        """
        # 새로운 세션 생성
        session = self.get_session()

        try:
            # ReviewAnalysis 객체 생성
            # ORM 객체를 생성하면 Python 객체로 다룰 수 있음
            analysis = ReviewAnalysis(
                novel_id=novel_id,
                author_id=author_id,

                # content를 최대 1000자로 제한
                # MySQL 컬럼이 VARCHAR(1000)이므로 초과 시 에러 방지
                content=content[:1000],

                # 감성 분석 결과에서 필요한 필드 추출
                sentiment=analysis_result['sentiment'],
                score=analysis_result['score'],
                positive_count=analysis_result['positive_count'],
                negative_count=analysis_result['negative_count']
                # analyzed_at은 default=datetime.now로 자동 설정됨
            )

            # 세션에 객체 추가
            # 이 시점에서는 아직 데이터베이스에 저장되지 않음 (메모리상에만 존재)
            session.add(analysis)

            # 커밋: 실제 데이터베이스에 INSERT 쿼리 실행
            # 트랜잭션을 커밋하여 변경사항을 영구 저장
            session.commit()

            # refresh: 데이터베이스에서 최신 데이터 다시 로드
            # 자동 생성된 id 값을 가져오기 위해 필요
            session.refresh(analysis)

            logger.info(f"분석 결과 저장 완료: ID={analysis.id}")

            # 생성된 레코드의 ID 반환
            return analysis.id

        except Exception as e:
            # 에러 발생 시 롤백 (변경사항 취소)
            # 데이터 일관성 유지를 위해 필수
            session.rollback()

            logger.error(f"분석 결과 저장 실패: {e}")
            return None

        finally:
            # 성공/실패 여부와 관계없이 세션 종료
            # 커넥션 풀에 연결 반환
            session.close()

    def get_novel_statistics(self, novel_id: int) -> Dict:
        """
        소설별 감성 분석 통계 조회

        특정 소설에 대한 모든 리뷰의 감성 분석 통계를 계산합니다.

        처리 단계:
        1. SQL 쿼리로 집계 (COUNT, SUM, AVG)
        2. 결과에서 비율 계산
        3. 딕셔너리 형태로 반환

        Args:
            novel_id: 소설 ID

        Returns:
            Dict: 통계 정보
            {
                'total_reviews': 전체 리뷰 수,
                'positive_rate': 긍정 비율 (%),
                'negative_rate': 부정 비율 (%),
                'neutral_rate': 중립 비율 (%),
                'avg_score': 평균 감성 점수
            }
        """
        session = self.get_session()

        try:
            # Raw SQL 쿼리 작성
            # SQLAlchemy의 text() 함수로 SQL 문자열을 쿼리 객체로 변환
            query = text("""
                SELECT
                    COUNT(*) as total,
                    SUM(CASE WHEN sentiment = 'POSITIVE' THEN 1 ELSE 0 END) as positive,
                    SUM(CASE WHEN sentiment = 'NEGATIVE' THEN 1 ELSE 0 END) as negative,
                    SUM(CASE WHEN sentiment = 'NEUTRAL' THEN 1 ELSE 0 END) as neutral,
                    AVG(score) as avg_score
                FROM review_analysis
                WHERE novel_id = :novel_id
            """)

            # 쿼리 실행
            # :novel_id는 플레이스홀더 (SQL Injection 방지)
            # {"novel_id": novel_id}로 파라미터 바인딩
            result = session.execute(query, {"novel_id": novel_id}).fetchone()

            # 결과가 있고 total > 0인 경우
            if result and result[0] > 0:
                total = result[0]  # 전체 리뷰 수

                return {
                    'total_reviews': total,

                    # 긍정 비율 = (긍정 수 / 전체 수) * 100
                    # round(..., 2): 소수점 2자리까지 반올림
                    'positive_rate': round((result[1] / total) * 100, 2),

                    # 부정 비율
                    'negative_rate': round((result[2] / total) * 100, 2),

                    # 중립 비율
                    'neutral_rate': round((result[3] / total) * 100, 2),

                    # 평균 점수
                    # result[4]가 None일 수 있으므로 삼항 연산자로 처리
                    'avg_score': round(result[4], 2) if result[4] else 0
                }

            # 결과가 없는 경우 (해당 소설의 리뷰가 없음)
            return {
                'total_reviews': 0,
                'positive_rate': 0,
                'negative_rate': 0,
                'neutral_rate': 0,
                'avg_score': 0
            }

        except Exception as e:
            logger.error(f"통계 조회 실패: {e}")
            # 에러 발생 시 빈 딕셔너리 반환
            return {}

        finally:
            session.close()


# ===== 싱글톤 패턴 구현 =====
# DatabaseManager 인스턴스를 하나만 생성하여 재사용
_db_manager = None


def get_db_manager() -> DatabaseManager:
    """
    데이터베이스 매니저 싱글톤 인스턴스 반환

    첫 호출 시에만 DatabaseManager를 생성하고,
    이후 호출에서는 생성된 인스턴스를 재사용합니다.

    싱글톤을 사용하는 이유:
    1. 데이터베이스 커넥션 풀 공유
    2. 리소스 절약 (엔진을 한 번만 생성)
    3. 전역적으로 일관된 데이터베이스 접근

    Returns:
        DatabaseManager: 데이터베이스 매니저 인스턴스
    """
    global _db_manager

    if _db_manager is None:
        _db_manager = DatabaseManager()

    return _db_manager
