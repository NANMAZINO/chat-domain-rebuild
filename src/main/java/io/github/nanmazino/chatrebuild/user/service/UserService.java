package io.github.nanmazino.chatrebuild.user.service;

import io.github.nanmazino.chatrebuild.user.dto.request.SignUpRequest;
import io.github.nanmazino.chatrebuild.user.dto.response.SignUpResponse;
import io.github.nanmazino.chatrebuild.user.entity.User;
import io.github.nanmazino.chatrebuild.user.exception.DuplicateEmailException;
import io.github.nanmazino.chatrebuild.user.exception.DuplicateNicknameException;
import io.github.nanmazino.chatrebuild.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignUpResponse signUp(SignUpRequest request) {
        validateDuplicateEmail(request.email());
        validateDuplicateNickname(request.nickname());

        String encodedPassword = passwordEncoder.encode(request.password());

        User user = new User(request.email(), encodedPassword, request.nickname());

        User savedUser = userRepository.save(user);

        return new SignUpResponse(savedUser.getId(), savedUser.getEmail(), savedUser.getNickname()
        );
    }

    private void validateDuplicateNickname(String nickname) {
        if (userRepository.existsByNickname(nickname)) {
            throw new DuplicateNicknameException();
        }
    }

    private void validateDuplicateEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
    }
}
