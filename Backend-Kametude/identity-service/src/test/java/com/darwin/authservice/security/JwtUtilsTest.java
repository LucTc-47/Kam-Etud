package com.darwin.authservice.security;

import com.darwin.authservice.entity.Role;
import com.darwin.authservice.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilsTest {

    private static final String SECRET =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Test
    void accessTokenContainsClaimsExpectedByGateway() {
        JwtUtils jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpiration", 60_000L);
        ReflectionTestUtils.setField(jwtUtils, "refreshExpiration", 120_000L);
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("student@example.com")
                .password("encoded")
                .role(Role.STUDENT)
                .build();

        String token = jwtUtils.generateToken(user);
        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo(user.getEmail());
        assertThat(claims.get("userId", String.class)).isEqualTo(userId.toString());
        assertThat(claims.get("role", String.class)).isEqualTo("STUDENT");
        assertThat(claims.get("tokenType", String.class)).isEqualTo("access");
    }
}
