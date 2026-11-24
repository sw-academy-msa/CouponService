package com.msa.coupon.domain.coupon.command.service.impl;

import com.msa.coupon.domain.campaign.dto.request.*;
import com.msa.coupon.domain.campaign.dto.response.*;
import com.msa.coupon.domain.campaign.entity.*;
import com.msa.coupon.domain.campaign.exception.*;
import com.msa.coupon.domain.campaign.repository.*;
import com.msa.coupon.domain.coupon.command.service.CouponClaimService;
import com.msa.coupon.domain.coupon.dto.request.*;
import com.msa.coupon.domain.coupon.dto.response.*;
import com.msa.coupon.domain.coupon.entity.*;
import com.msa.coupon.domain.coupon.exception.*;
import com.msa.coupon.domain.coupon.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.*;

/** 유니크 경합이 롤백된 뒤 새 조회로 기존 발급을 돌려줍니다. */
@Service
@RequiredArgsConstructor
public class CouponClaimServiceImpl implements CouponClaimService {
    private final com.msa.coupon.domain.campaign.command.service.CampaignIssueStore store;
    private final CouponCampaignRepository campaigns;
    private final CouponIssueRepository issues;

    public ClaimResponse claim(String code, UUID account) {
        var c =
                campaigns
                        .findByCode(code)
                        .orElseThrow(
                                () -> new CampaignException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
        try {
            return store.issue(
                    code, account, "CLAIM", "CLAIM", "claim:" + c.id + ":" + account, null, null);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            var i = issues.findByCampaignIdAndAccountIdAndSubjectKey(c.id, account, "CLAIM");
            if (i.isPresent()) return new ClaimResponse(IssueResponse.from(i.get()), false);
            throw e;
        }
    }
}
