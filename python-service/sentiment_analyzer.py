"""
한국어 감성 분석 엔진 모듈

KoNLPy의 Okt(Open Korean Text) 형태소 분석기를 사용하여
한국어 텍스트의 감성(긍정/부정/중립)을 분석합니다.

처리 흐름:
1. 텍스트 전처리 (특수문자 제거, 공백 정리)
2. 형태소 분석 (단어를 기본형으로 변환)
3. 감성 사전과 매칭 (긍정/부정 단어 추출)
4. 점수 계산 및 감성 판별
"""
import re
from typing import Dict
from konlpy.tag import Okt


class SentimentAnalyzer:
    """
    한국어 감성 분석기 클래스

    KoNLPy의 Okt를 사용하여 형태소 분석 후 감성 사전과 매칭하여
    텍스트의 감성을 판별합니다.
    """

    def __init__(self):
        """
        감성 분석기 초기화

        Okt 형태소 분석기 인스턴스를 생성하고
        감성 사전을 메모리에 로드합니다.
        """
        # Okt 형태소 분석기 인스턴스 생성
        # Okt는 KoNLPy에서 제공하는 한국어 처리 도구 중 하나
        # 비교적 빠르고 정확도가 높아 많이 사용됨
        self.okt = Okt()

        # 감성 사전 로드
        self._load_sentiment_dict()

    def _load_sentiment_dict(self):
        """
        감성 사전 로드 (내부 메서드)

        긍정 단어와 부정 단어를 Set 자료구조에 저장합니다.
        Set을 사용하는 이유: O(1) 시간복잡도로 빠른 검색 가능
        """
        # 긍정 단어 사전
        # 웹소설 독자들이 자주 사용하는 긍정적 표현들을 수집
        self.positive_words = {
            '좋다', '재미있다', '최고', '멋지다', '훌륭하다', '감동', '완벽',
            '사랑', '행복', '웃기다', '즐겁다', '흥미롭다', '짱', '대박',
            '굿', '킹', '갓', '꿀잼', '인정', '레전드', '명작', '몰입',
            '박진감', '스릴', '반전', '매력', '추천', '소름', '환상',
            '천재', '몰입도', '흥미진진', '재밌다'
        }

        # 부정 단어 사전
        # 웹소설 독자들이 자주 사용하는 부정적 표현들을 수집
        self.negative_words = {
            '싫다', '별로', '실망', '지루하다', '짜증', '최악', '후회',
            '답답하다', '억지', '노잼', '쓰레기', '개연성', '불편', '빡치다',
            '화나다', '허술', '졸리다', '뻔하다', '유치', '이해불가',
            '망작', '루즈', '늘어지다', '지루', '따분'
        }

    def analyze(self, text: str) -> Dict[str, any]:
        """
        텍스트 감성 분석 메인 메서드

        주어진 텍스트를 분석하여 긍정/부정/중립을 판별하고
        감성 점수와 발견된 단어 목록을 반환합니다.

        처리 단계:
        1. 텍스트 전처리 (특수문자 제거 등)
        2. 형태소 분석 (단어를 기본형으로 변환)
        3. 감성 단어 추출 (감성 사전과 매칭)
        4. 점수 계산 (긍정/부정 비율)
        5. 감성 판별 (POSITIVE/NEGATIVE/NEUTRAL)

        Args:
            text: 분석할 한국어 텍스트

        Returns:
            Dict: 분석 결과를 담은 딕셔너리
            {
                'sentiment': str - 감성 (POSITIVE/NEGATIVE/NEUTRAL)
                'score': float - 감성 점수 (0-100)
                'positive_words': List[str] - 발견된 긍정 단어 목록
                'negative_words': List[str] - 발견된 부정 단어 목록
                'positive_count': int - 긍정 단어 개수
                'negative_count': int - 부정 단어 개수
            }
        """
        # 1단계: 텍스트 전처리
        # 특수문자, 이모티콘 등을 제거하고 한글/영문/공백만 남김
        cleaned_text = self._preprocess(text)

        # 2단계: 형태소 분석
        # stem=True 옵션: 단어를 기본형으로 변환
        # 예: "좋았어" -> "좋다", "재미있네" -> "재미있다"
        # 이렇게 하면 다양한 활용형을 하나의 기본형으로 통일할 수 있음
        morphs = self.okt.morphs(cleaned_text, stem=True)

        # 3단계: 감성 단어 추출
        # List Comprehension을 사용하여 형태소 중 감성 사전에 있는 단어만 추출
        # found_positive: 발견된 긍정 단어 목록
        # 예: ["좋다", "재미있다", "최고"]
        found_positive = [word for word in morphs if word in self.positive_words]

        # found_negative: 발견된 부정 단어 목록
        # 예: ["지루하다", "별로"]
        found_negative = [word for word in morphs if word in self.negative_words]

        # 4단계: 점수 계산
        # 긍정 단어 개수
        positive_score = len(found_positive)

        # 부정 단어 개수
        negative_score = len(found_negative)

        # 전체 감성 단어 개수 (긍정 + 부정)
        total_score = positive_score + negative_score

        # 5단계: 감성 판별 및 점수 산출
        # Case 1: 감성 단어가 하나도 없는 경우
        if total_score == 0:
            sentiment = "NEUTRAL"
            score = 50.0  # 중립 점수

        # Case 2: 긍정 단어가 부정 단어보다 많은 경우
        elif positive_score > negative_score:
            sentiment = "POSITIVE"
            # 점수 = (긍정 단어 비율) * 100
            # 예: 긍정 7개, 부정 3개 -> (7/10) * 100 = 70점
            # min() 함수로 100을 넘지 않도록 제한
            score = min((positive_score / total_score) * 100, 100)

        # Case 3: 부정 단어가 긍정 단어보다 많은 경우
        elif negative_score > positive_score:
            sentiment = "NEGATIVE"
            # 점수 = (부정 단어 비율) * 100
            score = min((negative_score / total_score) * 100, 100)

        # Case 4: 긍정과 부정 단어 개수가 같은 경우
        else:
            sentiment = "NEUTRAL"
            score = 50.0  # 중립 점수

        # 분석 결과 반환
        return {
            'sentiment': sentiment,
            'score': round(score, 2),  # 소수점 2자리까지 반올림
            'positive_words': found_positive,
            'negative_words': found_negative,
            'positive_count': len(found_positive),
            'negative_count': len(found_negative)
        }

    def _preprocess(self, text: str) -> str:
        """
        텍스트 전처리 메서드 (내부 메서드)

        분석 전에 텍스트를 정제합니다:
        1. 특수문자, 숫자, 이모티콘 제거
        2. 연속된 공백을 하나로 통일
        3. 앞뒤 공백 제거

        Args:
            text: 원본 텍스트

        Returns:
            str: 전처리된 텍스트
        """
        # 정규표현식으로 한글(가-힣), 영문(a-zA-Z), 공백(\s)만 남기고 모두 제거
        # [^...]: not을 의미, 즉 대괄호 안의 문자가 아닌 것을 의미
        # 예: "좋아요!!! 123" -> "좋아요 "
        text = re.sub(r'[^가-힣a-zA-Z\s]', '', text)

        # 연속된 공백(\s+)을 하나의 공백으로 변환
        # 예: "정말    좋아요" -> "정말 좋아요"
        text = re.sub(r'\s+', ' ', text)

        # 문자열 앞뒤의 공백 제거
        return text.strip()


# ===== 싱글톤 패턴 구현 =====
# 전역 변수로 분석기 인스턴스를 하나만 유지
# 이유: Okt 초기화에 시간이 걸리므로 재사용
_analyzer = None


def get_analyzer() -> SentimentAnalyzer:
    """
    감성 분석기 싱글톤 인스턴스 반환 함수

    첫 호출 시에만 SentimentAnalyzer 인스턴스를 생성하고,
    이후 호출에서는 생성된 인스턴스를 재사용합니다.

    싱글톤 패턴을 사용하는 이유:
    1. Okt 초기화에 시간이 걸림 (약 1-2초)
    2. 메모리 절약 (감성 사전을 한 번만 로드)
    3. 성능 향상 (매번 새로 생성하지 않음)

    Returns:
        SentimentAnalyzer: 감성 분석기 인스턴스
    """
    global _analyzer

    # 인스턴스가 없으면 생성
    if _analyzer is None:
        _analyzer = SentimentAnalyzer()

    # 기존 인스턴스 반환
    return _analyzer
