package io.github.nanmazino.chatrebuild.auth.service;

import io.github.nanmazino.chatrebuild.auth.dto.request.LoginRequest;
import io.github.nanmazino.chatrebuild.auth.dto.response.LoginResponse;
import io.github.nanmazino.chatrebuild.auth.exception.InvalidLoginCredentialsException;
import io.github.nanmazino.chatrebuild.global.security.JwtTokenProvider;
import io.github.nanmazino.chatrebuild.user.entity.User;
import io.github.nanmazino.chatrebuild.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(InvalidLoginCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidLoginCredentialsException();
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());

        return LoginResponse.of(accessToken, user.getId(), user.getEmail(), user.getNickname());
    }
}
