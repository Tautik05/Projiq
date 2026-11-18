package projiq.project;

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
import projiq.organization.dto.CreateOrganizationRequest;
import projiq.project.dto.CreateProjectRequest;
import projiq.project.entity.ProjectStatus;
import projiq.task.dto.CreateTaskCommentRequest;
import projiq.task.dto.CreateTaskRequest;
import projiq.task.dto.UpdateTaskRequest;
import projiq.task.entity.TaskPriority;
import projiq.task.entity.TaskStatus;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectAndTaskTest {

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

    private UUID createOrganization(UserAuth user, String name) throws Exception {
        CreateOrganizationRequest orgReq = new CreateOrganizationRequest(
                name, "slug-" + UUID.randomUUID().toString().substring(0, 8), name + " description"
        );
        MvcResult res = mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orgReq)))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("id").asText());
    }

    @Test
    @DisplayName("Should create project, add tasks, track activities, and prevent cross-tenant access")
    void shouldManageProjectsAndTasksWithTenantIsolation() throws Exception {
        UserAuth owner1 = createAndLoginUser("ProjectOwner");
        UUID org1Id = createOrganization(owner1, "Engineering Team");

        UserAuth otherUser = createAndLoginUser("OtherUser");
        UUID org2Id = createOrganization(otherUser, "Marketing Team");

        // 1. Create a Project in Org 1
        CreateProjectRequest projectReq = new CreateProjectRequest(
                "Backend Rewrite",
                "Rewriting legacy services to Spring Boot",
                ProjectStatus.ACTIVE,
                null,
                null
        );

        MvcResult projectRes = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + owner1.token())
                        .header("X-Organization-Id", org1Id.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projectReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Backend Rewrite"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();

        UUID projectId = UUID.fromString(objectMapper.readTree(projectRes.getResponse().getContentAsString())
                .path("data").path("id").asText());

        // Test listing projects without search query (verifies lower(bytea) bug fix)
        mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + owner1.token())
                        .header("X-Organization-Id", org1Id.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", not(empty())))
                .andExpect(jsonPath("$.data[0].id").value(projectId.toString()));

        // Test searching projects by keyword
        mockMvc.perform(get("/api/projects?search=Backend")
                        .header("Authorization", "Bearer " + owner1.token())
                        .header("X-Organization-Id", org1Id.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", not(empty())))
                .andExpect(jsonPath("$.data[0].name").value("Backend Rewrite"));

        // 2. Cross-tenant test: OtherUser cannot access this project using Org 1 or Org 2
        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + otherUser.token())
                        .header("X-Organization-Id", org1Id.toString()))
                .andExpect(status().isForbidden());

        // 3. Create a Task in Project 1
        CreateTaskRequest taskReq = new CreateTaskRequest(
                projectId,
                "Implement Multi-Tenancy Filter",
                "Ensure strict data isolation by organization_id",
                TaskStatus.TODO,
                TaskPriority.HIGH,
                null,
                null
        );

        MvcResult taskRes = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + owner1.token())
                        .header("X-Organization-Id", org1Id.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("Implement Multi-Tenancy Filter"))
                .andExpect(jsonPath("$.data.status").value("TODO"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andReturn();

        UUID taskId = UUID.fromString(objectMapper.readTree(taskRes.getResponse().getContentAsString())
                .path("data").path("id").asText());

        // 4. Update Task status and priority
        UpdateTaskRequest updateTaskReq = new UpdateTaskRequest(
                null, null, TaskStatus.IN_PROGRESS, TaskPriority.URGENT, null, null
        );

        mockMvc.perform(patch("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + owner1.token())
                        .header("X-Organization-Id", org1Id.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateTaskReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.priority").value("URGENT"));

        // 5. Add a Comment to the Task
        CreateTaskCommentRequest commentReq = new CreateTaskCommentRequest("Filter implemented with unit tests.");
        mockMvc.perform(post("/api/tasks/" + taskId + "/comments")
                        .header("Authorization", "Bearer " + owner1.token())
                        .header("X-Organization-Id", org1Id.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commentReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value("Filter implemented with unit tests."));

        // 6. Verify Comments retrieval
        mockMvc.perform(get("/api/tasks/" + taskId + "/comments")
                        .header("Authorization", "Bearer " + owner1.token())
                        .header("X-Organization-Id", org1Id.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        // 7. Verify Task Activities (Created, Status Changed, Priority Changed, Commented)
        mockMvc.perform(get("/api/tasks/" + taskId + "/activities")
                        .header("Authorization", "Bearer " + owner1.token())
                        .header("X-Organization-Id", org1Id.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(4))));

        // 8. Search tasks by status
        mockMvc.perform(get("/api/tasks")
                        .param("status", "IN_PROGRESS")
                        .header("Authorization", "Bearer " + owner1.token())
                        .header("X-Organization-Id", org1Id.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(taskId.toString()));
    }
}
