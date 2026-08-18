package com.SIH.mark1.service;

import com.SIH.mark1.dto.request.LoginRequest;
import com.SIH.mark1.dto.request.RegisterRequest;
import com.SIH.mark1.dto.response.LoginResponse;
import com.SIH.mark1.dto.response.ProfileResponse;
import com.SIH.mark1.model.User;
import com.SIH.mark1.model.UserRole;
import com.SIH.mark1.dto.request.PasswordResetRequest;
import com.SIH.mark1.repository.UserRepository;
import com.SIH.mark1.security.jwt.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final OtpService otpService;

    public AuthenticationService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, AuthenticationManager authenticationManager, OtpService otpService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.otpService = otpService;
    }

    @Transactional
    public LoginResponse register(RegisterRequest req) {
        if (!otpService.isMobileVerifiedForRegistration(req.getMobile())) {
            throw new IllegalArgumentException("Mobile number not verified via OTP. Please verify OTP first.");
        }
        if (userRepository.existsByMobile(req.getMobile())) {
            throw new IllegalArgumentException("Mobile already in use");
        }
        if (req.getEmail() != null && userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }
        if (req.getPassword() == null || req.getPassword().length() < 6) {
            throw new IllegalArgumentException("Password too short");
        }

        User user = User.builder()
                .name(req.getName())
                .mobile(req.getMobile())
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(UserRole.CITIZEN)
                .language(req.getPreferredLanguage())
                .build();

        userRepository.save(user);

        // auto-login
        return generateTokensForUser(user);
    }


    @Transactional
    public LoginResponse createInitialAdmin(RegisterRequest req) {
        if (userRepository.countByRole(UserRole.ADMIN) > 0) {
            throw new IllegalStateException("Admin already exists");
        }
        if (userRepository.existsByMobile(req.getMobile())) {
            throw new IllegalArgumentException("Mobile already in use");
        }
        if (req.getEmail() != null && userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }
        if (req.getPassword() == null || req.getPassword().length() < 6) {
            throw new IllegalArgumentException("Password too short");
        }

        User admin = User.builder()
                .name(req.getName())
                .mobile(req.getMobile())
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(UserRole.ADMIN)
                .language(req.getPreferredLanguage())
                .build();

        userRepository.save(admin);
        return generateTokensForUser(admin);
    }
    public LoginResponse login(LoginRequest req) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(req.getMobile(), req.getPassword()));
        } catch (AuthenticationException e) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        User user = userRepository.findByMobile(req.getMobile()).orElseThrow(() -> new IllegalArgumentException("User not found"));
        return generateTokensForUser(user);
    }

    public LoginResponse refreshToken(String refreshToken) {
        String username = jwtService.extractUsername(refreshToken);
        Optional<User> userOpt = userRepository.findByMobile(username);
        if (userOpt.isEmpty()) userOpt = userRepository.findByEmail(username);
        if (userOpt.isEmpty()) throw new IllegalArgumentException("User not found");

        if (!jwtService.isTokenExpired(refreshToken)) {
            User user = userOpt.get();
            String access = jwtService.generateAccessToken(new org.springframework.security.core.userdetails.User(user.getMobile(), user.getPasswordHash(), java.util.Collections.emptyList()));
            return LoginResponse.builder()
                    .accessToken(access)
                    .refreshToken(refreshToken)
                    .userId(user.getUserId())
                    .name(user.getName())
                    .mobile(user.getMobile())
                    .email(user.getEmail())
                    .role(user.getRole())
                    .build();
        }
        throw new IllegalArgumentException("Refresh token expired");
    }

    public void logout(String refreshToken) {
        // Stateless implementation: client should discard tokens. For revocation, persist blacklist.
    }

    @Transactional
    public void resetPassword(PasswordResetRequest req) {
        User user = userRepository.findByMobile(req.getMobile())
                .orElseThrow(() -> new IllegalArgumentException("User not found with this mobile number"));
        if (user.getRole() == null) {
            throw new IllegalArgumentException("Invalid user role");
        }
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
    }

    public ProfileResponse getProfile(String mobileOrEmail) {
        User user = userRepository.findByMobile(mobileOrEmail).orElseGet(() -> userRepository.findByEmail(mobileOrEmail).orElse(null));
        if (user == null) throw new IllegalArgumentException("User not found");
        return ProfileResponse.builder()
                .id(user.getUserId())
                .name(user.getName())
                .mobile(user.getMobile())
                .email(user.getEmail())
                .role(user.getRole())
                .language(user.getLanguage())
                .build();
    }

    @Transactional
    public ProfileResponse updateProfile(String mobileOrEmail, ProfileResponse update) {
        User user = userRepository.findByMobile(mobileOrEmail).orElseGet(() -> userRepository.findByEmail(mobileOrEmail).orElse(null));
        if (user == null) throw new IllegalArgumentException("User not found");

        if (update.getName() != null) user.setName(update.getName());
        if (update.getEmail() != null) user.setEmail(update.getEmail());
        if (update.getLanguage() != null) user.setLanguage(update.getLanguage());

        userRepository.save(user);

        return ProfileResponse.builder()
                .id(user.getUserId())
                .name(user.getName())
                .mobile(user.getMobile())
                .email(user.getEmail())
                .role(user.getRole())
                .language(user.getLanguage())
                .build();
    }

    private LoginResponse generateTokensForUser(User user) {
        org.springframework.security.core.userdetails.UserDetails ud =
                new org.springframework.security.core.userdetails.User(user.getMobile(), user.getPasswordHash(), java.util.Collections.emptyList());
        String access = jwtService.generateAccessToken(ud);
        String refresh = jwtService.generateRefreshToken(ud);

        return LoginResponse.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .userId(user.getUserId())
                .name(user.getName())
                .mobile(user.getMobile())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }
}

