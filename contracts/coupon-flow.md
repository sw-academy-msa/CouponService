# CouponService 연동 계약 v1

CouponService는 쿠폰 정의, 캠페인, 발급, 잠금, 사용, 해제의 원본입니다. 주문 금액은 order-command가 계산하고, 쿠폰은 사용 가능 판단과 할인 조건 스냅샷만 돌려줍니다. 공개 HTTP API는 [openapi/coupon.openapi.json](../openapi/coupon.openapi.json)을 기준으로 합니다.

## 1. 주문 잠금 (order-command → coupon, 내부 HTTP)

`POST /internal/v1/coupon-locks` (Gateway 라우팅 없음)

- 인증: Account 서비스 토큰. sub = `ORDER_SERVICE_SUBJECT`, client_id = `order-command-service`, principal_type = `service`, scope에 `coupon.lock` 포함, aud와 iss는 공통 설정.
- 요청: `{"orderId","couponIssueId","accountId","productId","sellerId","itemsAmount","holdUntil"}`. itemsAmount는 1 이상, holdUntil은 현재보다 뒤여야 합니다.
- 200: `{"success":true,"code":"COMMON_SUCCESS","data":{"orderId","couponIssueId","discountType","amount","rateBps","maxDiscount","minItemsAmount","definitionVersion","state":"LOCKED"}}`. Cache-Control no-store.
- 멱등: 같은 orderId와 같은 요청 값이면 같은 응답입니다. 값이 다르면 409 `COUPON_LOCK_CONFLICT`입니다.
- 확정 거절(쿠폰 없이 주문 진행): 403 `COUPON_NOT_OWNED`, 404 `COUPON_NOT_FOUND`, 409 `COUPON_NOT_AVAILABLE`, `COUPON_EXPIRED`, `COUPON_SCOPE_MISMATCH`, `COUPON_MIN_AMOUNT`, `COUPON_SELF_PRODUCT`(sellerId = accountId).
- 그 밖의 오류와 타임아웃은 일시 실패입니다. 같은 요청으로 재시도하면 멱등 응답을 받습니다.

할인액 계산(order-command): AMOUNT는 amount, RATE는 floor(itemsAmount × rateBps / 10000)와 maxDiscount 중 작은 값입니다. 결제할 상품 금액은 최소 100원(MIN_ITEMS_CHARGE)을 남깁니다.

## 2. 주문 결과 조회 (coupon → order-command, 내부 HTTP)

`GET /internal/v1/orders/{orderId}/coupon-outcome`. order-command가 2단계에서 새로 만듭니다.

- 인증: coupon-service 서비스 토큰(scope `order.coupon.read`)을 order-command가 검증합니다.
- 200: `{"success":true,"data":{"orderId","phase","couponApplied":true|false,"couponDiscount":3000}}`. phase는 Saga 상태 문자열이고 CONFIRMED, FAILED, EXPIRED만 끝 상태로 봅니다. couponApplied=true이면 couponDiscount는 0 이상의 정수입니다.
- 404: 주문 없음. CouponService는 잠근 뒤 24시간이 지난 경우에만 해제합니다.
- 그 밖의 응답, 형식 오류, 타임아웃은 결과 모름입니다. 잠금을 풀지 않고 백오프 후 다시 묻습니다(최대 10분 간격).
- 확인 시작은 holdUntil + `coupon.lock-recovery.grace-minutes`(기본 10분)이고, 진행 중이면 5분 뒤 다시 묻습니다.

## 3. Kafka 입력

모든 입력은 16KiB 이하, schemaVersion 1입니다. 계약 오류와 같은 eventId의 다른 본문은 바로 `<topic>.DLT`(같은 partition)로 보내고, 일시 실패는 1초 간격 3회 재시도 후 DLT로 보냅니다. DLT 발행이 실패하면 offset을 커밋하지 않고 listener를 멈춥니다.

| 토픽 | group | 내용 |
|---|---|---|
| stockdrop.orders.lifecycle.v1 | coupon-order-lifecycle-v1 | type ORDER_LIFECYCLE. eventId = nameUUID("ORDER_LIFECYCLE:"+orderId+":"+phase). CONFIRMED와 couponApplied=true이면 USED, CONFIRMED와 couponApplied=false, FAILED, EXPIRED이면 해제합니다. buyerId와 couponIssueId가 잠금과 다르면 계약 오류입니다 |
| stockdrop.ai.campaign-targets.v1 | coupon-campaign-target-v1 | type CAMPAIGN_TARGET. eventId = AI decisionId, ruleCode는 SALE_OPEN, CHECKOUT_ABANDON, SOLD_OUT_SIMILAR, CATEGORY_NEW. 동의 확인 실패는 DLT가 아니라 coupon_pending_targets에서 30분 안에 재시도합니다 |
| stockdrop.membership.events.private.v1 | coupon-membership-v1 | 회원제 설계의 완전 스냅샷. MEMBERSHIP_STARTED와 RENEWED만 발급하고 멱등 키는 subscriptionId:generation:cycleNumber입니다. 활성 MEMBER 캠페인이 정확히 1개가 아니면 일시 실패로 재시도 후 DLT로 보냅니다 |

