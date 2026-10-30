package com.platform.common.assignment;

import java.util.List;

/** Engine-owned narrow call-output effect with its original Portal content expectations. */
public record AssignmentRelatedVariablePatch(String instanceId,String definitionId,List<AssignmentVariablePatch.Entry> variablePatch) {
    public static final String EFFECT_KEY="relatedVariablePatches";
    public AssignmentRelatedVariablePatch {
        if (instanceId==null || definitionId==null || variablePatch==null) throw new IllegalArgumentException("Incomplete accepted call output effect");
        variablePatch=List.copyOf(variablePatch);
    }
}
