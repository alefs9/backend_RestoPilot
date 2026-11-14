package com.restopilot.backend.security;

import com.nimbusds.jwt.SignedJWT;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.web.client.RestOperations;

import java.net.URI;
import java.text.ParseException;
import java.util.Map;

/** Uses Supabase Auth itself to verify legacy HS256 tokens; JWKS verifies asymmetric tokens locally. */
final class SupabaseHybridJwtDecoder implements JwtDecoder {
    private final JwtDecoder asymmetric;
    private final String issuer;
    private final String publishableKey;
    private final RestOperations auth;
    private final OAuth2TokenValidator<Jwt> validator;

    SupabaseHybridJwtDecoder(JwtDecoder asymmetric, String issuer, String publishableKey,
                             RestOperations auth, OAuth2TokenValidator<Jwt> validator) {
        this.asymmetric = asymmetric;
        this.issuer = issuer;
        this.publishableKey = publishableKey;
        this.auth = auth;
        this.validator = validator;
    }

    @Override public Jwt decode(String token) throws JwtException {
        final SignedJWT signed;
        try { signed = SignedJWT.parse(token); }
        catch (ParseException ex) { throw new BadJwtException("Token malformado", ex); }
        String algorithm = signed.getHeader().getAlgorithm().getName();
        if (!"HS256".equals(algorithm)) return asymmetric.decode(token);
        if (publishableKey.isBlank()) throw new BadJwtException("Falta configuracion publica de Supabase");
        final String verifiedUserId;
        try {
            var headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.set("apikey", publishableKey);
            var response = auth.exchange(new RequestEntity<Void>(headers, org.springframework.http.HttpMethod.GET,
                    URI.create(issuer + "/user")), Map.class);
            Object id = response.getBody() == null ? null : response.getBody().get("id");
            if (!(id instanceof String value)) throw new BadJwtException("Identidad de Auth ausente");
            verifiedUserId = value;
        } catch (BadJwtException ex) { throw ex; }
        catch (RuntimeException ex) { throw new BadJwtException("Supabase Auth no pudo verificar el token", ex); }
        try {
            var claims = MappedJwtClaimSetConverter.withDefaults(Map.of())
                    .convert(signed.getJWTClaimsSet().getClaims());
            Jwt jwt = new Jwt(token, (java.time.Instant) claims.get("iat"),
                    (java.time.Instant) claims.get("exp"), signed.getHeader().toJSONObject(), claims);
            if (!verifiedUserId.equals(jwt.getSubject()) || validator.validate(jwt).hasErrors())
                throw new BadJwtException("Token o identidad no valida");
            return jwt;
        } catch (ParseException | IllegalArgumentException ex) {
            throw new BadJwtException("Claims de token no validos", ex);
        }
    }
}
