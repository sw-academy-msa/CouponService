# STOCKDROP CouponService

Java 21 / Spring Boot 4.1.1 쿠폰 원본 서비스입니다. 포트 8088, DB와 스키마 coupon을 사용합니다.

모든 업무 플래그는 기본 false입니다. DB_PASSWORD와 JWT 설정은 환경변수로 주입합니다. 로컬에서도 Flyway 실행 후 Hibernate validate만 수행합니다.

검증: JAVA_HOME을 JDK 21로 지정하고 Docker 실행 후 ./gradlew test bootJar를 실행합니다. Swagger는 local 프로필에서 /swagger-ui.html이며 내부 API는 제외합니다.

로컬 저장소만 생성했습니다. 원격 push와 운영 배포는 수행하지 않습니다. 상세 상태는 임시 coupon/impl1-report.md를 참고합니다.
