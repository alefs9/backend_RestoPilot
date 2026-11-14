package com.restopilot.backend.security;

import com.restopilot.backend.modules.auth.entity.Rol;
import com.restopilot.backend.modules.auth.entity.Usuario;
import com.restopilot.backend.modules.auth.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupabaseJwtConfigTest {
    @Test
    void resolvesAuthoritiesFromDatabaseProfile() {
        var repository = mock(UsuarioRepository.class);
        var details = mock(CustomUserDetailsService.class);
        UUID authId = UUID.randomUUID();
        var user = Usuario.builder().id(7L).correo("owner@example.test").nombreCompleto("Owner")
                .passwordHash("unused").rol(Rol.DUENO).habilitado(true).build();
        when(repository.findCorreoByAuthUserId(authId)).thenReturn(Optional.of(user.getCorreo()));
        when(details.loadUserByUsername(user.getCorreo())).thenReturn(user);
        var auth = new SupabaseJwtConfig().supabaseAuthenticationConverter(repository, details).convert(jwt(authId));
        assertSame(user, auth.getPrincipal());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DUENO")));
    }

    @Test
    void rejectsTokenWithoutEnabledLinkedProfile() {
        var repository = mock(UsuarioRepository.class);
        var details = mock(CustomUserDetailsService.class);
        UUID authId = UUID.randomUUID();
        when(repository.findCorreoByAuthUserId(authId)).thenReturn(Optional.empty());
        assertThrows(OAuth2AuthenticationException.class,
                () -> new SupabaseJwtConfig().supabaseAuthenticationConverter(repository, details).convert(jwt(authId)));
        verifyNoInteractions(details);
    }

    private static Jwt jwt(UUID subject) {
        return Jwt.withTokenValue("signed-token-fixture").header("alg", "ES256")
                .subject(subject.toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
