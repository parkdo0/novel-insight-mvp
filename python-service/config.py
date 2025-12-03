"""
환경 변수 설정 관리
"""
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """애플리케이션 설정"""

    # Kafka 설정
    kafka_bootstrap_servers: str = "novel-kafka:29092"
    kafka_topic: str = "novel-reviews"
    kafka_group_id: str = "python-consumer-group"

    # MySQL 설정
    db_host: str = "novel-mysql"
    db_port: int = 3306
    db_user: str = "novel_user"
    db_password: str = "novel1234!"
    db_name: str = "novel_insight"

    # 애플리케이션 설정
    log_level: str = "INFO"

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


settings = Settings()
