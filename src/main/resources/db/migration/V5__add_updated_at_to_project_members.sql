-- V5: Add updated_at to project_members to match BaseEntity

ALTER TABLE project_members ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
