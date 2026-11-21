package com.restopilot.backend.modules.ia.service;

import com.restopilot.backend.modules.ia.dto.ChatIAResponseDTO;
import com.restopilot.backend.modules.reportes.service.ReporteService;
import com.restopilot.backend.security.AccesoRestaurante;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
public class AgenteIAService {
    private static final Logger LOG = LoggerFactory.getLogger(AgenteIAService.class);
    private static final String INSTRUCCIONES_ADMIN = """
            Eres el asistente administrativo de RestoPilot. Responde en español.
            Puedes consultar platos más vendidos y pagos por período. Usa la herramienta correspondiente
            para toda consulta sobre esos indicadores. Envía las fechas ISO inclusivas; un mes con año
            especificados se convierte al primer y último día de ese mes. Si el período es ambiguo o falta,
            llama a aclararPeriodo. Realiza como máximo una llamada de herramienta. Nunca inventes cifras.
            Si piden otros indicadores, indica que todavía no están disponibles en este chat.
            No solicites credenciales o datos personales.
            """;

    private final ObjectProvider<ChatModel> modelos;
    private final ReporteService reportes;
    private final AccesoRestaurante acceso;

    public AgenteIAService(ObjectProvider<ChatModel> modelos, ReporteService reportes, AccesoRestaurante acceso) {
        this.modelos = modelos;
        this.reportes = reportes;
        this.acceso = acceso;
    }

    public ChatIAResponseDTO responderAdministrativo(String mensaje, Long restauranteId) {
        ChatModel modelo = modelos.getIfAvailable();
        if (modelo == null) {
            throw new FalloChat(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE",
                    "El asistente administrativo no está disponible.");
        }

        var herramientas = new HerramientasReportes(reportes, acceso, restauranteId);
        long inicio = System.nanoTime();
        String respuesta;
        try {
            respuesta = ChatClient.create(modelo).prompt()
                    .system(INSTRUCCIONES_ADMIN)
                    .user(mensaje)
                    .tools(herramientas)
                    .call()
                    .content();
        } catch (RuntimeException ex) {
            if (herramientas.fallo() != null) {
                throw herramientas.fallo();
            }
            LOG.warn("Falló la consulta al proveedor de IA. tipo={} duraciónMs={}",
                    ex.getClass().getSimpleName(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio));
            throw falloProveedor();
        }
        if (herramientas.fallo() != null) {
            throw herramientas.fallo();
        }
        if (herramientas.resultado() != null) {
            return herramientas.resultado();
        }
        if (respuesta == null || respuesta.isBlank()) {
            LOG.warn("El proveedor de IA devolvió una respuesta vacía. duraciónMs={}",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio));
            throw falloProveedor();
        }
        return new ChatIAResponseDTO(ChatIAResponseDTO.Estado.ANSWER, respuesta);
    }

    private FalloChat falloProveedor() {
        return new FalloChat(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_FAILURE",
                "El asistente no pudo responder. Intente nuevamente.");
    }

    public static final class FalloChat extends RuntimeException {
        private final HttpStatus estado;
        private final String codigo;

        private FalloChat(HttpStatus estado, String codigo, String mensaje) {
            super(mensaje);
            this.estado = estado;
            this.codigo = codigo;
        }

        public HttpStatus estado() {
            return estado;
        }

        public String codigo() {
            return codigo;
        }
    }
}
