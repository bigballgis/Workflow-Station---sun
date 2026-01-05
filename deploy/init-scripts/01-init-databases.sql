-- =====================================================
-- Database Initialization Script
-- Creates separate databases for each service
-- =====================================================

-- Create databases for each service
CREATE DATABASE workflow_engine;
CREATE DATABASE admin_center;
CREATE DATABASE developer_workstation;
CREATE DATABASE user_portal;

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE workflow_engine TO platform;
GRANT ALL PRIVILEGES ON DATABASE admin_center TO platform;
GRANT ALL PRIVILEGES ON DATABASE developer_workstation TO platform;
GRANT ALL PRIVILEGES ON DATABASE user_portal TO platform;

-- Connect to workflow_engine and enable extensions
\c workflow_engine
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Connect to admin_center and enable extensions
\c admin_center
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Connect to developer_workstation and enable extensions
\c developer_workstation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Connect to user_portal and enable extensions
\c user_portal
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
