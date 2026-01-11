# Database Initialization Scripts

## Directory Structure

```
init-scripts/
├── 00-schema/              # Database schema and system data
│   ├── 01-schema.sql       # Table definitions
│   └── 02-system-data.sql  # Roles, permissions, dictionaries
├── 01-admin/               # Admin user initialization
│   └── 01-admin-user.sql   # System administrator account
├── 02-test-data/           # Test data (development/testing only)
│   ├── 01-organization.sql # Organization structure (Level 1-2)
│   ├── 02-organization-detail.sql # Organization (Level 3-4)
│   ├── 03-users.sql        # Test users
│   ├── 04-role-assignments.sql # Role assignments
│   └── 05-virtual-groups.sql # Virtual groups
├── 03-test-workflow/       # Test workflow data (placeholder)
│   └── README.md
└── 99-utilities/           # Utility scripts (manual execution)
    └── 01-cleanup-old-tables.sql
```

## Usage

### Development (Full Test Data)

Default docker-compose.yml loads all directories:

```bash
docker-compose up -d postgres
```

### Schema Only (Production-like)

Use for production environments where users are managed separately:

```bash
docker-compose -f docker-compose.yml -f docker-compose.schema-only.yml up -d postgres
```

### Schema + Admin User (SIT/UAT)

Use for SIT/UAT environments:

```bash
docker-compose -f docker-compose.yml -f docker-compose.with-admin.yml up -d postgres
```

## Test Accounts

| Username | Password | Role | Service |
|----------|----------|------|---------|
| admin | test123 | SYS_ADMIN | Admin Center |
| hr.manager | test123 | MANAGER | User Portal |
| corp.manager | test123 | MANAGER | User Portal |
| tech.director | test123 | TECH_DIRECTOR | Developer Workstation |
| core.lead | test123 | TEAM_LEADER | Developer Workstation |
| dev.john | test123 | DEVELOPER | Developer Workstation |

See `02-test-data/04-role-assignments.sql` for complete list.

## Notes

- Scripts execute in alphabetical order within each directory
- Use numeric prefixes (01-, 02-) to control execution order
- `99-utilities/` is NOT mounted by default - run manually when needed
- BCrypt password hash for `test123`: `$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH`
