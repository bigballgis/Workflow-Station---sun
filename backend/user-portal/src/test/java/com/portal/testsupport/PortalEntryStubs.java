package com.portal.testsupport;

import com.platform.security.service.PortalEntryService;
import com.portal.component.EffectiveDelegationRules;
import com.portal.repository.DelegationRuleRepository;

import java.util.Collection;
import java.util.LinkedHashSet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/** Test doubles for Portal entry (platform access model §7.1). */
public final class PortalEntryStubs {

    private PortalEntryStubs() {
    }

    /** Everyone asked about has Portal entry. */
    @SuppressWarnings("unchecked")
    public static PortalEntryService everyone() {
        PortalEntryService service = mock(PortalEntryService.class);
        lenient().when(service.withPortalEntry(any()))
                .thenAnswer(inv -> new LinkedHashSet<>((Collection<String>) inv.getArgument(0)));
        lenient().when(service.hasPortalEntry(any())).thenReturn(true);
        return service;
    }

    /** Delegation rules over {@code repository} with every party entitled, i.e. the repository's answer unchanged. */
    public static EffectiveDelegationRules delegationRules(DelegationRuleRepository repository) {
        return new EffectiveDelegationRules(repository, everyone());
    }
}
