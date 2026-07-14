package com.keepbooking.user.model;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.keepbooking.common.model.BaseEntity;
import com.keepbooking.reference.model.City;
import com.keepbooking.reference.model.Country;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity implements UserDetails {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstname;

    @Column(nullable = false)
    private String lastname;

    private String phone;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    // transient (Java, not JPA @Transient): User implements UserDetails, which extends
    // Serializable - these lazy JPA associations point to non-Serializable entities and would
    // break Java serialization (e.g. a Hibernate proxy holding a live session) if it were ever
    // triggered; excluding them from it doesn't affect normal JPA persistence.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id")
    private transient Country country;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private transient City city;

    private String avatarUrl;

    @Column(nullable = false)
    @Builder.Default
    private String language = "en";

    @Column(nullable = false)
    @Builder.Default
    private String timezone = "UTC";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserRole role = UserRole.ROLE_USER;

    // Scope of the role above - see UserRole for the exact combinations this can take.
    // ROLE_USER/ROLE_SUPER_ADMIN: both null. ROLE_COMPANY_ADMIN: companyId set, restaurantId
    // null (manages every restaurant in that company). ROLE_RESTAURANT_ADMIN: both set (manages
    // only that one restaurant). Enforced in the DB via chk_users_role_scope (V020).
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "restaurant_id")
    private Long restaurantId;

    private Instant deletedAt;

    // --- UserDetails ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.BLOCKED;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE && deletedAt == null;
    }
}
