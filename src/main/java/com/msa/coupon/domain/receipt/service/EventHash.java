package com.msa.coupon.domain.receipt.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 본문을 노출하지 않고 멱등 요청 충돌을 판별합니다. */
public final class EventHash {
    private EventHash() {}

    public static String of(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("HASH_UNAVAILABLE");
        }
    }
}