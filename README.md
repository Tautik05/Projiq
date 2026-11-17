# Projiq – Multi-Tenant Enterprise Workspace & Project Management SaaS

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/Neon%20PostgreSQL-18.7-blue.svg)](https://neon.tech/)
[![Redis](https://img.shields.io/badge/Upstash%20Redis-SSL-red.svg)](https://upstash.com/)
[![Flyway](https://img.shields.io/badge/Flyway-Migrations%20V1--V6-coral.svg)](https://flywaydb.org/)
[![OpenAPI](https://img.shields.io/badge/OpenAPI%203.1-Swagger%20UI-green.svg)](http://localhost:8080/swagger-ui/index.html)

**Projiq** is a production-grade, multi-tenant B2B enterprise SaaS application that enables multiple organizations to manage their teams, projects, tasks, dynamic roles, and fine-grained permissions through strictly isolated workspaces.

Built as a **modular monolith** in Spring Boot with Java 21, it achieves enterprise multi-tenancy on a shared PostgreSQL database while enforcing strict tenant boundaries, dynamic database-driven RBAC with Redis caching, comprehensive audit trails, and zero external email service dependencies.

---

## Architecture Overview

```mermaid
graph TD
    Client["Web Client (Tailwind + Vanilla JS)"]
    
    subgraph "Spring Boot Modular Monolith"
        Security["Spring Security + JWT Filter"]
        TenantGuard["TenantInterceptor (X-Organization-Id Validation)"]
        
        subgraph "Application Modules"
            AuthMod["auth\n(Tokens, Passwords, DevMail)"]
            OrgMod["organization\n(Workspaces)"]
            RbacMod["rbac\n(Dynamic Roles & Perms)"]
            MemberMod["membership\n(Team & Invitations)"]
            ProjMod["project\n(Projects)"]
            TaskMod["task\n(Kanban, Comments, Timeline)"]
            AuditMod["audit\n(Append-Only Log)"]
            NotifMod["notification\n(In-App Alerts)"]
        end
        
        Aspect["@RequirePermission Aspect (AOP)"]
    end
    
    subgraph "Infrastructure & Storage"
        Postgres[("Neon PostgreSQL\n(Shared Tables + organization_id)")]
        Redis[("Upstash Redis\n(RBAC Cache + Rate Limiting)")]
    end
    
    Client --> Security
    Security --> TenantGuard
    TenantGuard --> Aspect
    Aspect --> Application Modules
    
    RbacMod <--> Redis
    AuthMod <--> Redis
    Application Modules --> Postgres
```

### Module Structure

The backend is engineered as a clean modular monolith located under `projiq/`:

| Module | Responsibility | Key Components |
|---|---|---|
| `projiq.auth` | Identity, JWT rotation, BCrypt(12), rate limiting, and Dev Email simulator | `AuthController`, `AuthService`, `RateLimitingService`, `DevEmailService` |
| `projiq.user` | User account models and JPA repositories | `User`, `UserRepository` |
| `projiq.organization` | Multi-tenant organization workspaces | `OrganizationController`, `OrganizationService`, `Organization` |
| `projiq.membership` | Workspace members, invitations, and role assignments | `MembershipController`, `MembershipService`, `OrganizationMember`, `OrganizationInvitation` |
| `projiq.rbac` | Dynamic RBAC, role inheritance, and permission caching | `RoleController`, `RbacService`, `RequirePermissionAspect`, `Role`, `Permission` |
| `projiq.project` | Tenant-scoped project tracking and status workflows | `ProjectController`, `ProjectService`, `Project`, `ProjectMember` |
| `projiq.task` | Kanban task management, activity logging, and comments | `TaskController`, `TaskService`, `Task`, `TaskComment`, `TaskActivity` |
| `projiq.audit` | Append-only tamper-resistant organizational audit logging | `AuditController`, `AuditService`, `AuditLog` |
| `projiq.notification` | Real-time in-app user notifications and unread badges | `NotificationController`, `NotificationService`, `Notification` |
| `projiq.common` | Security config, JWT filter, TenantContext `ThreadLocal`, Redis config, OpenAPI, and GlobalExceptionHandler | `SecurityConfig`, `TenantContext`, `TenantInterceptor`, `GlobalExceptionHandler` |

---

## Multi-Tenancy & Tenant Isolation

1. **Shared Database & Shared Tables**:
   - All tenant-owned tables (`projects`, `tasks`, `organization_members`, `roles`, `audit_logs`, `notifications`) include an `organization_id` foreign key with compound indexes (`organization_id`, `created_at`, etc.).
2. **Context-Aware Request Pipeline**:
   - The frontend passes the active tenant ID in the `X-Organization-Id` HTTP header.
   - `TenantInterceptor` intercepts requests: validates that the authenticated user is an active member of that organization.
   - Sets the validated tenant UUID into `TenantContext` (`ThreadLocal<UUID>`).
   - Requests with missing or forged tenant headers are rejected before reaching controller business logic.
3. **Defense-in-Depth Repository Isolation**:
   - Service queries and repository operations explicitly scope queries to `organization_id` (e.g. `findByOrganizationIdAndId`), preventing any cross-tenant data leakage.

---

## Dynamic Role-Based Access Control (RBAC)

- **Database-Driven Roles**:
  - Predefined system roles: `Organization Owner`, `Organization Admin`, `Member`, `Viewer`.
  - Custom roles: Workspace administrators can create custom roles with any subset of granular permissions.
- **Granular Permissions**:
  - `organization.read`, `organization.update`
  - `member.invite`, `member.remove`, `member.update_role`
  - `role.manage`
  - `project.create`, `project.read`, `project.update`, `project.delete`
  - `task.create`, `task.read`, `task.assign`, `task.update`, `task.delete`
  - `audit.read`
- **Method-Level Security**:
  - Controllers and services enforce `@RequirePermission("permission.name")` checked via Spring AOP (`RequirePermissionAspect`).
- **High-Performance Redis Caching**:
  - User permissions for an organization are cached in Redis (`rbac:perms:{orgId}:{userId}`) with a 15-minute TTL.
  - Caches are automatically invalidated when a member's role changes or a role's permissions are modified.
- **Privilege Escalation Protection**:
  - Only Organization Owners can invite or assign the `Organization Owner` role.
  - The last remaining Owner of an organization cannot be demoted or removed.

---

## Zero-Cost Email Delivery Simulator

For local and portfolio development without purchasing a domain or paying for an email provider (such as Resend or SendGrid):
- When a user registers, requests a password reset, or is invited to an organization:
  - Secure tokens are generated and stored only as SHA-256 hashes in PostgreSQL.
  - `DevEmailService` outputs formatted simulation logs to the console and queues the raw link in an in-memory dev inbox.
  - The frontend includes a floating **Dev Email Inbox** drawer that polls `/api/dev/tokens` and allows one-click action execution (`Open Action Link ↗` or `Copy Token`).
- Dedicated landing pages handle action links:
  - [verify-email.html](file:///d:/Projects/Projiq/projiq/src/main/resources/static/verify-email.html): Confirms email verification tokens.
  - [reset-password.html](file:///d:/Projects/Projiq/projiq/src/main/resources/static/reset-password.html): Validates reset tokens and updates passwords.
  - [accept-invitation.html](file:///d:/Projects/Projiq/projiq/src/main/resources/static/accept-invitation.html): Accepts organization invitations for new or returning users.
- Production safety: `/api/dev/tokens` is guarded by `@Profile("dev")` and disabled in production.

---

## Database Migrations (Flyway)

Migrations are automatically applied on startup from `src/main/resources/db/migration`:

- **V1**: Core setup (`pgcrypto` extension).
- **V2**: Authentication & users (`users`, `refresh_tokens`, `auth_tokens`).
- **V3**: Multi-tenancy & RBAC (`organizations`, `permissions`, `roles`, `role_permissions`, `organization_members`, `organization_invitations`, default roles & seed permissions).
- **V4**: Projects & tasks (`projects`, `project_members`, `tasks`, `task_comments`, `task_activities`).
- **V5**: Added `updated_at` to `project_members`.
- **V6**: Audit logging & notifications (`audit_logs`, `notifications`).

---

## Technology Stack

- **Java Version**: 21 LTS
- **Framework**: Spring Boot 4.1.2 (Spring Framework 7.0.10)
- **Security**: Spring Security 7, JJWT (`io.jsonwebtoken:jjwt-api:0.12.6`), BCrypt (strength 12)
- **Database**: Neon Hosted PostgreSQL 18.7 (Connection pooling via HikariCP + PgBouncer)
- **Cache & Rate Limiting**: Upstash Hosted Redis (SSL Lettuce client)
- **Migrations**: Flyway Community 11.20
- **API Documentation**: Springdoc OpenAPI Starter WebMvc UI 3.2.0
- **Frontend**: HTML5, Vanilla JavaScript ES6+, Tailwind CSS (Modern Single Page Application)

---

## Getting Started

### 1. Prerequisites
- Java 21 JDK installed (`java -version`)
- Access to Neon PostgreSQL and Upstash Redis (configured in `.env`)

### 2. Configuration (`.env`)
Create a `.env` file in the project root (see [.env.example](file:///d:/Projects/Projiq/projiq/.env.example)):

```ini
# Neon PostgreSQL
SPRING_DATASOURCE_URL=jdbc:postgresql://<neon-host>:5432/<db>?sslmode=require
SPRING_DATASOURCE_USERNAME=<username>
SPRING_DATASOURCE_PASSWORD=<password>

# Upstash Redis (SSL)
SPRING_DATA_REDIS_URL=rediss://default:<password>@<upstash-host>:6379

# JWT Security
JWT_SECRET=<min-64-character-hex-or-base64-secret>
JWT_ACCESS_EXPIRATION_MS=900000
JWT_REFRESH_EXPIRATION_MS=604800000

# Server
SERVER_PORT=8080
```

### 3. Run the Application
```bash
./mvnw.cmd spring-boot:run
```

Once running:
- **Application Web UI**: [http://localhost:8080/](http://localhost:8080/)
- **Swagger UI / OpenAPI Documentation**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **OpenAPI 3.1 JSON Specification**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### 4. Run the Full Test Suite
```bash
./mvnw.cmd test
```
Runs all 23 unit and integration tests across authentication, tenant isolation, dynamic RBAC, project management, task activity tracking, audit logging, and static asset serving against live Neon and Upstash instances.

---

## License
MIT License. Created for portfolio demonstration and enterprise multi-tenant architecture reference.
