package com.portal.component;

import com.portal.dto.TaskCompleteRequest;
import com.portal.dto.TaskInfo;
import java.util.List;
import java.util.Map;

/** Server-only prepared input, before any workflow completion or Portal business-row write. */
record TaskApprovalPreparation(TaskInfo task,TaskCompleteRequest request,String actor,
        Map<String,Object> variables,Map<String,Object> engineVariables,Map<String,Object> submitted,
        boolean submittedSubTables,Map<String,Object> writeContext,List<SubTableBatchInitialMiOutcome.Expected> expected,
        String definitionId,String representedUser) { }
