package com.civicsense.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class Phase12MdcTaskDecoratorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void decoratorCopiesCorrelationIntoWorkerAndLeavesCallerContextIntact() throws Exception {
        MDC.put(CorrelationId.MDC_KEY, "11111111-1111-4111-8111-111111111111");
        AtomicReference<String> workerValue = new AtomicReference<>();

        Runnable decorated = new MdcTaskDecorator().decorate(() ->
                workerValue.set(MDC.get(CorrelationId.MDC_KEY)));
        Thread worker = new Thread(decorated);
        worker.start();
        worker.join();

        assertThat(workerValue.get()).isEqualTo("11111111-1111-4111-8111-111111111111");
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isEqualTo("11111111-1111-4111-8111-111111111111");
    }
}
