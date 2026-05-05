package com.fooddelivery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

@Configuration
public class AwsConfig {

    @Value("${aws.region}")
    private String region;

    @Bean
    public SnsClient snsClient() {
        // Uses DefaultCredentialsProvider: env vars, ~/.aws/credentials, or EC2 IAM role
        return SnsClient.builder()
                .region(Region.of(region))
                .build();
    }
}
