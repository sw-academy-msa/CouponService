-- 받기형 캠페인의 적용 상품을 선택적으로 고정합니다.
ALTER TABLE coupon.coupon_campaigns ADD COLUMN product_id UUID;
ALTER TABLE coupon.coupon_campaigns ADD CONSTRAINT ck_campaign_product_kind CHECK (product_id IS NULL OR kind = 'CLAIM');
