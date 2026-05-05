package com.fooddelivery.service;

import com.fooddelivery.dto.Dtos.OrderRequest;
import com.fooddelivery.exception.BadRequestException;
import com.fooddelivery.exception.ResourceNotFoundException;
import com.fooddelivery.model.*;
import com.fooddelivery.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final SnsNotificationService snsService;

    @Transactional
    public Order placeOrder(String userEmail, OrderRequest req) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Restaurant restaurant = restaurantRepository.findById(req.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));

        Order order = new Order();
        order.setUser(user);
        order.setRestaurant(restaurant);
        order.setDeliveryAddress(req.getDeliveryAddress());
        order.setStatus(Order.OrderStatus.ORDER_RECEIVED);

        List<OrderItem> items = req.getItems().stream().map(itemReq -> {
            MenuItem menuItem = menuItemRepository.findById(itemReq.getMenuItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Menu item not found: " + itemReq.getMenuItemId()));
            if (!menuItem.isAvailable()) throw new BadRequestException("Item not available: " + menuItem.getName());

            OrderItem oi = new OrderItem();
            oi.setOrder(order);
            oi.setMenuItem(menuItem);
            oi.setQuantity(itemReq.getQuantity());
            oi.setPrice(menuItem.getPrice());
            return oi;
        }).collect(Collectors.toList());

        order.setItems(items);
        order.setTotalAmount(items.stream().mapToDouble(i -> i.getPrice() * i.getQuantity()).sum());

        Order saved = orderRepository.save(order);
        snsService.publishOrderStatusUpdate(saved);
        log.info("Order #{} placed by {}", saved.getId(), userEmail);
        return saved;
    }

    @Transactional
    public Order updateOrderStatus(Long orderId, Order.OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        validateStatusTransition(order.getStatus(), newStatus);
        order.setStatus(newStatus);
        order.setUpdatedAt(LocalDateTime.now());

        Order updated = orderRepository.save(order);
        snsService.publishOrderStatusUpdate(updated);
        log.info("Order #{} status updated to {}", orderId, newStatus);
        return updated;
    }

    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    public List<Order> getUserOrders(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    private void validateStatusTransition(Order.OrderStatus current, Order.OrderStatus next) {
        boolean valid = switch (current) {
            case ORDER_RECEIVED  -> next == Order.OrderStatus.PREPARING;
            case PREPARING       -> next == Order.OrderStatus.OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY -> next == Order.OrderStatus.DELIVERED;
            case DELIVERED       -> false;
        };
        if (!valid) throw new BadRequestException(
                "Invalid status transition: " + current + " -> " + next);
    }
}
