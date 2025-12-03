"""
MySQL 데이터베이스 연동
"""
from datetime import datetime
from typing import Dict, Optional
from sqlalchemy import create_engine, Column, BigInteger, String, Float, DateTime, Integer, text
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, Session
from config import settings
import logging

logger = logging.getLogger(__name__)

Base = declarative_base()


class ReviewAnalysis(Base):
    """리뷰 감성 분석 결과 테이블"""
    __tablename__ = 'review_analysis'

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    novel_id = Column(BigInteger, nullable=False, index=True)
    author_id = Column(BigInteger, nullable=False, index=True)
    content = Column(String(1000), nullable=False)
    sentiment = Column(String(20), nullable=False)  # POSITIVE, NEGATIVE, NEUTRAL
    score = Column(Float, nullable=False)
    positive_count = Column(Integer, default=0)
    negative_count = Column(Integer, default=0)
    analyzed_at = Column(DateTime, default=datetime.now)


class DatabaseManager:
    """데이터베이스 매니저"""

    def __init__(self):
        self.engine = None
        self.SessionLocal = None
        self._connect()

    def _connect(self):
        """데이터베이스 연결"""
        try:
            db_url = (
                f"mysql+pymysql://{settings.db_user}:{settings.db_password}"
                f"@{settings.db_host}:{settings.db_port}/{settings.db_name}"
                f"?charset=utf8mb4"
            )
            self.engine = create_engine(
                db_url,
                pool_pre_ping=True,  # 연결 유효성 체크
                pool_recycle=3600,   # 1시간마다 재연결
                echo=False
            )
            self.SessionLocal = sessionmaker(
                autocommit=False,
                autoflush=False,
                bind=self.engine
            )
            # 테이블 생성
            Base.metadata.create_all(bind=self.engine)
            logger.info("✅ MySQL 연결 성공")
        except Exception as e:
            logger.error(f"❌ MySQL 연결 실패: {e}")
            raise

    def get_session(self) -> Session:
        """세션 반환"""
        return self.SessionLocal()

    def save_analysis(
        self,
        novel_id: int,
        author_id: int,
        content: str,
        analysis_result: Dict
    ) -> Optional[int]:
        """
        분석 결과 저장

        Returns:
            저장된 레코드 ID (실패 시 None)
        """
        session = self.get_session()
        try:
            analysis = ReviewAnalysis(
                novel_id=novel_id,
                author_id=author_id,
                content=content[:1000],  # 최대 1000자
                sentiment=analysis_result['sentiment'],
                score=analysis_result['score'],
                positive_count=analysis_result['positive_count'],
                negative_count=analysis_result['negative_count']
            )
            session.add(analysis)
            session.commit()
            session.refresh(analysis)
            logger.info(f"💾 분석 결과 저장 완료: ID={analysis.id}")
            return analysis.id
        except Exception as e:
            session.rollback()
            logger.error(f"❌ 분석 결과 저장 실패: {e}")
            return None
        finally:
            session.close()

    def get_novel_statistics(self, novel_id: int) -> Dict:
        """
        소설별 통계 조회

        Returns:
            {
                'total_reviews': int,
                'positive_rate': float,
                'negative_rate': float,
                'neutral_rate': float,
                'avg_score': float
            }
        """
        session = self.get_session()
        try:
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
            result = session.execute(query, {"novel_id": novel_id}).fetchone()

            if result and result[0] > 0:
                total = result[0]
                return {
                    'total_reviews': total,
                    'positive_rate': round((result[1] / total) * 100, 2),
                    'negative_rate': round((result[2] / total) * 100, 2),
                    'neutral_rate': round((result[3] / total) * 100, 2),
                    'avg_score': round(result[4], 2) if result[4] else 0
                }
            return {
                'total_reviews': 0,
                'positive_rate': 0,
                'negative_rate': 0,
                'neutral_rate': 0,
                'avg_score': 0
            }
        except Exception as e:
            logger.error(f"❌ 통계 조회 실패: {e}")
            return {}
        finally:
            session.close()


# 싱글톤 인스턴스
_db_manager = None


def get_db_manager() -> DatabaseManager:
    """데이터베이스 매니저 싱글톤 반환"""
    global _db_manager
    if _db_manager is None:
        _db_manager = DatabaseManager()
    return _db_manager
