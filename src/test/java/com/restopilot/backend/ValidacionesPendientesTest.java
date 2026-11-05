package com.restopilot.backend;

import com.restopilot.backend.modules.auth.dto.RegisterRequestDTO;
import com.restopilot.backend.modules.reservas.dto.EstadoMesaRequestDTO;
import com.restopilot.backend.modules.reservas.dto.EstadoModuloRequestDTO;
import com.restopilot.backend.security.PasswordUtf8;
import com.restopilot.backend.core.exception.BusinessRuleException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ValidacionesPendientesTest {
    @Test void passwordNoSeTruncaSilenciosamente() {
        String prefijo = "Aa1#" + "a".repeat(68);
        assertEquals(72, prefijo.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertTrue(PasswordUtf8.dentroDelLimite(prefijo));
        assertThrows(BusinessRuleException.class, () -> PasswordUtf8.validar(prefijo + "X"));
        assertThrows(BusinessRuleException.class, () -> PasswordUtf8.validar(prefijo + "Y"));
        assertFalse(PasswordUtf8.dentroDelLimite("Aa1#" + "ñ".repeat(35)));
    }

    @Test void clienteValidaNombreYPasswordAntesDePersistir() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var dto = RegisterRequestDTO.builder().nombreCompleto("A".repeat(150))
                    .correo("nuevo@audit.test").password("Aa1#" + "a".repeat(68)).build();
            assertTrue(validator.validate(dto).isEmpty());
            dto.setNombreCompleto("A".repeat(151));
            assertTrue(validator.validate(dto).stream()
                    .anyMatch(v -> v.getPropertyPath().toString().equals("nombreCompleto")));
            dto.setNombreCompleto("Prueba");
            dto.setPassword("Aa1#" + "a".repeat(69));
            assertFalse(validator.validate(dto).isEmpty());
        }
    }

    @Test void flagsObligatoriosAceptanFalsePeroNoNull() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new EstadoMesaRequestDTO(null)).isEmpty());
            assertFalse(validator.validate(new EstadoModuloRequestDTO(null)).isEmpty());
            assertTrue(validator.validate(new EstadoMesaRequestDTO(false)).isEmpty());
            assertTrue(validator.validate(new EstadoModuloRequestDTO(false)).isEmpty());
        }
    }
}