package com.fooddelivery.service;

import com.fooddelivery.model.Order;
import com.fooddelivery.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Separate bean so Spring AOP proxy intercepts the REQUIRES_NEW transaction.
 * Self-calls within the same class bypass the proxy and the transaction annotation.
 */
@Service
@RequiredArgsConstructor
public class OrderStatusUpdater {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusUpdater.class);

    private final OrderRepository orderRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order saveStatusChange(Long orderId, Order.OrderStatus next) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return null;

        // Guard: skip if already advanced (concurrent call)
        Order.OrderStatus expected = previousStatus(next);
        if (order.getStatus() != expected) {
            log.warn("Order #{} already at {} — skipping", orderId, order.getStatus());
            return null;
        }

        order.setStatus(next);
        order.setUpdatedAt(LocalDateTime.now());
        Order saved = orderRepository.save(order);
        log.info("Order #{} committed -> {}", orderId, next);
        return saved;
    }

    private Order.OrderStatus previousStatus(Order.OrderStatus next) {
        return switch (next) {
            case PREPARING        -> Order.OrderStatus.ORDER_RECEIVED;
            case OUT_FOR_DELIVERY -> Order.OrderStatus.PREPARING;
            case DELIVERED        -> Order.OrderStatus.OUT_FOR_DELIVERY;
            default               -> null;
        };
    }
}
