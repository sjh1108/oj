package dev.algoj.domain.image.service;

import dev.algoj.domain.image.dto.UploadImageRequest;
import dev.algoj.domain.image.dto.UploadImageResponse;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Uploads problem statement images to S3 and returns the public object URL.
 * The bucket policy allows anonymous GetObject on problems/* only, so images
 * are readable by URL (unguessable UUID) without listing.
 */
@Service
@RequiredArgsConstructor
public class ImageService {

    // Keep well under nginx's 1MB body cap (base64 adds ~33%).
    private static final int MAX_BYTES = 700 * 1024;

    private static final Map<String, String> EXTENSION_BY_TYPE = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/gif", "gif",
            "image/webp", "webp",
            "image/svg+xml", "svg"
    );

    private final ObjectProvider<S3Client> s3ClientProvider;

    @Value("${app.s3.image-bucket}")
    private String bucket;

    @Value("${app.s3.region}")
    private String region;

    public UploadImageResponse upload(UploadImageRequest req) {
        S3Client s3 = s3ClientProvider.getIfAvailable();
        if (s3 == null || bucket.isBlank()) {
            throw new BusinessException(ErrorCode.IMAGE_STORAGE_NOT_CONFIGURED);
        }

        String extension = EXTENSION_BY_TYPE.get(req.contentType());
        if (extension == null) {
            throw new BusinessException(ErrorCode.IMAGE_TYPE_NOT_ALLOWED);
        }

        byte[] data;
        try {
            data = Base64.getDecoder().decode(req.base64Data());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "base64Data 디코드에 실패했습니다.");
        }
        if (data.length > MAX_BYTES) {
            throw new BusinessException(ErrorCode.IMAGE_TOO_LARGE);
        }

        String key = "problems/" + UUID.randomUUID() + "." + extension;
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(req.contentType())
                        // Immutable content under a UUID key — cache forever.
                        .cacheControl("public, max-age=31536000, immutable")
                        .build(),
                RequestBody.fromBytes(data));

        return new UploadImageResponse(
                "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, region, key));
    }
}
