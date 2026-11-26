# 받기형 캠페인 광고 알림

POST `/api/v1/admin/coupons/campaigns/{id}/notifications`는 ADMIN 전용입니다. `COUPON_API_ENABLED=true`, `COUPON_CLAIM_NOTIFICATION_ENABLED=true`일 때만 존재합니다. 후자는 기본 false이며 SSM 경로는 `/stockdrop/prod/coupon-service/COUPON_CLAIM_NOTIFICATION_ENABLED`입니다.

첫 요청은 CLAIM, ACTIVE, startsAt 이상 endsAt 미만, 남은 예산을 검사합니다. 캠페인 행을 잠그고 발송 요청 시각 notificationSentAt과 기존 Outbox 이벤트를 같은 트랜잭션으로 저장합니다. 응답은 기존 ApiResponse의 CampaignResponse(200)이며 목록과 공개 캠페인 응답에도 nullable notificationSentAt을 추가합니다. 같은 캠페인 재요청은 최초 접수 결과(200)를 반환합니다. 이 표시는 푸시 실제 전달 완료가 아닌 발송 요청 접수입니다.

기존 `stockdrop.notifications.campaign.v1` 토픽을 재사용하므로 새 토픽은 없습니다. 파티션 키는 campaignId입니다. schemaVersion=1, type=CLAIM_CAMPAIGN_NOTIFICATION, eventId, campaignId, campaignCode, couponName, deadline, occurredAt을 전달합니다. eventId는 UTF-8 `CLAIM_CAMPAIGN:{campaignId}`의 Java UUID.nameUUIDFromBytes입니다. couponName은 정의 이름이며 deadline은 캠페인 endsAt입니다. 수신자 정보는 넣지 않습니다.

V2 마이그레이션은 coupon.coupon_campaigns.notification_sent_at 컬럼을 추가합니다. AccountService, NotificationService를 먼저 배포하고 수신 플래그를 켠 뒤 CouponService의 요청 플래그를 켜야 합니다. Outbox 발행에는 기존 COUPON_OUTBOX_PUBLISHER_ENABLED가 필요합니다. 플래그 변경은 해당 서비스 재배포가 필요합니다. 이 변경은 운영 설정을 직접 수정하지 않습니다.