## 4. Kafka 출력 (Outbox)

`stockdrop.notifications.coupon.v1`(source COUPON), `stockdrop.notifications.campaign.v1`(source CAMPAIGN, 예산 소진 때 쿠폰 없는 알림), key = accountId. 본문은 기존 BusinessNotice와 같습니다.

```json
{"eventId":"nameUUID(source:type:aggregateId:aggregateVersion:accountId)","schemaVersion":1,"type":"BUSINESS_NOTIFICATION","source":"COUPON","accountId":"uuid","notificationType":"COUPON_SALE_OPEN","aggregateId":"issueId 또는 decisionId","aggregateVersion":1,"targetType":"PRODUCT|COUPON","targetId":"productId 또는 issueId","relatedId":"issueId|null","deadline":"...","occurredAt":"..."}
```

notificationType: COUPON_SALE_OPEN, COUPON_CHECKOUT_REMINDER, COUPON_SIMILAR_DROP, COUPON_CATEGORY_NEW, COUPON_EXPIRING, COUPON_MEMBER_ARRIVED, CAMPAIGN_SALE_OPEN, CAMPAIGN_CHECKOUT_REMINDER, CAMPAIGN_SIMILAR_DROP, CAMPAIGN_CATEGORY_NEW. 문구, 동의, 야간 금지, "(광고)" 표기는 Notification이 정합니다. Outbox는 20회 실패하면 failed_at으로 격리하고 같은 eventId로 재발행합니다.

## 5. Account 의존

- 서비스 토큰: `POST /internal/v1/auth/service-tokens`, client_id `coupon-service`, scope `account.notifications.read order.coupon.read`. 비밀값은 `COUPON_CLIENT_SECRET`, 주체는 `COUPON_SERVICE_SUBJECT` 환경변수입니다.
- 동의 확인: `GET /internal/v1/notification-eligibility?accountId=&purpose=PERSONALIZED` → `data.eligible`. 거절이면 발급하지 않고, 확인 불가이면 재시도합니다.

## 6. 회원제 결과 계약 대조

Payment의 MembershipResultOutboxServiceImpl과 Account의 MembershipProjectionServiceImpl 허용 목록을 기준으로 다음 결과 일곱 종류를 받습니다. 모두 schemaVersion=1, source=PAYMENT, generation 및 aggregateVersion은 1 이상이고 cycleNumber는 long입니다.

| type | 쿠폰 처리 | cycleNumber |
|---|---|---|
| MEMBERSHIP_STARTED | 주기별 1회 발급 및 알림 | 1 이상 |
| MEMBERSHIP_RENEWED | 주기별 1회 발급 및 알림 | 1 이상 |
| MEMBERSHIP_RENEWAL_PENDING | receipt만 저장 | 0 이상 |
| MEMBERSHIP_PAYMENT_FAILED | receipt만 저장 | 0 이상 |
| MEMBERSHIP_CANCELED | receipt만 저장, 기존 쿠폰 유지 | 0 이상, 실제 해지 결과는 0 |
| MEMBERSHIP_EXPIRED | receipt만 저장, 기존 쿠폰 유지 | 0 이상 |
| MEMBERSHIP_WITHDRAWN | receipt만 저장, 기존 쿠폰 유지 | 0 이상, 실제 탈퇴 결과는 0 |

MEMBERSHIP_CANCELLATION_SCHEDULED는 실제 Payment 결과 이름이 아니므로 허용 목록에서 제거했습니다. Account에서 Payment로 보내는 MEMBERSHIP_CANCEL_REQUESTED 명령과 Account 알림용 MEMBERSHIP_EXPIRING도 이 결과 토픽 계약에 포함하지 않습니다. 비발급 결과는 활성 MEMBER 캠페인 유무와 무관하게 receipt를 커밋하며 쿠폰, 발급 예산 및 Outbox를 바꾸지 않습니다. 같은 eventId와 같은 본문은 멱등이고 다른 본문은 기존 계약대로 DLT 대상입니다.
