package com.msa.coupon.global.config.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** ?? ???? ??? ID? ?? Account ?? ??? ?????. */
@ConfigurationProperties("coupon.clients")
public record ClientProperties(
        String scheme,
        String account,
        String ai,
        String productQuery,
        String orderCommand,
        String clientSecret,
        String serviceSubject) {
    public ClientProperties {
        scheme = scheme == null || scheme.isBlank() ? "https" : scheme;
    }

    @Override
    public String toString() {
        return "ClientProperties[REDACTED]";
    }
}
