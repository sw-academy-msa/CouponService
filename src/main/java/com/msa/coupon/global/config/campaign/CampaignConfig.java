package com.msa.coupon.global.config.campaign;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 캠페인 설정은 환경변수로만 바꾸며 운영 중 API로 허용 목록을 고치지 않습니다. */
@Configuration
@EnableConfigurationProperties(CampaignProperties.class)
public class CampaignConfig {}
