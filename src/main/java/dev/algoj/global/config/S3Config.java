package dev.algoj.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

/**
 * S3 client for problem statement images. Only created when a bucket is
 * configured (S3_IMAGE_BUCKET) so local dev without AWS keys boots normally —
 * the upload API then fails with IMAGE_STORAGE_NOT_CONFIGURED instead.
 * Credentials come from the default provider chain (.env → container env).
 *
 * S3_ENDPOINT points the client at an S3-compatible store instead of AWS
 * (OCI Object Storage, Cloudflare R2, MinIO). Those want path-style addressing
 * — virtual-host style (bucket.endpoint) does not resolve there — so
 * S3_PATH_STYLE defaults to true whenever an endpoint is set. Left unset, the
 * client talks to AWS S3 exactly as before.
 */
@Configuration
public class S3Config {

    @Bean
    @ConditionalOnExpression("!'${app.s3.image-bucket:}'.isEmpty()")
    public S3Client s3Client(@Value("${app.s3.region}") String region,
                             @Value("${app.s3.endpoint:}") String endpoint,
                             @Value("${app.s3.path-style:}") String pathStyle) {
        S3ClientBuilder builder = S3Client.builder().region(Region.of(region));
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                    .forcePathStyle(!"false".equalsIgnoreCase(pathStyle.trim()));
        }
        return builder.build();
    }
}
