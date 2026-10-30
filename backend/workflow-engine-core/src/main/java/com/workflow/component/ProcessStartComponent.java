package com.workflow.component;

import com.workflow.dto.request.StartProcessRequest;
import com.workflow.dto.response.ProcessInstanceResult;

public interface ProcessStartComponent {
    ProcessInstanceResult startProcess(StartProcessRequest request);
}
