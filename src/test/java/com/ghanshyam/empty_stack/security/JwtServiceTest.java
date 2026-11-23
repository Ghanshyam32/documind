package com.ghanshyam.empty_stack.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET_A = Base64.getEncoder()
            .encodeToString("an-unguessable-test-secret-of-32+bytes!".getBytes());
    private static final String SECRET_B = Base64.getEncoder()
            .encodeToString("a-completely-different-32+byte-secret!!".getBytes());

    private final UserDetails jane = User.withUsername("jane@example.com").password("x").roles("USER").build();
    private final UserDetails bob = User.withUsername("bob@example.com").password("x").roles("USER").build();

    private static JwtService jwtService(String secret, long expirationMs) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", secret);
        ReflectionTestUtils.setField(service, "expiration", expirationMs);
        return service;
    }

    @Test
    void issuedTokenCarriesTheUserEmailAndIsValid() {
        JwtService service = jwtService(SECRET_A, 60_000);

        String token = service.generateToken(jane);

        assertThat(service.extractEmail(token)).isEqualTo("jane@example.com");
        assertThat(service.isTokenValid(token, jane)).isTrue();
    }

    @Test
    void tokenIsNotValidForAnotherUser() {
        JwtService service = jwtService(SECRET_A, 60_000);

        assertThat(service.isTokenValid(service.generateToken(jane), bob)).isFalse();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String forged = jwtService(SECRET_B, 60_000).generateToken(jane);

        assertThatThrownBy(() -> jwtService(SECRET_A, 60_000).isTokenValid(forged, jane))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService service = jwtService(SECRET_A, -1_000);

        String token = service.generateToken(jane);

        assertThatThrownBy(() -> service.isTokenValid(token, jane)).isInstanceOf(ExpiredJwtException.class);
    }
}
