package projiq.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projiq.auth.dto.*;
import projiq.auth.entity.AuthToken;
import projiq.auth.entity.AuthTokenType;
import projiq.auth.entity.RefreshToken;
import projiq.auth.repository.AuthTokenRepository;
import projiq.auth.repository.RefreshTokenRepository;
import projiq.auth.util.TokenHashUtils;
import projiq.common.exception.BadRequestException;
import projiq.common.exception.ConflictException;
import projiq.common.exception.ResourceNotFoundException;
import projiq.common.exception.UnauthorizedException;
import projiq.common.security.JwtTokenProvider;
import projiq.common.security.RateLimitingService;
import projiq.user.dto.UserSummaryDTO;
import projiq.user.entity.User;
import projiq.user.repository.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final RateLimitingService rateLimitingService;

    @Value("${app.jwt.refresh-token-expiration-ms:604800000}")
    private long refreshTokenExpirationMs;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       AuthTokenRepository authTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       EmailService emailService,
                       RateLimitingService rateLimitingService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.authTokenRepository = authTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.emailService = emailService;
        this.rateLimitingService = rateLimitingService;
    }

    @Transactional
    public UserSummaryDTO register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("An account with email " + normalizedEmail + " already exists.");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());
        User user = new User(normalizedEmail, passwordHash, request.getFullName().trim());
        user = userRepository.save(user);

        // Generate email verification token (24 hours)
        String rawToken = TokenHashUtils.generateSecureToken(32);
        String tokenHash = TokenHashUtils.hashToken(rawToken);
        Instant expiresAt = Instant.now().plus(Duration.ofHours(24));

        AuthToken authToken = new AuthToken(user, AuthTokenType.EMAIL_VERIFICATION, tokenHash, expiresAt);
        authTokenRepository.save(authToken);

        emailService.sendEmailVerification(user, rawToken);

        return UserSummaryDTO.from(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        // Rate limiting: max 10 login attempts per minute per IP + account
        rateLimitingService.checkRateLimit("login:" + clientIp + ":" + normalizedEmail, 10, Duration.ofMinutes(1), "login");

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        if (!user.isActive()) {
            throw new UnauthorizedException("Your account is deactivated. Please contact support.");
        }

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String rawRefreshToken = TokenHashUtils.generateSecureToken(64);
        String refreshTokenHash = TokenHashUtils.hashToken(rawRefreshToken);
        Instant expiresAt = Instant.now().plusMillis(refreshTokenExpirationMs);

        String deviceInfo = httpRequest != null ? httpRequest.getHeader("User-Agent") : "Unknown";
        RefreshToken refreshToken = new RefreshToken(user, refreshTokenHash, expiresAt, deviceInfo);
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(
                accessToken,
                rawRefreshToken,
                jwtTokenProvider.getAccessTokenExpirationMs(),
                UserSummaryDTO.from(user)
        );
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request, HttpServletRequest httpRequest) {
        String rawRefreshToken = request.getRefreshToken();
        String tokenHash = TokenHashUtils.hashToken(rawRefreshToken);

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        User user = storedToken.getUser();

        // Check for token reuse (revoked token being reused)
        if (storedToken.isRevoked()) {
            if (storedToken.getReplacedByTokenHash() != null) {
                log.warn("Detected refresh token reuse for user {}. Revoking all sessions for security.", user.getEmail());
                refreshTokenRepository.revokeAllUserTokens(user);
            }
            throw new UnauthorizedException("Refresh token is revoked");
        }

        if (storedToken.isExpired()) {
            throw new UnauthorizedException("Refresh token has expired. Please login again.");
        }

        if (!user.isActive()) {
            throw new UnauthorizedException("Account is deactivated");
        }

        // Token rotation: revoke current token and issue new one
        String newRawRefreshToken = TokenHashUtils.generateSecureToken(64);
        String newRefreshTokenHash = TokenHashUtils.hashToken(newRawRefreshToken);

        storedToken.setRevoked(true);
        storedToken.setReplacedByTokenHash(newRefreshTokenHash);
        refreshTokenRepository.save(storedToken);

        Instant newExpiresAt = Instant.now().plusMillis(refreshTokenExpirationMs);
        String deviceInfo = httpRequest != null ? httpRequest.getHeader("User-Agent") : storedToken.getDeviceInfo();
        RefreshToken newRefreshToken = new RefreshToken(user, newRefreshTokenHash, newExpiresAt, deviceInfo);
        refreshTokenRepository.save(newRefreshToken);

        String newAccessToken = jwtTokenProvider.generateAccessToken(user);

        return new AuthResponse(
                newAccessToken,
                newRawRefreshToken,
                jwtTokenProvider.getAccessTokenExpirationMs(),
                UserSummaryDTO.from(user)
        );
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            String tokenHash = TokenHashUtils.hashToken(request.getRefreshToken().trim());
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
            });
        }
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        // Rate limit password reset requests per IP + account
        rateLimitingService.checkRateLimit("forgot:" + clientIp + ":" + normalizedEmail, 5, Duration.ofMinutes(15), "password reset");

        userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(user -> {
            if (user.isActive()) {
                // Invalidate existing active reset tokens
                authTokenRepository.invalidateExistingTokens(user, AuthTokenType.PASSWORD_RESET);

                String rawToken = TokenHashUtils.generateSecureToken(32);
                String tokenHash = TokenHashUtils.hashToken(rawToken);
                Instant expiresAt = Instant.now().plus(Duration.ofHours(1));

                AuthToken token = new AuthToken(user, AuthTokenType.PASSWORD_RESET, tokenHash, expiresAt);
                authTokenRepository.save(token);

                emailService.sendPasswordReset(user, rawToken);
            }
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = TokenHashUtils.hashToken(request.getToken().trim());

        AuthToken authToken = authTokenRepository.findByTokenHashAndTokenType(tokenHash, AuthTokenType.PASSWORD_RESET)
                .orElseThrow(() -> new BadRequestException("Invalid or expired password reset link"));

        if (!authToken.isValid()) {
            throw new BadRequestException("Password reset link has already been used or expired");
        }

        User user = authToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        authToken.setUsed(true);
        authTokenRepository.save(authToken);

        // Invalidate all active user sessions / refresh tokens
        refreshTokenRepository.revokeAllUserTokens(user);
    }

    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        String tokenHash = TokenHashUtils.hashToken(request.getToken().trim());

        AuthToken authToken = authTokenRepository.findByTokenHashAndTokenType(tokenHash, AuthTokenType.EMAIL_VERIFICATION)
                .orElseThrow(() -> new BadRequestException("Invalid or expired verification link"));

        if (!authToken.isValid()) {
            throw new BadRequestException("Verification link has already been used or expired");
        }

        User user = authToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        authToken.setUsed(true);
        authTokenRepository.save(authToken);
    }

    @Transactional(readOnly = true)
    public UserSummaryDTO getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return UserSummaryDTO.from(user);
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
