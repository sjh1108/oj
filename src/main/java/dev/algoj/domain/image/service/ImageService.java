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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Stores problem statement images and returns the URL the browser loads them from.
 *
 * Two backends, picked by configuration:
 * <ul>
 *   <li><b>Local disk</b> ({@code IMAGE_DIR}) — the box's own filesystem. The API
 *       serves the files back at {@code /images/**} ({@code ImageResourceConfig}),
 *       so the public URL is {@code IMAGE_PUBLIC_BASE_URL/problems/<uuid>.<ext>}.
 *       This is what production uses.</li>
 *   <li><b>S3 / S3-compatible</b> ({@code S3_IMAGE_BUCKET}) — kept so a bucket can be
 *       plugged back in by configuration alone.</li>
 * </ul>
 * Local disk wins when both are set. Neither → 503 (I003). Keys are random UUIDs,
 * so images are readable by URL without being listable.
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

    // Local directory images are written to (inside the container). Empty → S3.
    @Value("${app.images.dir:}")
    private String localDir = "";

    // Where the local directory is served from, e.g. https://algoj.duckdns.org/images
    @Value("${app.images.public-base-url:}")
    private String localPublicBaseUrl = "";

    @Value("${app.s3.image-bucket}")
    private String bucket;

    @Value("${app.s3.region}")
    private String region;

    // Base URL the uploaded object is served from. Empty → AWS S3's own
    // virtual-host URL. Set it when the bucket lives on an S3-compatible store
    // or behind a CDN/custom domain, since those serve objects from a different
    // host than the AWS pattern.
    @Value("${app.s3.public-base-url:}")
    private String publicBaseUrl = "";

    public UploadImageResponse upload(UploadImageRequest req) {
        boolean local = !isBlank(localDir);
        S3Client s3 = local ? null : s3ClientProvider.getIfAvailable();
        if (local ? isBlank(localPublicBaseUrl) : (s3 == null || isBlank(bucket))) {
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
        if (local) {
            writeLocal(key, data);
            return new UploadImageResponse(joinUrl(localPublicBaseUrl, key));
        }

        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(req.contentType())
                        // Immutable content under a UUID key — cache forever.
                        .cacheControl("public, max-age=31536000, immutable")
                        .build(),
                RequestBody.fromBytes(data));

        return new UploadImageResponse(publicUrl(key));
    }

    private void writeLocal(String key, byte[] data) {
        Path target = Path.of(localDir).resolve(key);
        try {
            Files.createDirectories(target.getParent());
            // Write beside the target and move into place, so the file is never
            // served half-written.
            Path tmp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                Files.write(tmp, data);
                // nginx/rclone on the host read these as another user.
                Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-r--r--"));
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("이미지 저장 실패: " + target, e);
        }
    }

    private String publicUrl(String key) {
        if (isBlank(publicBaseUrl)) {
            return "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, region, key);
        }
        return joinUrl(publicBaseUrl, key);
    }

    private static String joinUrl(String base, String key) {
        return "%s/%s".formatted(base.replaceAll("/+$", ""), key);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
