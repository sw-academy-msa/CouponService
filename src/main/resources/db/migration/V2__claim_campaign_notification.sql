-- 캠페인 잠금과 같은 트랜잭션에서 발송 요청을 한 번만 기록합니다.
ALTER TABLE coupon.coupon_campaigns ADD COLUMN notification_sent_at timestamptz;
