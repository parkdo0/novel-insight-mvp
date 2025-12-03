"""
환경 변수 및 애플리케이션 설정 관리 모듈

이 모듈은 Pydantic Settings를 사용하여 환경 변수를 타입 안전하게 관리합니다.
.env 파일이나 시스템 환경 변수에서 설정을 자동으로 로드합니다.
"""
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """
    애플리케이션 전역 설정 클래스

    BaseSettings를 상속받아 환경 변수를 자동으로 바인딩합니다.
    예: KAFKA_BOOTSTRAP_SERVERS 환경 변수 -> kafka_bootstrap_servers 필드
    """

    # ===== Kafka 관련 설정 =====
    # Kafka 브로커 서버 주소
    # Docker 네트워크 내부에서는 서비스 이름(novel-kafka)으로 접근
    # 29092 포트는 Docker 내부 통신용
    kafka_bootstrap_servers: str = "novel-kafka:29092"

    # Kafka 토픽 이름
    # Spring Boot에서 전송하는 리뷰 로그를 받기 위한 토픽
    kafka_topic: str = "novel-reviews"

    # Kafka Consumer Group ID
    # 같은 그룹의 Consumer들은 메시지를 분산 처리
    # 다른 그룹의 Consumer는 같은 메시지를 중복으로 받을 수 있음
    kafka_group_id: str = "python-consumer-group"

    # ===== MySQL 데이터베이스 설정 =====
    # 데이터베이스 호스트 주소
    # Docker 네트워크 내부에서는 서비스 이름으로 접근
    db_host: str = "novel-mysql"

    # MySQL 기본 포트
    db_port: int = 3306

    # 데이터베이스 사용자 이름
    # docker-compose.yml의 MYSQL_USER와 일치해야 함
    db_user: str = "novel_user"

    # 데이터베이스 비밀번호
    # 실제 운영 환경에서는 반드시 환경 변수로 주입해야 함
    db_password: str = "novel1234!"

    # 사용할 데이터베이스 이름
    db_name: str = "novel_insight"

    # ===== 애플리케이션 설정 =====
    # 로그 레벨 설정 (DEBUG, INFO, WARNING, ERROR, CRITICAL)
    log_level: str = "INFO"

    class Config:
        """Pydantic Settings 설정"""
        # .env 파일을 자동으로 로드
        # 파일이 없어도 에러를 발생시키지 않음
        env_file = ".env"

        # .env 파일의 인코딩 설정
        env_file_encoding = "utf-8"


# 싱글톤 인스턴스 생성
# 애플리케이션 전역에서 이 인스턴스를 import하여 사용
# 예: from config import settings
settings = Settings()
