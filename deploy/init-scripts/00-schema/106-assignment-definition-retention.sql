-- Engine-owned derived dependency index. Rows commit/roll back with their compiled deployment.
CREATE TABLE IF NOT EXISTS we_assignment_definition_dependencies (
    owner_deployment_id VARCHAR(255) NOT NULL,
    referenced_definition_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (owner_deployment_id, referenced_definition_id)
);
CREATE INDEX IF NOT EXISTS idx_assignment_referenced_definition
    ON we_assignment_definition_dependencies(referenced_definition_id);
