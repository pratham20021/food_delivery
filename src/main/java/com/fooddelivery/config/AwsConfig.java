package com.fooddelivery.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

@Configuration
public class AwsConfig {

    private static final Logger log = LoggerFactory.getLogger(AwsConfig.class);

    @Value("${aws.region}")
    private String region;

    // Optional — if blank, falls back to DefaultCredentialsProvider (IAM role on EC2)
    @Value("${aws.access.key.id:}")
    private String accessKeyId;

    @Value("${aws.secret.access.key:}")
    private String secretAccessKey;

    @Bean
    public SnsClient snsClient() {
        var builder = SnsClient.builder().region(Region.of(region));

        if (accessKeyId != null && !accessKeyId.isBlank() &&
            secretAccessKey != null && !secretAccessKey.isBlank()) {
            // Explicit credentials — used locally or when not on EC2 with IAM role
            builder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                )
            );
            log.info("AWS SNS: using explicit credentials for region [{}]", region);
        } else {
            // IAM role / ~/.aws/credentials / environment variables
            builder.credentialsProvider(DefaultCredentialsProvider.create());
            log.info("AWS SNS: using DefaultCredentialsProvider for region [{}]", region);
        }

        return builder.build();
    }
}
