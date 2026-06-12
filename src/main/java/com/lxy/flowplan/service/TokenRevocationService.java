package com.lxy.flowplan.service;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenRevocationService {

    private final Set<String> revokedTokens = ConcurrentHashMap.newKeySet();

    public void revoke(String token) {
        revokedTokens.add(normalize(token));
    }

    public boolean isRevoked(String token) {
        return revokedTokens.contains(normalize(token));
    }

    private String normalize(String token) {
        if (token == null) {
            return "";
        }
        String normalized = token.trim();
        if (normalized.startsWith("Bearer ")) {
            return normalized.substring(7);
        }
        return normalized;
    }
}
