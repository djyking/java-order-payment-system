package com.acme.order.order;

import java.time.LocalDateTime;
import java.util.List;
import com.acme.order.api.product.ProductDtos.SkuView;
import com.acme.order.common.core.BizException;
import com.acme.order.common.observability.OrderMetrics;
import com.acme.order.common.redis.IdempotencyTokenService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OrderBusinessTest {
    RemoteClients remote;
    OrderRepository repo;
    IdempotencyTokenService tokens;
    OrderTxWriter writer;
    OrderService service;
    final List<OrderDtos.Item> items = List.of(new OrderDtos.Item(1001, 1));

    @BeforeEach void setup() {
        remote = mock(RemoteClients.class);
        repo = mock(OrderRepository.class);
        tokens = mock(IdempotencyTokenService.class);
        writer = mock(OrderTxWriter.class);
        service = new OrderService(remote, repo, new OrderStateMachine(), tokens, writer,
            new OrderMetrics(new SimpleMeterRegistry()));
    }

    @Test void lostResponseRetryUsesDurableOrderMappingWithoutNewReservation() {
        when(repo.completedRequest(eq(101L), eq("request-token"), anyString())).thenReturn("O-existing");
        when(repo.get("O-existing", 101)).thenReturn(snapshot("O-existing", OrderStatus.WAIT_PAY));
        assertEquals("O-existing", service.create(101, "request-token", items).orderNo());
        verifyNoInteractions(tokens, writer, remote);
    }

    @Test void redisCompletionFailureAfterCommitDoesNotReleaseCommittedInventory() {
        when(tokens.acquire(101, "request-token")).thenReturn(true);
        when(remote.sku(1001)).thenReturn(new SkuView(1001, 1, "Test", "Test", 9900, true));
        when(repo.get(anyString(), eq(101L))).thenAnswer(inv -> snapshot(inv.getArgument(0), OrderStatus.WAIT_PAY));
        doThrow(new IllegalStateException("Redis unavailable")).when(tokens).complete(eq(101L), anyString(), anyString());

        assertEquals("WAIT_PAY", service.create(101, "request-token", items).status());
        verify(writer).persist(anyLong(), anyString(), eq(101L), any(), eq("request-token"), anyString());
        verify(remote, never()).release(anyString());
    }

    @Test void persistenceFailureReleasesReservationAndAllowsRetry() {
        when(tokens.acquire(101, "request-token")).thenReturn(true);
        when(remote.sku(1001)).thenReturn(new SkuView(1001, 1, "Test", "Test", 9900, true));
        doThrow(new IllegalStateException("rollback")).when(writer).persist(anyLong(), anyString(), anyLong(), any(), anyString(), anyString());
        assertThrows(IllegalStateException.class, () -> service.create(101, "request-token", items));
        verify(remote).release(anyString());
        verify(tokens).release(101, "request-token");
    }

    @Test void deliveryRequiresPaidStateAndRecordsActualAdminOperator() {
        when(repo.get("O1", 101)).thenReturn(snapshot("O1", OrderStatus.WAIT_PAY));
        assertThrows(BizException.class, () -> service.deliver("O1", 101, 999));
        verify(repo, never()).transit(any(), any(), anyString(), anyString(), anyLong());
        var paid = snapshot("O1", OrderStatus.WAIT_DELIVERY);
        when(repo.get("O1", 101)).thenReturn(paid);
        when(repo.transit(paid, OrderStatus.WAIT_RECEIVE, "DELIVER", "OrderDelivered", 999)).thenReturn(true);
        service.deliver("O1", 101, 999);
        verify(repo).transit(paid, OrderStatus.WAIT_RECEIVE, "DELIVER", "OrderDelivered", 999);
    }

    @Test void duplicateSkuLinesAreRejectedBeforeInventoryCall() {
        assertThrows(BizException.class, () -> service.settle(List.of(new OrderDtos.Item(1001, 1), new OrderDtos.Item(1001, 2))));
        verifyNoInteractions(remote);
    }

    private OrderRepository.Snapshot snapshot(String no, OrderStatus status) {
        return new OrderRepository.Snapshot(1, no, 101, status, status == OrderStatus.WAIT_PAY ? 10 : 20,
            9900, 0, LocalDateTime.now());
    }
}
