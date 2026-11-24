package com.civicsense.routing;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Keeps routing failures isolated from complaint submission and AI persistence. */
@Component
public class RoutingListener {

    private final RoutingEngine routingEngine;

    public RoutingListener(RoutingEngine routingEngine) {
        this.routingEngine = routingEngine;
    }

    @Async("aiTaskExecutor")
    @TransactionalEventListener
    public void onRoutingRequested(RoutingRequestedEvent event) {
        routingEngine.routeAfterCommit(event.complaintId());
    }
}
