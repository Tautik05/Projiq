package projiq.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import projiq.auth.dto.*;
import projiq.auth.service.DevEmailService;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private DevEmailService devEmailService;

    private String generateUniqueEmail() {
        return "user_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    @Test
    @DisplayName("Should successfully register a new user and generate verification token")
    void shouldRegisterNewUser() throws Exception {
        String email = generateUniqueEmail();
        RegisterRequest request = new RegisterRequest(email, "Password@123", "Test User");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.data.fullName").value("Test User"))
                .andExpect(jsonPath("$.data.emailVerified").value(false));

        // Verify that dev link was generated and recorded
        assertTrue(devEmailService.getDevLinks().stream()
                .anyMatch(link -> link.getRecipientEmail().equalsIgnoreCase(email)));
    }

    @Test
    @DisplayName("Should reject registration with duplicate email")
    void shouldRejectDuplicateEmail() throws Exception {
        String email = generateUniqueEmail();
        RegisterRequest request = new RegisterRequest(email, "Password@123", "User One");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second registration with same email
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("already exists")));
    }

    @Test
    @DisplayName("Should login successfully with valid credentials and return JWT and refresh token")
    void shouldLoginSuccessfully() throws Exception {
        String email = generateUniqueEmail();
        String password = "SecurePassword@123";
        RegisterRequest regRequest = new RegisterRequest(email, password, "Login Tester");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.email").value(email.toLowerCase()))
                .andReturn();

        JsonNode responseNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = responseNode.path("data").path("accessToken").asText();

        // Access /api/auth/me using the access token
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email.toLowerCase()));
    }

    @Test
    @DisplayName("Should reject login with wrong password")
    void shouldRejectInvalidCredentials() throws Exception {
        String email = generateUniqueEmail();
        RegisterRequest regRequest = new RegisterRequest(email, "CorrectPassword123", "Wrong Password Tester");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest(email, "WrongPassword");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Should rotate refresh token and issue new access token")
    void shouldRotateRefreshToken() throws Exception {
        String email = generateUniqueEmail();
        String password = "Password@123";
        RegisterRequest regRequest = new RegisterRequest(email, password, "Rotation Tester");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest(email, password);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginData = objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data");
        String firstRefreshToken = loginData.path("refreshToken").asText();

        // Use refresh token to get a new pair
        RefreshTokenRequest refreshReq = new RefreshTokenRequest(firstRefreshToken);
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andReturn();

        JsonNode refreshData = objectMapper.readTree(refreshResult.getResponse().getContentAsString()).path("data");
        String secondRefreshToken = refreshData.path("refreshToken").asText();

        assertNotEquals(firstRefreshToken, secondRefreshToken, "New refresh token must be rotated and different");

        // Attempting to reuse the first (revoked) refresh token should be rejected (reuse detection)
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should verify email using dev verification token")
    void shouldVerifyEmail() throws Exception {
        String email = generateUniqueEmail();
        RegisterRequest regRequest = new RegisterRequest(email, "Password@123", "Email Verifier");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        // Find the generated dev verification link
        DevEmailLink link = devEmailService.getDevLinks().stream()
                .filter(l -> l.getRecipientEmail().equalsIgnoreCase(email) && "EMAIL_VERIFICATION".equals(l.getType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Verification link was not found"));

        VerifyEmailRequest verifyRequest = new VerifyEmailRequest(link.getRawToken());
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Reusing the same token should fail
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should complete password reset workflow via dev token")
    void shouldResetPassword() throws Exception {
        String email = generateUniqueEmail();
        String oldPassword = "OldPassword@123";
        String newPassword = "NewPassword@456";

        RegisterRequest regRequest = new RegisterRequest(email, oldPassword, "Reset Tester");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        ForgotPasswordRequest forgotReq = new ForgotPasswordRequest(email);
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotReq)))
                .andExpect(status().isOk());

        DevEmailLink link = devEmailService.getDevLinks().stream()
                .filter(l -> l.getRecipientEmail().equalsIgnoreCase(email) && "PASSWORD_RESET".equals(l.getType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Reset password link not found"));

        ResetPasswordRequest resetReq = new ResetPasswordRequest(link.getRawToken(), newPassword);
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Old password should now fail
        LoginRequest oldLogin = new LoginRequest(email, oldPassword);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oldLogin)))
                .andExpect(status().isUnauthorized());

        // New password should succeed
        LoginRequest newLogin = new LoginRequest(email, newPassword);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
