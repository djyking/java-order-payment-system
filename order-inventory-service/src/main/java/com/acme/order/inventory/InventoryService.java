package com.acme.order.inventory;

import com.acme.order.api.inventory.InventoryDtos.ReserveRequest;
import com.acme.order.common.observability.OrderMetrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

/**
 * 编排库存预占、确认和释放事务。
 *
 * @author heyu
 * @since 2026-08-02
 */
@Service
public class InventoryService {

    private final InventoryRepository repo;
    private final OrderMetrics metrics;

    @Value("${debug.failure.reserve-delay-ms:0}")
    private long reserveDelayMs;

    @Value("${debug.failure.reserve-error:false}")
    private boolean reserveError;

    public InventoryService(InventoryRepository repo, OrderMetrics metrics) {
        this.repo = repo;
        this.metrics = metrics;
    }

    @Transactional
    public void reserve(ReserveRequest r) {
        try {
            if (reserveDelayMs > 0) {
                Thread.sleep(Math.min(reserveDelayMs, 30_000));
            }
            if (reserveError) {
                throw new IllegalStateException("fault injection: inventory reserve error");
            }
            r.items().forEach(i -> repo.reserve(r.orderNo(), r.userId(), i.skuId(), i.quantity()));
            metrics.success("inventory_reserve");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            metrics.failure("inventory_reserve");
            throw new IllegalStateException("inventory reserve interrupted", e);
        } catch (RuntimeException e) {
            metrics.failure("inventory_reserve");
            throw e;
        }
    }

    @Transactional
    public int confirm(String o) {
        return repo.confirm(o);
    }

    @Transactional
    public int release(String o) {
        return repo.release(o);
    }
}
