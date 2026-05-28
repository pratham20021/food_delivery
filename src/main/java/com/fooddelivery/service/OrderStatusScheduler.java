package com.fooddelivery.service;

import com.fooddelivery.model.Order;
import com.fooddelivery.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusScheduler.class);

    private final OrderRepository orderRepository;
    private final SnsNotificationService snsService;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void progressOrderStatuses() {
        List<Order> activeOrders = orderRepository.findByStatusIn(List.of(
                Order.OrderStatus.ORDER_RECEIVED,
                Order.OrderStatus.PREPARING,
                Order.OrderStatus.OUT_FOR_DELIVERY
        ));

        for (Order order : activeOrders) {
            Order.OrderStatus next = switch (order.getStatus()) {
                case ORDER_RECEIVED   -> Order.OrderStatus.PREPARING;
                case PREPARING        -> Order.OrderStatus.OUT_FOR_DELIVERY;
                case OUT_FOR_DELIVERY -> Order.OrderStatus.DELIVERED;
                default               -> null;
            };

            if (next != null) {
                order.setStatus(next);
                order.setUpdatedAt(LocalDateTime.now());
                orderRepository.save(order);
                snsService.publishOrderStatusUpdate(order);
                log.info("Auto-progressed Order #{} to {}", order.getId(), next);
            }
        }
    }
}
