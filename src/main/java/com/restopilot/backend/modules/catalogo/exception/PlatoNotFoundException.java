package com.restopilot.backend.modules.catalogo.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class PlatoNotFoundException extends RuntimeException {
    public PlatoNotFoundException(Long id) {
        super("Plato con ID " + id + " no encontrado.");
    }
}