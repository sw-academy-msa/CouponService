# STOCKDROP CouponService

Java 21 / Spring Boot 4.1.1 쿠폰 원본 서비스입니다. 포트 8088, DB와 스키마 coupon을 사용합니다.

모든 업무 플래그는 기본 false입니다. DB_PASSWORD와 JWT 설정은 환경변수로 주입합니다. 로컬에서도 Flyway 실행 후 Hibernate validate만 수행합니다. 기존 PaymentService와 동일한 의존성만 사용하며 포매터 플러그인을 추가하지 않았습니다.

쿠폰 정의, 캠페인, 선착순 발급, 쿠폰함과 적용 가능 조회, 주문 잠금과 사용 및 해제, 임대 기반 복구, 회원 쿠폰, 대상 발급과 예산 소진 알림, 만료와 만료 임박 알림을 제공합니다. 운영자 API는 ADMIN, 사용자 API는 BUYER, 내부 잠금은 order-command 서비스 주체와 coupon.lock scope를 검증합니다. 캠페인 조회 GET은 비로그인도 가능합니다.

활성화 환경변수는 COUPON_API_ENABLED, COUPON_CONSUMER_ENABLED, COUPON_SCHEDULER_ENABLED, COUPON_OUTBOX_PUBLISHER_ENABLED입니다. 잠금 복구는 holdUntil 뒤 기본 10분부터 주문 원본을 확인하며 coupon.lock-recovery.grace-minutes로 조정합니다. 대상 이벤트는 receipt와 내구성 큐에 저장하고 30초 주기로 동의를 확인합니다. 일시 실패는 최대 30분 동안 재시도하며 HTTP 동안 DB 연결을 점유하지 않습니다.

Kafka 입력은 stockdrop.orders.lifecycle.v1, stockdrop.ai.campaign-targets.v1, stockdrop.membership.events.private.v1이며, 각 원본의 .DLT를 미리 만들어야 합니다. 신규 계약 오류는 즉시 DLT, 일시 오류는 1초 간격 3회 후 DLT입니다. 격리 발행이 실패하면 소비를 pause하고 offset을 유지합니다. Outbox는 최대 20회 뒤 failed_at으로 격리합니다. Outbox와 쿠폰 경보 수집은 MONITORING_OUTBOX_ENABLED와 MONITORING_COUPON_ENABLED로 켭니다.

검증: JAVA_HOME을 JDK 21로 지정하고 Docker 실행 후 ./gradlew test bootJar를 실행합니다. Swagger는 local 프로필에서 /swagger-ui.html이며 내부 API는 제외합니다.

로컬 저장소만 생성했습니다. 원격 push와 운영 배포는 수행하지 않습니다. 상세 상태는 임시 coupon/impl1-report.md를 참고합니다.
