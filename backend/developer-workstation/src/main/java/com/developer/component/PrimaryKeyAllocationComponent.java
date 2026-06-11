package com.developer.component;

import com.developer.dto.AllocatePrimaryKeyRequest;
import com.developer.dto.AllocatePrimaryKeyResponse;

public interface PrimaryKeyAllocationComponent {

    AllocatePrimaryKeyResponse allocate(AllocatePrimaryKeyRequest request, Long functionUnitId);
}
