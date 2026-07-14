package dev.algoj.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * S3 client for problem statement images. Only created when a bucket is
 * configured (S3_IMAGE_BUCKET) so local dev without AWS keys boots normally —
 * the upload API then fails with IMAGE_STORAGE_NOT_CONFIGURED instead.
 * Credentials come from the default provider chain (.env → container env).
 */
@Configuration
public class S3Config {

    @Bean
    @ConditionalOnExpression("!'${app.s3.image-bucket:}'.isEmpty()")
    public S3Client s3Client(@Value("${app.s3.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }
}
