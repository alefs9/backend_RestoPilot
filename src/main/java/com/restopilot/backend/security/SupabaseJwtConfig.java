package com.restopilot.backend.security;

import com.restopilot.backend.modules.auth.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.core.convert.converter.Converter;

import java.util.UUID;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@ConditionalOnProperty(name = "restopilot.auth.provider", havingValue = "supabase")
public class SupabaseJwtConfig {
    @Bean
    JwtDecoder supabaseJwtDecoder(@Value("${restopilot.auth.supabase.issuer}") String issuer,
                                 @Value("${restopilot.auth.supabase.publishable-key}") String publishableKey) {
        if (!issuer.startsWith("https://") || !issuer.endsWith("/auth/v1")) {
            throw new IllegalArgumentException("El issuer de Supabase debe ser HTTPS y terminar en /auth/v1");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json")
                .jwsAlgorithm(SignatureAlgorithm.ES256).build();
        OAuth2TokenValidator<Jwt> standard = JwtValidators.createDefaultWithIssuer(issuer);
        decoder.setJwtValidator(jwt -> {
            OAuth2TokenValidatorResult result = standard.validate(jwt);
            if (result.hasErrors()) return result;
            if (!jwt.getAudience().contains("authenticated") || !"authenticated".equals(jwt.getClaimAsString("role"))) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token de usuario no autenticado", null));
            }
            return OAuth2TokenValidatorResult.success();
        });
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(5000);
        return new SupabaseHybridJwtDecoder(decoder, issuer, publishableKey,
                new RestTemplate(requestFactory), jwt -> {
                    var result = standard.validate(jwt);
                    if (result.hasErrors()) return result;
                    if (!jwt.getAudience().contains("authenticated") ||
                            !"authenticated".equals(jwt.getClaimAsString("role")))
                        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token de usuario no autenticado", null));
                    return OAuth2TokenValidatorResult.success();
                });
    }

    @Bean
    Converter<Jwt, UsernamePasswordAuthenticationToken> supabaseAuthenticationConverter(
            UsuarioRepository usuarios, CustomUserDetailsService userDetails) {
        return new Converter<Jwt, UsernamePasswordAuthenticationToken>() {
          @Override public UsernamePasswordAuthenticationToken convert(Jwt jwt) {
            UUID authId;
            try { authId = UUID.fromString(jwt.getSubject()); }
            catch (RuntimeException ex) { throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token")); }
            String correo = usuarios.findCorreoByAuthUserId(authId)
                    .orElseThrow(() -> new OAuth2AuthenticationException(new OAuth2Error("invalid_token")));
            var principal = userDetails.loadUserByUsername(correo);
            if (!principal.isEnabled()) throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
            return new UsernamePasswordAuthenticationToken(principal, jwt, principal.getAuthorities());
          }
        };
    }
}
