# CouponService

STOCKDROP 쿠폰 원본 서비스입니다. Java 21 / Spring Boot 4.1.1 / Spring Cloud 2025.1.4 / Gradle 9.8.2를 사용하며 PaymentService 구조를 따릅니다.

1. 기존 일반 PostgreSQL `stockdrop-account-postgres`(5433)를 실행합니다.
2. PowerShell에서 `scripts/Initialize-LocalCouponDatabase.ps1`과 `scripts/Test-LocalCouponDatabase.ps1`을 실행합니다. 전용 `coupon` DB, 계정, 스키마를 준비하며 비밀번호는 Git 제외 `.env`에 생성합니다. 업무 테이블은 Flyway가 만듭니다.
3. Java 21로 `./gradlew.bat bootRun`을 실행합니다. 기본 포트 8088, 서비스 이름은 `coupon-service`입니다.
4. 상태 확인은 `http://localhost:8088/actuator/health`, 로컬 Swagger는 `/swagger-ui.html`입니다(내부 API 제외).

쿠폰 정의, 캠페인(CLAIM, TARGETED, MEMBER), 선착순과 예산 발급, 쿠폰함과 적용 가능 조회, 주문 잠금과 사용 및 해제, 결과 확인 뒤 잠금 복구, 회원 쿠폰, 대상 발급과 예산 소진 알림, 만료와 만료 임박 알림을 제공합니다. 사용자 API는 BUYER, 운영자 API는 ADMIN, 내부 잠금은 order-command 서비스 주체와 `coupon.lock` scope를 검증합니다. 캠페인 조회 GET만 비로그인으로 열립니다.

모든 업무 플래그는 기본 false입니다.

```dotenv
COUPON_API_ENABLED=true
COUPON_CONSUMER_ENABLED=true
COUPON_SCHEDULER_ENABLED=true
COUPON_OUTBOX_PUBLISHER_ENABLED=true
KAFKA_BOOTSTRAP_SERVERS=localhost:19092
```

- [연동 계약](contracts/coupon-flow.md): 주문 잠금, 주문 결과 조회, Kafka 입력과 알림 출력, Account 의존.
- [공개 HTTP API 스냅샷](openapi/coupon.openapi.json): 실제 컨트롤러에서 생성하며 API를 바꾸면 같은 PR에서 갱신합니다.
- OpenAPI 기본 서버는 일반 Gateway인 http://localhost:8000이며 운영 주소는 `coupon.openapi.gateway-base-url`로 주입합니다.
- 새 토픽과 `.DLT`는 Infra topics.sh로 미리 만듭니다. 자동 생성은 쓰지 않습니다.
- 시연 허용 목록은 `CAMPAIGN_DEMO_ACCOUNT_IDS`(UUID 최대 10개)이고 비어 있으면 시연 발급을 막습니다.
- Outbox와 쿠폰 경보 지표는 `MONITORING_OUTBOX_ENABLED`, `MONITORING_COUPON_ENABLED`로 켭니다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot'
./gradlew.bat test bootJar
```

테스트는 격리 PostgreSQL과 Kafka 컨테이너, 테스트 메모리 RSA 키, JDK HttpServer 대역을 씁니다. 대역 테스트 통과가 실제 order-command, Account, Notification 연동 완료를 뜻하지는 않습니다. 로컬 `.env`, 개인정보, 실제 토큰은 커밋하지 않습니다.
