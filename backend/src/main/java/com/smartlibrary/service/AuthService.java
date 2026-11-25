package com.smartlibrary.service;

import com.smartlibrary.config.LibraryProperties;
import com.smartlibrary.dto.AuthDto.AuthResponse;
import com.smartlibrary.dto.AuthDto.LoginRequest;
import com.smartlibrary.dto.AuthDto.RegisterRequest;
import com.smartlibrary.dto.UserDto.UserResponse;
import com.smartlibrary.entity.Role;
import com.smartlibrary.entity.User;
import com.smartlibrary.entity.UserStatus;
import com.smartlibrary.exception.ConflictException;
import com.smartlibrary.repository.UserRepository;
import com.smartlibrary.security.JwtService;
import com.smartlibrary.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final MembershipService membershipService;
    private final LibraryProperties rules;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists");
        }
        User user = new User();
        user.setName(req.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(Role.MEMBER);            // self-registration can never create an ADMIN
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        if (rules.defaultMembershipDays() > 0) {
            membershipService.createInitial(user, "TRIAL", rules.defaultMembershipDays());
        }
        return tokenFor(UserPrincipal.from(user), user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email().trim().toLowerCase(), req.password()));
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        User user = userRepository.findById(principal.getId()).orElseThrow();
        return tokenFor(principal, user);
    }

    private AuthResponse tokenFor(UserPrincipal principal, User user) {
        return new AuthResponse(jwtService.generate(principal), "Bearer", jwtService.expiresInSeconds(),
                UserResponse.from(user));
    }
}
