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
        List.of(
                new PurchaseEvent("evt_101", DEMO_JOURNEY_ID, Instant.parse("2026-10-20T09:00:00Z"),
                        "cart_created", "simulated_demo", new BigDecimal("179.00"), "USD",
                        "Agent selected shoes; shopper budget is $200. The limit is demo data."),
                new PurchaseEvent("evt_102", DEMO_JOURNEY_ID, Instant.parse("2026-10-20T09:02:00Z"),
                        "shipping_updated", "simulated_demo", new BigDecimal("209.00"), "USD",
                        "Shipping update increased the total above the stated budget." )
        ).forEach(eventStore::save);
    }
}
