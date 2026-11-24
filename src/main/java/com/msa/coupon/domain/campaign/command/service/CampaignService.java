package com.msa.coupon.domain.campaign.command.service;

import com.msa.coupon.domain.campaign.dto.request.*;
import com.msa.coupon.domain.campaign.dto.response.*;
import com.msa.coupon.domain.campaign.entity.*;
import com.msa.coupon.domain.campaign.exception.*;
import com.msa.coupon.domain.campaign.repository.*;
import com.msa.coupon.domain.coupon.dto.request.*;
import com.msa.coupon.domain.coupon.dto.response.*;
import com.msa.coupon.domain.coupon.entity.*;
import com.msa.coupon.domain.coupon.exception.*;
import com.msa.coupon.domain.coupon.repository.*;

import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.*;

/** 도메인 경계를 인터페이스로 분리합니다. */
public interface CampaignService {
    CampaignStatsResponse stats(UUID id);

    List<CampaignResponse> list();

    CampaignResponse create(CampaignRequest r);

    CampaignResponse state(UUID id, String status);

    CampaignResponse budget(UUID id, int total);
}
