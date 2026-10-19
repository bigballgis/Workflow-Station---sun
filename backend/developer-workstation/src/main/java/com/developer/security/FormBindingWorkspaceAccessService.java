package com.developer.security;

import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.FormTableBindingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves binding ownership from persisted metadata before workspace authorization. */
@Service
@RequiredArgsConstructor
public class FormBindingWorkspaceAccessService {
    private final FormTableBindingRepository bindingRepository;
    private final FunctionUnitWorkspaceAccessService workspaceAccessService;

    @Transactional(readOnly = true)
    public void assertCanAccess(Long formId, Long bindingId, WorkspaceAccessAction action) {
        var binding = bindingRepository.findByIdWithTable(bindingId)
                .orElseThrow(() -> new ResourceNotFoundException("FormTableBinding", bindingId));
        var form = binding.getForm();
        if (!formId.equals(form.getId())) {
            throw new ResourceNotFoundException("FormTableBinding", bindingId);
        }
        workspaceAccessService.assertCanAccess(form.getFunctionUnit().getId(), action);
    }
}
