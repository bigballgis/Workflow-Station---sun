package com.portal.util;

import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One round-trip: which of these process instances are withdrawn.
 * Avoids N+1 {@code findById} per task on list hot paths.
 */
public final class WithdrawnProcessIds {

    private WithdrawnProcessIds() {
    }

    public static Set<String> of(ProcessInstanceRepository repository, Collection<String> processInstanceIds) {
        if (processInstanceIds == null || processInstanceIds.isEmpty()) {
            return Collections.emptySet();
        }
        return repository.findAllById(processInstanceIds).stream()
                .filter(pi -> "WITHDRAWN".equals(pi.getStatus()))
                .map(ProcessInstance::getId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
    }
}
