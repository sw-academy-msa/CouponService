package com.msa.coupon.global.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.*;

/** 서비스 주체와 scope를 사용자 권한과 분리해 확인합니다. */
@Component("orderServiceAccess")
public class OrderServiceAccess {
    private final String subject;

    public OrderServiceAccess(@Value("${ORDER_SERVICE_SUBJECT:}") String subject) {
        this.subject = subject;
    }

    public boolean allowed(Jwt jwt) {
        return jwt != null
                && !subject.isBlank()
                && subject.equals(jwt.getSubject())
                && "service".equals(jwt.getClaimAsString("principal_type"))
                && "order-command-service".equals(jwt.getClaimAsString("client_id"))
                && jwt.getClaimAsString("scope") != null
                && Arrays.asList(jwt.getClaimAsString("scope").split(" +")).contains("coupon.lock");
    }
}
