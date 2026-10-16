package com.portal.recordnote;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Workers use transactional SKIP LOCKED ownership; there is no unrecoverable SENDING state. */
@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class RecordNoteDeliveryScheduler {
    private final RecordNoteSubmissionRepository submissions;
    private final RecordNoteDeliveryComponent delivery;

    @org.springframework.beans.factory.annotation.Value("${record-note.delivery-paused:false}")
    private boolean paused;

    @Scheduled(fixedDelay=5000)
    public void dispatch() {
        if(paused) return;
        for(String id:submissions.due()) {
            try { delivery.deliver(id); }
            catch(RuntimeException e) {
                // Retry state is durable and explicit; exception content may contain business data.
                submissions.retry(id);
                log.warn("Record Note delivery retry required: submission={}, failureType={}",id,e.getClass().getSimpleName());
            }
        }
    }
}
