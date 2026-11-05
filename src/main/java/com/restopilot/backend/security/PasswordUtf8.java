package com.restopilot.backend.security;

import com.restopilot.backend.core.exception.BusinessRuleException;
import java.nio.charset.StandardCharsets;

public final class PasswordUtf8 {
    private PasswordUtf8() {}

    public static boolean dentroDelLimite(String password) {
        // Los null/vacíos los rechazan las reglas @NotBlank existentes.
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    public static void validar(String password) {
        if (!dentroDelLimite(password)) {
            throw new BusinessRuleException("La contraseña no puede superar 72 bytes UTF-8.");
        }
    }
}