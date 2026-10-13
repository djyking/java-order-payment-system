package com.acme.order.common.mq;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitTopologyTest {

    private final RabbitTopology topology = new RabbitTopology();
    private final Declarables declarations = topology.orderBindings(topology.domainExchange());

    @Test
    void existingMainQueuesRetainTheirDurableDeclarationArguments() {
        Map<String, String> existingRoutes = Map.of(
            RabbitTopology.PAYMENT_QUEUE, "dead.payment",
            RabbitTopology.CONFIRM_QUEUE, "dead.inventory",
            RabbitTopology.RELEASE_QUEUE, "dead.inventory",
            RabbitTopology.QUERY_QUEUE, "dead.query");
        existingRoutes.forEach((name, route) -> {
            Queue queue = queue(name);
            assertTrue(queue.isDurable());
            assertFalse(queue.isAutoDelete());
            assertFalse(queue.isExclusive());
            assertEquals(Map.of("x-dead-letter-exchange", RabbitTopology.EXCHANGE,
                "x-dead-letter-routing-key", route), queue.getArguments());
        });
    }

    @Test
    void eachDeadLetterRouteHasOneDurableDestinationIncludingSharedInventory() {
        Map<String, String> destinations = Map.of(
            "dead.payment", RabbitTopology.PAYMENT_DLQ,
            "dead.inventory", RabbitTopology.INVENTORY_DLQ,
            "dead.query", RabbitTopology.QUERY_DLQ,
            "dead.order.close", RabbitTopology.CLOSE_DLQ);
        destinations.forEach((route, name) -> {
            List<Binding> bindings = declarations.getDeclarablesByType(Binding.class).stream()
                .filter(binding -> binding.getRoutingKey().equals(route)).toList();
            assertEquals(1, bindings.size(), "死信不得无目的地或重复分发: " + route);
            assertEquals(name, bindings.get(0).getDestination());
            assertTrue(queue(name).isDurable());
            assertTrue(queue(name).getArguments().isEmpty(), "死信队列不应形成再次死信循环");
        });
    }

    private Queue queue(String name) {
        return declarations.getDeclarablesByType(Queue.class).stream()
            .filter(queue -> queue.getName().equals(name)).findFirst().orElseThrow();
    }
}
