package com.acme.order.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;

class PaymentProviderConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withUserConfiguration(PaymentProviders.AdapterConfiguration.class);

    @Test void mockIsNotRegisteredByDefault() {
        context.run(app -> {
            assertNull(app.getStartupFailure());
            assertEquals(2, app.getBeansOfType(PaymentProvider.class).size());
            assertTrue(app.getBeansOfType(PaymentProvider.class).values().stream()
                .allMatch(provider -> !provider.method().available()));
        });
    }

    @Test void mockRequiresExplicitTrueAndDoesNotEnableRealProviders() {
        context.withPropertyValues("payment.mock-enabled=true").run(app -> {
            var adapters = app.getBeansOfType(PaymentProvider.class).values();
            assertEquals(3, adapters.size());
            assertEquals(java.util.List.of("MOCK"), adapters.stream().filter(p -> p.method().available())
                .map(p -> p.method().code()).toList());
        });
    }
}
