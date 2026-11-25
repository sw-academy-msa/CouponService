package com.msa.coupon.domain.coupon.command.service.impl;

import com.msa.coupon.domain.campaign.command.service.CampaignIssueStore;
import com.msa.coupon.domain.campaign.exception.CampaignErrorCode;
import com.msa.coupon.domain.campaign.exception.CampaignException;
import com.msa.coupon.domain.campaign.repository.CouponCampaignRepository;
import com.msa.coupon.domain.coupon.command.service.CouponClaimService;
import com.msa.coupon.domain.coupon.dto.response.ClaimResponse;
import com.msa.coupon.domain.coupon.dto.response.IssueResponse;
import com.msa.coupon.domain.coupon.repository.CouponIssueRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** 유니크 경합이 롤백된 뒤 새 조회로 기존 발급을 돌려줍니다. */
@Service
public class CouponClaimServiceImpl implements CouponClaimService {
    private final CampaignIssueStore store;
    private final CouponCampaignRepository campaigns;
    private final CouponIssueRepository issues;

    public CouponClaimServiceImpl(
            CampaignIssueStore store,
            CouponCampaignRepository campaigns,
            CouponIssueRepository issues) {
        this.store = store;
        this.campaigns = campaigns;
        this.issues = issues;
    }

    public ClaimResponse claim(String code, UUID account) {
        var c =
                campaigns
                        .findByCode(code)
                        .orElseThrow(
                                () -> new CampaignException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
        try {
            return store.issue(
                    code, account, "CLAIM", "CLAIM", "claim:" + c.id + ":" + account, null, null);
        } catch (DataIntegrityViolationException e) {
            var i = issues.findByCampaignIdAndAccountIdAndSubjectKey(c.id, account, "CLAIM");
            if (i.isPresent()) return new ClaimResponse(IssueResponse.from(i.get()), false);
            throw e;
        }
    }
}