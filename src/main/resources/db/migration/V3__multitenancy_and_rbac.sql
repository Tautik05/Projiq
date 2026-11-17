-- V3: Multi-tenancy and Dynamic RBAC Schema

-- 1. Organizations
CREATE TABLE IF NOT EXISTS organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_organizations_slug ON organizations(slug);
CREATE INDEX IF NOT EXISTS idx_organizations_created_by ON organizations(created_by_user_id);

-- 2. Permissions
CREATE TABLE IF NOT EXISTS permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed Initial Granular Permissions
INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'organization.read', 'View organization details and members'),
    (gen_random_uuid(), 'organization.update', 'Update organization details and settings'),
    (gen_random_uuid(), 'organization.delete', 'Delete organization workspace'),
    (gen_random_uuid(), 'member.invite', 'Invite new members to the organization'),
    (gen_random_uuid(), 'member.remove', 'Remove members from the organization'),
    (gen_random_uuid(), 'member.update_role', 'Change role of organization members'),
    (gen_random_uuid(), 'role.manage', 'Create and configure custom roles and permissions'),
    (gen_random_uuid(), 'project.create', 'Create new projects'),
    (gen_random_uuid(), 'project.read', 'View projects within organization'),
    (gen_random_uuid(), 'project.update', 'Update project details and settings'),
    (gen_random_uuid(), 'project.delete', 'Delete projects'),
    (gen_random_uuid(), 'task.create', 'Create tasks within projects'),
    (gen_random_uuid(), 'task.read', 'View tasks and activity'),
    (gen_random_uuid(), 'task.assign', 'Assign tasks to organization members'),
    (gen_random_uuid(), 'task.update', 'Update task status, priority, and details'),
    (gen_random_uuid(), 'task.delete', 'Delete tasks'),
    (gen_random_uuid(), 'audit.read', 'View audit logs for organizational activities')
ON CONFLICT (name) DO NOTHING;

-- 3. Roles (Scoped to organization or template)
CREATE TABLE IF NOT EXISTS roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    is_system_role BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_role_org_name UNIQUE (organization_id, name)
);

CREATE INDEX IF NOT EXISTS idx_roles_org ON roles(organization_id);

-- 4. Role Permissions
CREATE TABLE IF NOT EXISTS role_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_role_permission UNIQUE (role_id, permission_id)
);

CREATE INDEX IF NOT EXISTS idx_role_permissions_role ON role_permissions(role_id);
CREATE INDEX IF NOT EXISTS idx_role_permissions_perm ON role_permissions(permission_id);

-- 5. Organization Members
CREATE TABLE IF NOT EXISTS organization_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_org_member UNIQUE (organization_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_org_members_user ON organization_members(user_id);
CREATE INDEX IF NOT EXISTS idx_org_members_org ON organization_members(organization_id);

-- 6. Organization Invitations
CREATE TABLE IF NOT EXISTS organization_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    inviter_user_id UUID NOT NULL REFERENCES users(id),
    role_id UUID NOT NULL REFERENCES roles(id),
    email VARCHAR(255) NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_org_invitations_token ON organization_invitations(token_hash);
CREATE INDEX IF NOT EXISTS idx_org_invitations_org_email ON organization_invitations(organization_id, email);
