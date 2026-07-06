package com.darwin.authservice.security;

import com.darwin.authservice.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    private SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
    }

    public String generateToken(UserDetails user) {
        return buildToken(user, jwtExpiration, "access");
    }

    public String generateRefreshToken(UserDetails user) {
        return buildToken(user, refreshExpiration, "refresh");
    }

    private String buildToken(UserDetails user, long expiration, String tokenType) {
        var builder = Jwts.builder()
                .subject(user.getUsername())
                .claim("tokenType", tokenType)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration));

        if (user instanceof User identityUser) {
            builder.claim("userId", identityUser.getId().toString())
                    .claim("role", identityUser.getRole().name());
        }

        return builder
                .signWith(getSignKey())
                .compact();
    }

    public String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean isTokenValid(String token, UserDetails user) {
        String username = extractUsername(token);
        return username.equals(user.getUsername())
                && user.isEnabled()
                && user.isAccountNonLocked()
                && !isTokenExpired(token);
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(extractClaims(token).get("tokenType", String.class));
    }

    public boolean isAccessToken(String token) {
        return "access".equals(extractClaims(token).get("tokenType", String.class));
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration()
                .before(new Date());
    }
}
