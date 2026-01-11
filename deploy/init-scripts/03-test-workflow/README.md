# Test Workflow Data

This directory is reserved for test workflow data.

## Purpose

Place workflow-related test data here, such as:
- Sample process definitions
- Test process instances
- Sample form data
- Workflow test scenarios

## Files

Add SQL files with numeric prefixes for execution order:
- `01-process-definitions.sql` - Sample BPMN process definitions
- `02-process-instances.sql` - Test process instances
- `03-task-data.sql` - Sample task data

## Usage

These scripts are only loaded in test/development environments.
Do not include in production deployments.
