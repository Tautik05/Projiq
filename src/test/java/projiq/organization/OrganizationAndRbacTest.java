package projiq.organization;

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
import projiq.auth.dto.DevEmailLink;
import projiq.auth.dto.LoginRequest;
import projiq.auth.dto.RegisterRequest;
import projiq.auth.service.DevEmailService;
import projiq.membership.dto.AcceptInvitationRequest;
import projiq.membership.dto.InviteMemberRequest;
import projiq.membership.dto.UpdateMemberRoleRequest;
import projiq.organization.dto.CreateOrganizationRequest;
import projiq.organization.dto.UpdateOrganizationRequest;
import projiq.rbac.dto.CreateRoleRequest;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrganizationAndRbacTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DevEmailService devEmailService;

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
    @DisplayName("Should create organization with default roles and creator as Owner")
    void shouldCreateOrganizationWithDefaultRoles() throws Exception {
        UserAuth owner = createAndLoginUser("OrgCreator");

        CreateOrganizationRequest request = new CreateOrganizationRequest(
                "Acme Corp",
                "acme-" + UUID.randomUUID().toString().substring(0, 6),
                "Workspace for Acme Corp"
        );

        MvcResult result = mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Acme Corp"))
                .andExpect(jsonPath("$.data.roleName").value("Owner"))
                .andReturn();

        UUID orgId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText());

        // Check organization roles (should have Owner, Admin, Member, Viewer)
        mockMvc.perform(get("/api/organizations/" + orgId + "/roles")
                        .header("Authorization", "Bearer " + owner.token())
                        .header("X-Organization-Id", orgId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(4)));
    }

    @Test
    @DisplayName("Should prevent cross-tenant access when user is not a member of the organization")
    void shouldPreventCrossTenantAccess() throws Exception {
        UserAuth userA = createAndLoginUser("UserA");
        UserAuth userB = createAndLoginUser("UserB");

        // User A creates Org A
        CreateOrganizationRequest orgAReq = new CreateOrganizationRequest(
                "Org A", "org-a-" + UUID.randomUUID().toString().substring(0, 6), "Org A desc"
        );
        MvcResult orgARes = mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + userA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orgAReq)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID orgAId = UUID.fromString(objectMapper.readTree(orgARes.getResponse().getContentAsString())
                .path("data").path("id").asText());

        // User B attempts to access Org A's members endpoint with Org A's ID
        mockMvc.perform(get("/api/organizations/" + orgAId + "/members")
                        .header("Authorization", "Bearer " + userB.token())
                        .header("X-Organization-Id", orgAId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Should invite member, accept invitation, and enforce RBAC permissions")
    void shouldInviteAcceptAndEnforcePermissions() throws Exception {
        UserAuth owner = createAndLoginUser("OwnerUser");
        UserAuth invitee = createAndLoginUser("InvitedUser");

        // 1. Create Organization
        CreateOrganizationRequest orgReq = new CreateOrganizationRequest(
                "Team Workspace", "team-" + UUID.randomUUID().toString().substring(0, 6), "Team workspace"
        );
        MvcResult orgRes = mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orgReq)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID orgId = UUID.fromString(objectMapper.readTree(orgRes.getResponse().getContentAsString())
                .path("data").path("id").asText());

        // 2. Fetch roles to find "Member" role ID
        MvcResult rolesRes = mockMvc.perform(get("/api/organizations/" + orgId + "/roles")
                        .header("Authorization", "Bearer " + owner.token())
                        .header("X-Organization-Id", orgId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rolesData = objectMapper.readTree(rolesRes.getResponse().getContentAsString()).path("data");
        UUID memberRoleId = null;
        for (JsonNode roleNode : rolesData) {
            if ("Member".equals(roleNode.path("name").asText())) {
                memberRoleId = UUID.fromString(roleNode.path("id").asText());
                break;
            }
        }
        assertNotNull(memberRoleId, "Member role should exist in organization");

        // 3. Owner invites Invitee
        InviteMemberRequest inviteReq = new InviteMemberRequest(invitee.email(), memberRoleId);
        mockMvc.perform(post("/api/organizations/" + orgId + "/invitations")
                        .header("Authorization", "Bearer " + owner.token())
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inviteReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        // 4. Retrieve dev link token
        DevEmailLink link = devEmailService.getDevLinks().stream()
                .filter(l -> l.getRecipientEmail().equalsIgnoreCase(invitee.email()) && "INVITATION".equals(l.getType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Invitation link not found in dev emails"));

        // 5. Invitee accepts invitation
        AcceptInvitationRequest acceptReq = new AcceptInvitationRequest(link.getRawToken());
        mockMvc.perform(post("/api/invitations/accept")
                        .header("Authorization", "Bearer " + invitee.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(acceptReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleName").value("Member"));

        // 6. Invitee can now view members
        mockMvc.perform(get("/api/organizations/" + orgId + "/members")
                        .header("Authorization", "Bearer " + invitee.token())
                        .header("X-Organization-Id", orgId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));

        // 7. Invitee (Member) CANNOT update organization (lacks 'organization.update' permission)
        UpdateOrganizationRequest updateReq = new UpdateOrganizationRequest("Hacked Org Name", "desc");
        mockMvc.perform(patch("/api/organizations/" + orgId)
                        .header("Authorization", "Bearer " + invitee.token())
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        // 8. Owner CAN update organization
        UpdateOrganizationRequest validUpdate = new UpdateOrganizationRequest("Updated Workspace Name", "new desc");
        mockMvc.perform(patch("/api/organizations/" + orgId)
                        .header("Authorization", "Bearer " + owner.token())
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Workspace Name"));
    }

    @Test
    @DisplayName("Should create custom role, update member role, and prevent privilege escalation")
    void shouldCreateCustomRoleAndUpdateMember() throws Exception {
        UserAuth owner = createAndLoginUser("AdminOwner");
        UserAuth employee = createAndLoginUser("Employee");

        // 1. Create Organization
        CreateOrganizationRequest orgReq = new CreateOrganizationRequest(
                "Engineering Hub", "eng-" + UUID.randomUUID().toString().substring(0, 6), "Hub"
        );
        MvcResult orgRes = mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orgReq)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID orgId = UUID.fromString(objectMapper.readTree(orgRes.getResponse().getContentAsString())
                .path("data").path("id").asText());

        // 2. Create Custom Role "Project Lead" with specific permissions
        CreateRoleRequest customRoleReq = new CreateRoleRequest(
                "Project Lead",
                "Can manage projects and tasks",
                Set.of("organization.read", "project.create", "project.read", "project.update", "task.create", "task.read")
        );
        MvcResult roleRes = mockMvc.perform(post("/api/organizations/" + orgId + "/roles")
                        .header("Authorization", "Bearer " + owner.token())
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customRoleReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Project Lead"))
                .andReturn();
        UUID customRoleId = UUID.fromString(objectMapper.readTree(roleRes.getResponse().getContentAsString())
                .path("data").path("id").asText());

        // 3. Invite employee with custom role
        InviteMemberRequest inviteReq = new InviteMemberRequest(employee.email(), customRoleId);
        mockMvc.perform(post("/api/organizations/" + orgId + "/invitations")
                        .header("Authorization", "Bearer " + owner.token())
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inviteReq)))
                .andExpect(status().isCreated());

        DevEmailLink link = devEmailService.getDevLinks().stream()
                .filter(l -> l.getRecipientEmail().equalsIgnoreCase(employee.email()) && "INVITATION".equals(l.getType()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/api/invitations/accept")
                        .header("Authorization", "Bearer " + employee.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AcceptInvitationRequest(link.getRawToken()))))
                .andExpect(status().isOk());

        // 4. Privilege escalation test: Employee CANNOT modify Owner's role
        UpdateMemberRoleRequest demoteOwnerReq = new UpdateMemberRoleRequest(customRoleId);
        mockMvc.perform(patch("/api/organizations/" + orgId + "/members/" + owner.userId() + "/role")
                        .header("Authorization", "Bearer " + employee.token())
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(demoteOwnerReq)))
                .andExpect(status().isForbidden());

        // 5. Privilege escalation test: Demoting the ONLY Owner is rejected
        mockMvc.perform(patch("/api/organizations/" + orgId + "/members/" + owner.userId() + "/role")
                        .header("Authorization", "Bearer " + owner.token())
                        .header("X-Organization-Id", orgId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(demoteOwnerReq)))
                .andExpect(status().isForbidden()); // Owner cannot change own role / demote last owner
    }
}
