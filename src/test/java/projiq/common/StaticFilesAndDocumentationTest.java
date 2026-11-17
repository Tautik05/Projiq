package projiq.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class StaticFilesAndDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should serve index.html anonymously")
    void shouldServeIndexHtml() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should serve verify-email.html anonymously")
    void shouldServeVerifyEmailHtml() throws Exception {
        mockMvc.perform(get("/verify-email.html"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should serve reset-password.html anonymously")
    void shouldServeResetPasswordHtml() throws Exception {
        mockMvc.perform(get("/reset-password.html"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should serve accept-invitation.html anonymously")
    void shouldServeAcceptInvitationHtml() throws Exception {
        mockMvc.perform(get("/accept-invitation.html"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should serve js/app.js anonymously")
    void shouldServeAppJs() throws Exception {
        mockMvc.perform(get("/js/app.js"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should serve OpenAPI docs anonymously")
    void shouldServeOpenApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
