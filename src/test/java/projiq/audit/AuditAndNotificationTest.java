package projiq.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import projiq.auth.dto.LoginRequest;
import projiq.auth.dto.RegisterRequest;
import projiq.membership.dto.InviteMemberRequest;
import projiq.organization.dto.CreateOrganizationRequest;
import projiq.project.dto.CreateProjectRequest;
import projiq.task.dto.CreateTaskRequest;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuditAndNotificationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private record UserAuth(String email, String token, UUID userId) {}

    private UserAuth createAndLoginUser(String name) throws Exception {
        String email = name.toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        RegisterRequest reg = new RegisterRequest(email, "Password@123", name);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest(email, "Password@123");
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(res.getResponse().getContentAsString()).path("data");
        String token = data.path("accessToken").asText();
        UUID userId = UUID.fromString(data.path("user").path("id").asText());
        return new UserAuth(email, token, userId);
    }

    @Test
    @DisplayName("Should generate append-only audit logs and support filtered retrieval")
    void shouldGenerateAndRetrieveAuditLogs() throws Exception {
        UserAuth owner = createAndLoginUser("AuditOwner");

        // 1. Create Organization (triggers ORGANIZATION_CREATED audit)
        CreateOrganizationRequest orgReq = new CreateOrganizationRequest(
                "Audit Org", "audit-" + UUID.randomUUID().toString().substring(0, 8), "Audit test org"
        );
        MvcResult orgRes = mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orgReq)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID orgId = UUID.fromString(objectMapper.readTree(orgRes.getResponse().getContentAsString())
                .path("data").path("id").asText());

        // 2. Create Project
        CreateProjectRequest projectReq = new CreateProjectRequest("Project Alpha", "Desc", null, null, null);
        MvcResult projRes = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + owner.token())
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectReq)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID projId = UUID.fromString(objectMapper.readTree(projRes.getResponse().getContentAsString())
                .path("data").path("id").asText());

        // 3. Create Task (triggers TASK_CREATED audit)
        CreateTaskRequest taskReq = new CreateTaskRequest(projId, "Audit Task", "Details", null, null, null, null);
        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + owner.token())
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskReq)))
                .andExpect(status().isCreated());

        // 4. Query Audit Logs (requires 'audit.read', which Owner has)
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + owner.token())
                        .header("X-Organization-Id", orgId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.data.content[*].action", hasItems("ORGANIZATION_CREATED", "TASK_CREATED")));
    }

    @Test
    @DisplayName("Should generate and manage in-app notifications")
    void shouldGenerateAndManageInAppNotifications() throws Exception {
        UserAuth owner = createAndLoginUser("NotifOwner");
        UserAuth memberUser = createAndLoginUser("NotifMember");

        // 1. Create Organization
        CreateOrganizationRequest orgReq = new CreateOrganizationRequest(
                "Notif Workspace", "notif-" + UUID.randomUUID().toString().substring(0, 8), "Desc"
        );
        MvcResult orgRes = mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orgReq)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID orgId = UUID.fromString(objectMapper.readTree(orgRes.getResponse().getContentAsString())
                .path("data").path("id").asText());

        // Find Member role ID
        MvcResult rolesRes = mockMvc.perform(get("/api/organizations/" + orgId + "/roles")
                        .header("Authorization", "Bearer " + owner.token())
                        .header("X-Organization-Id", orgId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode roles = objectMapper.readTree(rolesRes.getResponse().getContentAsString()).path("data");
        UUID memberRoleId = null;
        for (JsonNode r : roles) {
            if ("Member".equals(r.path("name").asText())) {
                memberRoleId = UUID.fromString(r.path("id").asText());
                break;
            }
        }
        assertNotNull(memberRoleId);

        // 2. Owner invites memberUser (memberUser already exists -> creates in-app notification)
        InviteMemberRequest inviteReq = new InviteMemberRequest(memberUser.email(), memberRoleId);
        mockMvc.perform(post("/api/organizations/" + orgId + "/invitations")
                        .header("Authorization", "Bearer " + owner.token())
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inviteReq)))
                .andExpect(status().isCreated());

        // 3. Member checks unread notification count
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + memberUser.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount", greaterThanOrEqualTo(1)));

        // 4. Member lists notifications
        MvcResult notifListRes = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + memberUser.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.content[0].type").value("INVITATION_RECEIVED"))
                .andReturn();

        UUID notifId = UUID.fromString(objectMapper.readTree(notifListRes.getResponse().getContentAsString())
                .path("data").path("content").get(0).path("id").asText());

        // 5. Mark notification as read
        mockMvc.perform(patch("/api/notifications/" + notifId + "/read")
                        .header("Authorization", "Bearer " + memberUser.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));

        // 6. Mark all as read
        mockMvc.perform(post("/api/notifications/mark-all-read")
                        .header("Authorization", "Bearer " + memberUser.token()))
                .andExpect(status().isOk());

        // 7. Verify unread count is now 0
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + memberUser.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }
}
