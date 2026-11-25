package com.smartlibrary.security;

import com.smartlibrary.entity.Role;
import com.smartlibrary.entity.User;
import com.smartlibrary.entity.UserStatus;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String name;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private final boolean enabled;

    private UserPrincipal(User u) {
        this.id = u.getId();
        this.name = u.getName();
        this.email = u.getEmail();
        this.passwordHash = u.getPasswordHash();
        this.role = u.getRole();
        this.enabled = u.getStatus() == UserStatus.ACTIVE;
    }

    public static UserPrincipal from(User u) {
        return new UserPrincipal(u);
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return passwordHash; }

    @Override public String getUsername() { return email; }

    @Override public boolean isEnabled() { return enabled; }
}
