package com.SIH.mark1.service;

import com.SIH.mark1.dto.request.FirebaseLoginRequest;
import com.SIH.mark1.dto.request.FirebaseRegisterRequest;
import com.SIH.mark1.dto.request.FirebaseResetPasswordRequest;
import com.SIH.mark1.dto.response.LoginResponse;
import com.SIH.mark1.model.User;
import com.SIH.mark1.model.UserRole;
import com.SIH.mark1.repository.UserRepository;
import com.SIH.mark1.security.jwt.JwtService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles Firebase Phone-OTP based registration.
 *
 * Flow:
 * 1. Flutter app sends OTP via Firebase client SDK and verifies it client-side.
 * 2. App receives a Firebase ID token and sends it here along with registration details.
 * 3. This service verifies the ID token with the Firebase Admin SDK and extracts
 *    the verified phone number from the token claims.
 * 4. A new CITIZEN user is created and app JWT tokens are returned.
 *
 * Dev fallback: when firebase.dev-mock-enabled=true, an idToken of the form
 * "mock-<mobile>" (e.g. "mock-7057257037") is accepted without contacting Firebase.
 * NEVER enable this in production.
 */
@Service
public class FirebaseAuthService {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthService.class);
    private static final String MOCK_TOKEN_PREFIX = "mock-";

    private final FirebaseAuth firebaseAuth; // may be null when not configured
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final boolean devMockEnabled;

    public FirebaseAuthService(ObjectProvider<FirebaseAuth> firebaseAuthProvider,
                               UserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               JwtService jwtService,
                               @Value("${firebase.dev-mock-enabled:false}") boolean devMockEnabled) {
        this.firebaseAuth = firebaseAuthProvider.getIfAvailable();
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.devMockEnabled = devMockEnabled;
    }

    @Transactional
    public LoginResponse register(FirebaseRegisterRequest req) {
        String mobile = verifyTokenAndExtractMobile(req.getIdToken());

        if (userRepository.existsByMobile(mobile)) {
            throw new IllegalArgumentException("Mobile already in use");
        }
        if (req.getEmail() != null && !req.getEmail().isBlank() && userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = User.builder()
                .name(req.getName())
                .mobile(mobile)
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(UserRole.CITIZEN)
                .language(req.getPreferredLanguage())
                .build();

        userRepository.save(user);
        log.info("User registered via Firebase OTP: mobile={}", mobile);

        return generateTokensForUser(user);
    }

    /**
     * Firebase Phone-OTP login for existing users.
     * The ID token's verified phone number identifies the account — no password needed.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(FirebaseLoginRequest req) {
        String mobile = verifyTokenAndExtractMobile(req.getIdToken());
        User user = userRepository.findByMobile(mobile)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No account found for this mobile number. Please register first."));
        if (Boolean.TRUE.equals(user.getDeleted())) {
            throw new IllegalArgumentException("This account has been deactivated.");
        }
        log.info("User logged in via Firebase OTP: mobile={}", mobile);
        return generateTokensForUser(user);
    }

    /**
     * Resets a user's password using a Firebase-verified phone number as proof of ownership.
     */
    @Transactional
    public void resetPassword(FirebaseResetPasswordRequest req) {
        String mobile = verifyTokenAndExtractMobile(req.getIdToken());
        User user = userRepository.findByMobile(mobile)
                .orElseThrow(() -> new IllegalArgumentException("No account found for this mobile number."));
        if (Boolean.TRUE.equals(user.getDeleted())) {
            throw new IllegalArgumentException("This account has been deactivated.");
        }
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setUpdatedBy(user.getUserId());
        userRepository.save(user);
        log.info("Password reset via Firebase OTP: mobile={}", mobile);
    }

    /**
     * Verifies the Firebase ID token and returns the phone number (10-digit, without +91).
     */
    private String verifyTokenAndExtractMobile(String idToken) {
        // Dev-only mock path
        if (devMockEnabled && idToken != null && idToken.startsWith(MOCK_TOKEN_PREFIX)) {
            String mobile = idToken.substring(MOCK_TOKEN_PREFIX.length());
            log.warn("[DEV MOCK] Accepting mock Firebase token for mobile={}", mobile);
            return normalizeMobile(mobile);
        }

        if (firebaseAuth == null) {
            throw new IllegalStateException(
                    "Firebase is not configured on the server. Add firebase-service-account.json to resources.");
        }

        final FirebaseToken decoded;
        try {
            decoded = firebaseAuth.verifyIdToken(idToken);
        } catch (FirebaseAuthException e) {
            throw new IllegalArgumentException("Invalid or expired Firebase ID token: " + e.getMessage());
        }

        Object phoneClaim = decoded.getClaims().get("phone_number");
        if (phoneClaim == null || phoneClaim.toString().isBlank()) {
            throw new IllegalArgumentException("Firebase token does not contain a verified phone number");
        }

        return normalizeMobile(phoneClaim.toString());
    }

    /**
     * Normalizes E.164 phone (+917057257037) to the 10-digit format (7057257037)
     * used by the rest of the system (login, existing registration).
     */
    private String normalizeMobile(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("91") && digits.length() == 12) {
            digits = digits.substring(2);
        }
        return digits;
    }

    private LoginResponse generateTokensForUser(User user) {
        org.springframework.security.core.userdetails.UserDetails ud =
                new org.springframework.security.core.userdetails.User(
                        user.getMobile(), user.getPasswordHash(), java.util.Collections.emptyList());
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
