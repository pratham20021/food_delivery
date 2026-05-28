package com.fooddelivery.service;

import com.fooddelivery.model.Order;
import com.fooddelivery.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusScheduler.class);

    private final OrderRepository        orderRepository;
    private final OrderStatusUpdater     statusUpdater;
    private final SnsNotificationService snsService;
    private final InvoiceService         invoiceService;

    @Scheduled(fixedDelay = 5000)
    public void progressOrderStatuses() {

        // 1. Read active orders — no transaction, just a plain read
        List<Order> activeOrders = orderRepository.findByStatusIn(List.of(
                Order.OrderStatus.ORDER_RECEIVED,
                Order.OrderStatus.PREPARING,
                Order.OrderStatus.OUT_FOR_DELIVERY
        ));

        if (activeOrders.isEmpty()) return;

        // 2. Commit each status change in its own isolated transaction via separate bean
        //    A failed SNS/SES call can NEVER roll back the DB write
        List<Order> progressed = new ArrayList<>();
        for (Order order : activeOrders) {
            Order.OrderStatus next = nextStatus(order.getStatus());
            if (next == null) continue;

            Order saved = statusUpdater.saveStatusChange(order.getId(), next);
            if (saved != null) progressed.add(saved);
        }

        // 3. Fire notifications AFTER all DB commits — fully outside any transaction
        for (Order order : progressed) {
            snsService.publishOrderStatusUpdate(order);
            if (order.getStatus() == Order.OrderStatus.DELIVERED) {
                invoiceService.sendInvoiceEmail(order);
            }
        }
    }

    private Order.OrderStatus nextStatus(Order.OrderStatus current) {
        return switch (current) {
            case ORDER_RECEIVED   -> Order.OrderStatus.PREPARING;
            case PREPARING        -> Order.OrderStatus.OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY -> Order.OrderStatus.DELIVERED;
            default               -> null;
        };
    }
}
