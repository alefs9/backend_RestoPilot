package com.restopilot.backend.modules.restaurante.service;

import com.restopilot.backend.core.exception.ResourceNotFoundException;
import com.restopilot.backend.modules.restaurante.dto.RestauranteResponseDTO;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;
import com.restopilot.backend.modules.restaurante.repository.RestauranteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultaRestauranteService {
    private final RestauranteRepository repository;
    private final Clock clock;

    public List<RestauranteResponseDTO> listar() {
        return repository.findAll(Sort.by("id")).stream()
                .filter(r -> Boolean.TRUE.equals(r.getActivo())).map(this::mapear).toList();
    }
    public RestauranteResponseDTO consultar(Long id) {
        return mapear(repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado.")));
    }
    private RestauranteResponseDTO mapear(Restaurante r) {
        LocalTime now = LocalTime.now(clock);
        // Misma ventana [apertura,cierre) que valida PedidoService; no cambia reglas de horario.
        boolean abierto = Boolean.TRUE.equals(r.getActivo()) && (r.getHoraApertura() == null || r.getHoraCierre() == null
                || (!now.isBefore(r.getHoraApertura()) && now.isBefore(r.getHoraCierre())));
        return new RestauranteResponseDTO(r.getId(), r.getNombre(), r.getDireccion(), Boolean.TRUE.equals(r.getActivo()),
                abierto, Boolean.TRUE.equals(r.getTieneAtencionFisica()), Boolean.TRUE.equals(r.getAceptaDelivery()),
                r.getHoraApertura(), r.getHoraCierre());
    }
}
