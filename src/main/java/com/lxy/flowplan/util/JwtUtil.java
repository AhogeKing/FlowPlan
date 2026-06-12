package com.lxy.flowplan.util;

import com.lxy.flowplan.exception.JwtAuthenticationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtUtil {

    private String secret;
    private long expirationHours = 24;

    // 接收业务数据，生成 Token 并返回
    public String createToken(String subject, Map<String, Object> claims) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + Duration.ofHours(expirationHours).toMillis());
        Map<String, Object> tokenClaims = new HashMap<>(claims);

        return Jwts.builder()
                .subject(subject)
                .claims(tokenClaims)
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(getSecretKey())
                .compact();
    }

    public String createToken(Map<String, Object> claims) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + Duration.ofHours(expirationHours).toMillis());
        Map<String, Object> tokenClaims = new HashMap<>(claims);

        return Jwts.builder()
                .claims(tokenClaims)
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(getSecretKey())
                .compact();
    }

    // 接收 Token，验证 Token，返回业务数据
    public Claims parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtAuthenticationException("未登录");
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        try {
            return Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new JwtAuthenticationException("登录状态已失效，请重新登录", e);
        }
    }

    private SecretKey getSecretKey() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.jwt.secret 未配置");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
