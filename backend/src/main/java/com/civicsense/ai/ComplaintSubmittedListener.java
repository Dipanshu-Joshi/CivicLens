package com.civicsense.ai;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Never runs inside the submit transaction: AI failure cannot lose a complaint. */
@Component
public class ComplaintSubmittedListener {

    private final AiAnalysisService service;

    public ComplaintSubmittedListener(AiAnalysisService service) {
        this.service = service;
    }

    @Async("aiTaskExecutor")
    @TransactionalEventListener
    public void onComplaintSubmitted(ComplaintSubmittedEvent event) {
        service.process(event.complaintId());
    }
}
