"""
환경 변수 및 애플리케이션 설정 관리 모듈

이 모듈은 Pydantic Settings를 사용하여 환경 변수를 타입 안전하게 관리합니다.
.env 파일이나 시스템 환경 변수에서 설정을 자동으로 로드합니다.

사용 예시:
    from config import settings
    
    # 환경 변수 접근
    print(settings.db_password)  # 타입 안전
    print(settings.database_url)  # @property로 동적 생성
"""
from pydantic_settings import BaseSettings
from typing import Optional


class Settings(BaseSettings):
    """
    애플리케이션 전역 설정 클래스
    
    BaseSettings를 상속받아 환경 변수를 자동으로 바인딩합니다.
    환경 변수 이름은 대소문자 구분 없이 매핑됩니다.
    
    예: 
        KAFKA_BOOTSTRAP_SERVERS 환경 변수 → kafka_bootstrap_servers 필드
        DB_PASSWORD 환경 변수 → db_password 필드
    
    우선순위:
        1. 환경 변수 (최우선)
        2. .env 파일
        3. 기본값
    """

    # ===== Kafka 관련 설정 =====
    
    # Kafka 브로커 서버 주소
    # - 로컬 개발: localhost:9092
    # - Docker 네트워크: novel-kafka:29092
    # 29092 포트는 Docker 내부 통신용 (PLAINTEXT 리스너)
    kafka_bootstrap_servers: str = "novel-kafka:29092"

    # Kafka 토픽 이름
    # Spring Boot에서 전송하는 리뷰 로그를 받기 위한 토픽
    # 백엔드와 동일한 토픽 이름을 사용해야 함
    kafka_topic: str = "novel-reviews"

    # Kafka Consumer Group ID
    # - 같은 그룹의 Consumer들은 메시지를 분산 처리 (Load Balancing)
    # - 다른 그룹의 Consumer는 같은 메시지를 중복으로 받을 수 있음
    # - 재시작 시 마지막 Offset부터 이어서 소비
    kafka_group_id: str = "python-consumer-group"

    # ===== MySQL 데이터베이스 설정 =====
    
    # 데이터베이스 호스트 주소
    # - 로컬 개발: localhost
    # - Docker 네트워크: novel-mysql (서비스 이름)
    db_host: str = "novel-mysql"

    # MySQL 기본 포트 (기본값: 3306)
    db_port: int = 3306

    # 데이터베이스 사용자 이름
    # docker-compose.yml의 MYSQL_USER와 일치해야 함
    db_user: str = "novel_user"

    # [보안] 데이터베이스 비밀번호
    # 실제 운영 환경에서는 반드시 환경 변수로 주입해야 함
    # .env 파일에 작성하거나 시스템 환경 변수로 설정
    # 주의: 이 파일에 직접 작성하지 마세요!
    db_password: str

    # 사용할 데이터베이스 이름
    db_name: str = "novel_insight"

    # ===== 애플리케이션 설정 =====
    
    # 로그 레벨 설정
    # DEBUG: 상세한 디버깅 정보
    # INFO: 일반적인 정보성 메시지
    # WARNING: 경고 메시지
    # ERROR: 에러 메시지
    # CRITICAL: 치명적인 에러
    log_level: str = "INFO"

    # FastAPI 서버 포트
    port: int = 8000

    # FastAPI 서버 바인딩 호스트
    # 0.0.0.0: 모든 네트워크 인터페이스에서 접속 허용
    # 127.0.0.1: localhost만 허용
    host: str = "0.0.0.0"

    # ===== KoNLPy 설정 (선택사항) =====
    
    # Java Home 경로
    # KoNLPy는 내부적으로 JVM을 사용하므로 JAVA_HOME이 필요할 수 있음
    # 예: /usr/lib/jvm/java-11-openjdk-amd64
    # 보통 시스템 환경 변수로 설정되어 있으면 생략 가능
    java_home: Optional[str] = None

    # ===== Property (계산된 속성) =====
    
    @property
    def database_url(self) -> str:
        """
        SQLAlchemy용 데이터베이스 연결 URL 생성
        
        형식: mysql+pymysql://user:password@host:port/database
        
        Returns:
            str: SQLAlchemy 데이터베이스 URL
        
        예시:
            mysql+pymysql://novel_user:novel1234!@novel-mysql:3306/novel_insight
        """
        return f"mysql+pymysql://{self.db_user}:{self.db_password}@{self.db_host}:{self.db_port}/{self.db_name}"

    # ===== Pydantic Settings 설정 =====
    
    class Config:
        """
        Pydantic Settings 메타 설정
        
        - env_file: .env 파일 경로 (자동으로 로드)
        - env_file_encoding: .env 파일 인코딩
        - case_sensitive: 환경 변수 대소문자 구분 (False: 구분 안함)
        """
        # .env 파일을 자동으로 로드
        # 파일이 없어도 에러를 발생시키지 않음 (환경 변수 우선)
        env_file = ".env"
        
        # .env 파일의 인코딩 설정 (한글 지원)
        env_file_encoding = "utf-8"
        
        # 환경 변수 대소문자 구분 안함
        # DB_PASSWORD와 db_password 모두 매핑됨
        case_sensitive = False


# ===== 싱글톤 인스턴스 =====

# 애플리케이션 전역에서 사용할 설정 인스턴스
# 한 번만 생성되고 모든 모듈에서 공유됨
# 
# 사용 예시:
#     from config import settings
#     
#     print(settings.kafka_bootstrap_servers)
#     print(settings.database_url)
#
# 장점:
#     1. 타입 안전성: IDE 자동완성 지원
#     2. 유효성 검증: Pydantic이 자동으로 타입 체크
#     3. 환경 분리: .env 파일로 개발/스테이징/프로덕션 분리
settings = Settings()
