package com.portal.util;

import com.portal.component.ProcessTerminalStatusResolver;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One round-trip: which of these process instances have ended.
 * Avoids N+1 {@code findById} per task on list hot paths.
 *
 * <p>The question is "may a task on this instance still be acted on", so every terminal status
 * counts — not only WITHDRAWN. A rejected instance, or one terminated because the Function Unit
 * that called it was withdrawn, leaves tasks that nobody can complete; showing them in a to-do
 * list only produces failures at submit time.
 */
public final class WithdrawnProcessIds {

    private WithdrawnProcessIds() {
    }

    public static Set<String> of(ProcessInstanceRepository repository, Collection<String> processInstanceIds) {
        if (processInstanceIds == null || processInstanceIds.isEmpty()) {
            return Collections.emptySet();
        }
        return repository.findAllById(processInstanceIds).stream()
                .filter(pi -> ProcessTerminalStatusResolver.isTerminal(pi.getStatus()))
                .map(ProcessInstance::getId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
    }
}
