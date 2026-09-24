package com.restopilot.backend.modules.auth.dto;

import com.restopilot.backend.modules.auth.entity.Rol;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponseDTO {

    private String mensaje;
    private String token;
    private Long id;
    private String nombreCompleto;
    private String correo;
    private Rol rol;
}