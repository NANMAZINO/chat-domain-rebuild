package io.github.nanmazino.chatrebuild.global.security;

import io.github.nanmazino.chatrebuild.user.entity.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record JwtPrincipal(
    Long userId,
    String email,
    String password,
    String nickname,
    Collection<? extends GrantedAuthority> authorities
) implements UserDetails {

    private static final List<GrantedAuthority> DEFAULT_AUTHORITIES = List.of(
        new SimpleGrantedAuthority("ROLE_USER")
    );

    public static JwtPrincipal from(User user) {
        return new JwtPrincipal(
            user.getId(),
            user.getEmail(),
            user.getPassword(),
            user.getNickname(),
            DEFAULT_AUTHORITIES
        );
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
}
