# Requirements Document

## Introduction

本需求文档定义了采购审批流程中两个关键缺陷的修复要求。当部门经理拒绝采购申请时，系统存在以下问题：
1. 流程图错误地将所有节点标记为已完成（绿色），而不是仅高亮实际执行的路径
2. 采购明细子表单（purchase_items_form）的数据被意外清空

这些问题影响了用户对流程执行状态的理解，并导致重要业务数据丢失。

## Glossary

- **Process_Diagram**: 流程图组件，用于可视化显示BPMN工作流的执行状态
- **Completed_Node**: 已完成的流程节点，在流程图中以绿色高亮显示
- **Executed_Path**: 流程实例实际执行过的节点和连线序列
- **History_Service**: Flowable历史服务，提供流程执行历史数据的查询接口
- **Sub_Form**: 子表单，用于存储一对多关系的业务数据（如采购明细项）
- **Form_Data**: 表单数据，包括主表单和子表单的所有字段值
- **Process_Variables**: 流程变量，在流程执行过程中传递和存储的数据

## Requirements

### Requirement 1: 流程图高亮准确性

**User Story:** 作为流程审批人员，我希望流程图只高亮显示实际执行过的节点和路径，以便准确了解流程的真实执行状态。

#### Acceptance Criteria

1. WHEN a process instance is displayed THEN THE Process_Diagram SHALL highlight only nodes that have been executed according to History_Service
2. WHEN a department manager rejects a purchase request THEN THE Process_Diagram SHALL highlight the start node, department manager approval node, and rejection end node only
3. WHEN querying historical activity instances THEN THE System SHALL retrieve only activities with non-null end times
4. WHEN determining completed nodes THEN THE System SHALL use historical activity instance IDs rather than sequential position logic
5. WHEN a gateway is reached but not all outgoing paths are taken THEN THE Process_Diagram SHALL highlight only the gateway and the path that was actually taken

### Requirement 2: 子表单数据持久化

**User Story:** 作为采购申请人，我希望在流程审批过程中采购明细数据始终保留，以便在任何阶段都能查看完整的申请信息。

#### Acceptance Criteria

1. WHEN a task is completed with approval or rejection THEN THE System SHALL preserve all Sub_Form data in the database
2. WHEN loading task details THEN THE System SHALL retrieve and display all associated Sub_Form records
3. WHEN submitting task completion THEN THE System SHALL include Sub_Form data in Process_Variables
4. WHEN a process instance ends THEN THE System SHALL maintain Sub_Form data for historical reference
5. WHEN querying historical process data THEN THE System SHALL return complete Form_Data including Sub_Form entries

### Requirement 3: 历史数据查询优化

**User Story:** 作为系统开发人员，我希望历史数据查询能准确反映流程执行路径，以便为流程图高亮提供正确的数据源。

#### Acceptance Criteria

1. WHEN querying activity history for a process instance THEN THE History_Service SHALL return activities ordered by start time
2. WHEN an activity has been executed THEN THE System SHALL ensure the activity has both start time and end time recorded
3. WHEN multiple paths exist from a gateway THEN THE System SHALL identify which specific path was taken based on sequence flow execution
4. WHEN building the Executed_Path THEN THE System SHALL include all executed sequence flows between nodes
5. WHEN a process ends at a specific end event THEN THE System SHALL record which end event was reached

### Requirement 4: 前端流程图渲染逻辑

**User Story:** 作为前端开发人员，我希望流程图组件能根据历史数据正确渲染节点状态，以便用户看到准确的流程执行视图。

#### Acceptance Criteria

1. WHEN receiving completed node IDs from the backend THEN THE Process_Diagram SHALL apply completed styling only to those specific nodes
2. WHEN a node is in the completed list THEN THE Process_Diagram SHALL render it with green background and green border
3. WHEN a node is the current active node THEN THE Process_Diagram SHALL render it with orange background and orange border
4. WHEN a node has not been executed THEN THE Process_Diagram SHALL render it with default gray styling
5. WHEN rendering sequence flows THEN THE Process_Diagram SHALL highlight flows between completed nodes

### Requirement 5: 表单数据完整性验证

**User Story:** 作为质量保证人员，我希望系统能验证表单数据的完整性，以便及早发现数据丢失问题。

#### Acceptance Criteria

1. WHEN loading a task with Sub_Form data THEN THE System SHALL verify that all expected Sub_Form records are present
2. WHEN Sub_Form data is missing THEN THE System SHALL log a warning with the process instance ID and form name
3. WHEN completing a task THEN THE System SHALL validate that Sub_Form data exists before allowing submission
4. WHEN Form_Data is incomplete THEN THE System SHALL provide clear error messages to the user
5. WHEN historical data is queried THEN THE System SHALL include Sub_Form record counts in the response

### Requirement 6: 流程变量管理

**User Story:** 作为后端开发人员，我希望流程变量能正确存储和传递表单数据，以便在整个流程生命周期中保持数据一致性。

#### Acceptance Criteria

1. WHEN a task is submitted THEN THE System SHALL serialize all Form_Data including Sub_Form entries into Process_Variables
2. WHEN Process_Variables are stored THEN THE System SHALL use a format that preserves Sub_Form array structures
3. WHEN retrieving Process_Variables THEN THE System SHALL deserialize them back to the original Form_Data structure
4. WHEN a process instance is completed THEN THE System SHALL archive Process_Variables for historical access
5. WHEN Process_Variables exceed size limits THEN THE System SHALL store large data externally and maintain references

### Requirement 7: 错误处理和日志记录

**User Story:** 作为系统管理员，我希望系统能记录详细的错误日志，以便快速诊断和解决数据丢失或显示错误问题。

#### Acceptance Criteria

1. WHEN a Sub_Form data loss is detected THEN THE System SHALL log the process instance ID, task ID, and form name
2. WHEN historical activity query fails THEN THE System SHALL log the error and return a graceful error response
3. WHEN Process_Diagram rendering encounters invalid data THEN THE System SHALL log the issue and display a user-friendly error message
4. WHEN Form_Data serialization fails THEN THE System SHALL log the full error stack trace and notify administrators
5. WHEN debugging is enabled THEN THE System SHALL log all completed node IDs and executed paths for verification
