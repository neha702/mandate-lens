package com.emeralds.mandatelens.agentpulse.event;

import com.emeralds.mandatelens.agentpulse.event.PurchaseEvent;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Clearly labelled demo journey for the first end-to-end API slice. */
@Component
public class DemoDataInitializer implements ApplicationRunner {
    public static final String DEMO_JOURNEY_ID = "demo-shoes-001";
    private final EventStore eventStore;

    public DemoDataInitializer(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!eventStore.findByJourneyId(DEMO_JOURNEY_ID).isEmpty()) return;
        // Keep the seeded demo events recent so the inactivity scheduler does not
        // expire this journey immediately after a fresh application start.
        Instant now = Instant.now();
        List.of(
                new PurchaseEvent("evt_101", DEMO_JOURNEY_ID, now.minusSeconds(120),
                        "cart_created", "simulated_demo", new BigDecimal("179.00"), "USD",
                        "Agent selected shoes; shopper budget is $600. The limit is demo data."),
                new PurchaseEvent("evt_102", DEMO_JOURNEY_ID, now.minusSeconds(60),
                        "shipping_updated", "simulated_demo", new BigDecimal("609.00"), "USD",
                        "Shipping update increased the total above the stated budget.")
        ).forEach(eventStore::save);
    }
}
