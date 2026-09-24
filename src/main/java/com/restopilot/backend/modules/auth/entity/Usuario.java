package com.restopilot.backend.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import com.restopilot.backend.modules.restaurante.entity.Restaurante;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name="usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="nombre_completo", nullable= false, length = 150)
    private String nombreCompleto;

    @Column(name = "correo", nullable = false, unique = true, length = 100)
    private String correo;

    @Column(name="password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name="rol", nullable = false, length = 30)
    private Rol rol;

    @Builder.Default
    @Column(name = "habilitado", nullable = false)
    private Boolean habilitado = true;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurante_id", nullable = true)
    private Restaurante restaurante;
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;
    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;
    @PrePersist
    protected void onCreate() {
        this.creadoEn = LocalDateTime.now();
        this.actualizadoEn = LocalDateTime.now();
        if (this.habilitado == null) {
            this.habilitado = true;
        }
    }
    @PreUpdate
    protected void onUpdate() {
        this.actualizadoEn = LocalDateTime.now();
    }
    // Métodos requeridos por Spring Security (UserDetails)
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
    }
    @Override
    public String getPassword() {
        return this.passwordHash;
    }
    @Override
    public String getUsername() {
        return this.correo;
    }
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
    @Override
    public boolean isEnabled() {
        return this.habilitado != null && this.habilitado;
    }
}





