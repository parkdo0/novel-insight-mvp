"""
한국어 감성 분석 엔진 (KoNLPy Okt 사용)
"""
import re
from typing import Dict
from konlpy.tag import Okt


class SentimentAnalyzer:
    """한국어 감성 분석기"""

    def __init__(self):
        self.okt = Okt()
        self._load_sentiment_dict()

    def _load_sentiment_dict(self):
        """감성 사전 로드"""
        self.positive_words = {
            '좋다', '재미있다', '최고', '멋지다', '훌륭하다', '감동', '완벽',
            '사랑', '행복', '웃기다', '즐겁다', '흥미롭다', '짱', '대박',
            '굿', '킹', '갓', '꿀잼', '인정', '레전드', '명작', '몰입',
            '박진감', '스릴', '반전', '매력', '추천', '소름', '환상',
            '천재', '몰입도', '흥미진진', '재밌다'
        }

        self.negative_words = {
            '싫다', '별로', '실망', '지루하다', '짜증', '최악', '후회',
            '답답하다', '억지', '노잼', '쓰레기', '개연성', '불편', '빡치다',
            '화나다', '허술', '졸리다', '뻔하다', '유치', '이해불가',
            '망작', '루즈', '늘어지다', '지루', '따분'
        }

    def analyze(self, text: str) -> Dict[str, any]:
        """
        텍스트 감성 분석

        Args:
            text: 분석할 텍스트

        Returns:
            {
                'sentiment': 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL',
                'score': float (0-100),
                'positive_words': List[str],
                'negative_words': List[str]
            }
        """
        # 전처리
        cleaned_text = self._preprocess(text)

        # 형태소 분석
        morphs = self.okt.morphs(cleaned_text, stem=True)

        # 감성 단어 추출
        found_positive = [word for word in morphs if word in self.positive_words]
        found_negative = [word for word in morphs if word in self.negative_words]

        # 점수 계산
        positive_score = len(found_positive)
        negative_score = len(found_negative)
        total_score = positive_score + negative_score

        # 감성 판별
        if total_score == 0:
            sentiment = "NEUTRAL"
            score = 50.0
        elif positive_score > negative_score:
            sentiment = "POSITIVE"
            score = min((positive_score / total_score) * 100, 100)
        elif negative_score > positive_score:
            sentiment = "NEGATIVE"
            score = min((negative_score / total_score) * 100, 100)
        else:
            sentiment = "NEUTRAL"
            score = 50.0

        return {
            'sentiment': sentiment,
            'score': round(score, 2),
            'positive_words': found_positive,
            'negative_words': found_negative,
            'positive_count': len(found_positive),
            'negative_count': len(found_negative)
        }

    def _preprocess(self, text: str) -> str:
        """텍스트 전처리"""
        # 특수문자 제거 (한글, 영문, 공백만 남김)
        text = re.sub(r'[^가-힣a-zA-Z\s]', '', text)
        # 연속 공백 제거
        text = re.sub(r'\s+', ' ', text)
        return text.strip()


# 싱글톤 인스턴스
_analyzer = None


def get_analyzer() -> SentimentAnalyzer:
    """감성 분석기 싱글톤 인스턴스 반환"""
    global _analyzer
    if _analyzer is None:
        _analyzer = SentimentAnalyzer()
    return _analyzer
