package com.fooddelivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.model.Order;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class SqsPublisherService {

    private static final Logger log = LoggerFactory.getLogger(SqsPublisherService.class);

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.order.queue.url:}")
    private String orderQueueUrl;

    public void publishOrderPlaced(Order order) {
        if (orderQueueUrl == null || orderQueueUrl.isBlank()) {
            log.warn("SQS_ORDER_QUEUE_URL not set — skipping SQS publish for Order #{}", order.getId());
            return;
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                "orderId",         order.getId(),
                "action",          "PROCESS_ORDER",
                "customerEmail",   order.getUser().getEmail(),
                "customerName",    order.getUser().getName(),
                "restaurantId",    order.getRestaurant().getId(),
                "restaurantName",  order.getRestaurant().getName(),
                "totalAmount",     order.getTotalAmount(),
                "deliveryAddress", order.getDeliveryAddress(),
                "status",          order.getStatus().name()
            ));
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(orderQueueUrl)
                    .messageBody(body)
                    .build());
            log.info("Order #{} published to SQS order-processing queue", order.getId());
        } catch (Exception e) {
            log.error("Failed to publish Order #{} to SQS: {}", order.getId(), e.getMessage());
        }
    }
}
