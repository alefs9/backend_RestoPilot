package com.restopilot.backend.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.RestOperations;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupabaseHybridJwtDecoderTest {
    private static final String ISSUER = "https://example.supabase.co/auth/v1";

    @Test void acceptsLegacyTokenOnlyAfterAuthVerifiesSameSubject() throws Exception {
        var auth = mock(RestOperations.class);
        var asymmetric = mock(JwtDecoder.class);
        String subject = UUID.randomUUID().toString();
        when(auth.exchange(any(RequestEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("id", subject)));
        var decoder = decoder(asymmetric, auth);
        assertEquals(subject, decoder.decode(token(subject)).getSubject());
        verifyNoInteractions(asymmetric);
    }

    @Test void rejectsLegacyTokenWhenAuthReportsDifferentUser() throws Exception {
        var auth = mock(RestOperations.class);
        when(auth.exchange(any(RequestEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("id", UUID.randomUUID().toString())));
        assertThrows(BadJwtException.class,
                () -> decoder(mock(JwtDecoder.class), auth).decode(token(UUID.randomUUID().toString())));
    }

    private static SupabaseHybridJwtDecoder decoder(JwtDecoder asymmetric, RestOperations auth) {
        return new SupabaseHybridJwtDecoder(asymmetric, ISSUER, "public-test-key", auth,
                jwt -> OAuth2TokenValidatorResult.success());
    }

    private static String token(String subject) throws Exception {
        var claims = new JWTClaimsSet.Builder().subject(subject).issuer(ISSUER)
                .issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis() + 60000))
                .audience("authenticated").claim("role", "authenticated").build();
        var signed = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signed.sign(new MACSigner(new byte[32]));
        return signed.serialize();
    }
}
