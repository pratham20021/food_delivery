package com.fooddelivery.controller;

import com.fooddelivery.dto.Dtos.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sns")
@RequiredArgsConstructor
public class SnsAdminController {

    private static final Logger log = LoggerFactory.getLogger(SnsAdminController.class);

    private final SnsClient snsClient;

    @Value("${aws.sns.topic.arn:}")
    private String configuredTopicArn;

    @Value("${aws.region}")
    private String region;

    // ── GET /api/sns/status ───────────────────────────────────────────────────
    // Returns current SNS config status — safe to call anytime
    @GetMapping("/status")
    public ResponseEntity<ApiResponse> status() {
        Map<String, Object> info = new HashMap<>();
        info.put("region", region);
        info.put("topicArn", configuredTopicArn.isBlank() ? "NOT CONFIGURED" : configuredTopicArn);
        info.put("snsEnabled", !configuredTopicArn.isBlank());

        if (!configuredTopicArn.isBlank()) {
            try {
                var attrs = snsClient.getTopicAttributes(
                    GetTopicAttributesRequest.builder().topicArn(configuredTopicArn).build()
                );
                info.put("topicName", attrs.attributes().get("DisplayName"));
                info.put("subscriptionsConfirmed", attrs.attributes().get("SubscriptionsConfirmed"));
                info.put("subscriptionsPending", attrs.attributes().get("SubscriptionsPending"));
            } catch (Exception e) {
                info.put("error", e.getMessage());
            }
        }
        return ResponseEntity.ok(new ApiResponse(true, "SNS status", info));
    }

    // ── POST /api/sns/setup?topicName=food-delivery-notifications&email=you@x.com
    // Creates the SNS topic and subscribes the email — run once after deploy
    @PostMapping("/setup")
    public ResponseEntity<ApiResponse> setup(
            @RequestParam(defaultValue = "food-delivery-notifications") String topicName,
            @RequestParam String email) {
        try {
            // 1. Create topic (idempotent — safe to call multiple times)
            CreateTopicResponse topicRes = snsClient.createTopic(
                CreateTopicRequest.builder().name(topicName).build()
            );
            String topicArn = topicRes.topicArn();
            log.info("SNS topic ready: {}", topicArn);

            // 2. Subscribe email
            SubscribeResponse subRes = snsClient.subscribe(
                SubscribeRequest.builder()
                    .topicArn(topicArn)
                    .protocol("email")
                    .endpoint(email)
                    .build()
            );

            Map<String, String> result = new HashMap<>();
            result.put("topicArn", topicArn);
            result.put("subscriptionArn", subRes.subscriptionArn());
            result.put("nextStep",
                "Check " + email + " and click 'Confirm subscription'. " +
                "Then set SNS_TOPIC_ARN=" + topicArn + " in your environment.");

            return ResponseEntity.ok(new ApiResponse(true,
                "SNS topic created and subscription email sent to " + email, result));

        } catch (Exception e) {
            log.error("SNS setup failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(new ApiResponse(false, "SNS setup failed: " + e.getMessage(), null));
        }
    }

    // ── GET /api/sns/subscriptions ────────────────────────────────────────────
    @GetMapping("/subscriptions")
    public ResponseEntity<ApiResponse> subscriptions() {
        if (configuredTopicArn.isBlank()) {
            return ResponseEntity.badRequest()
                .body(new ApiResponse(false, "SNS_TOPIC_ARN not configured", null));
        }
        try {
            var subs = snsClient.listSubscriptionsByTopic(
                ListSubscriptionsByTopicRequest.builder().topicArn(configuredTopicArn).build()
            ).subscriptions().stream()
                .map(s -> Map.of(
                    "endpoint", s.endpoint(),
                    "protocol", s.protocol(),
                    "status",   s.subscriptionArn().equals("PendingConfirmation")
                                ? "PENDING" : "CONFIRMED"
                ))
                .collect(Collectors.toList());

            return ResponseEntity.ok(new ApiResponse(true, "Subscriptions", subs));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new ApiResponse(false, e.getMessage(), null));
        }
    }

    // ── POST /api/sns/test ────────────────────────────────────────────────────
    // Sends a test notification to verify the full SNS pipeline works
    @PostMapping("/test")
    public ResponseEntity<ApiResponse> testPublish() {
        if (configuredTopicArn.isBlank()) {
            return ResponseEntity.badRequest()
                .body(new ApiResponse(false, "SNS_TOPIC_ARN not configured. Call /api/sns/setup first.", null));
        }
        try {
            PublishResponse res = snsClient.publish(
                PublishRequest.builder()
                    .topicArn(configuredTopicArn)
                    .subject("🧪 FoodDash SNS Test Notification")
                    .message("""
                        This is a test notification from FoodDash.
                        ==========================================
                        If you received this email, your AWS SNS
                        integration is working correctly!
                        
                        Topic ARN : %s
                        Region    : %s
                        """.formatted(configuredTopicArn, region))
                    .build()
            );
            return ResponseEntity.ok(new ApiResponse(true,
                "Test notification sent! Check your email.", Map.of("messageId", res.messageId())));
        } catch (Exception e) {
            log.error("SNS test publish failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(new ApiResponse(false, "Publish failed: " + e.getMessage(), null));
        }
    }
}
