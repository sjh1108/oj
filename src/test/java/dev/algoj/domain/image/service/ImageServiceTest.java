package dev.algoj.domain.image.service;

import dev.algoj.domain.image.dto.UploadImageRequest;
import dev.algoj.domain.image.dto.UploadImageResponse;
import dev.algoj.global.exception.BusinessException;
import dev.algoj.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @Mock
    ObjectProvider<S3Client> s3ClientProvider;
    @Mock
    S3Client s3Client;

    @InjectMocks
    ImageService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "bucket", "algoj-images");
        ReflectionTestUtils.setField(service, "region", "ap-northeast-2");
    }

    private static String b64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    @Test
    void upload_putsObjectWithMetadata_andReturnsPublicUrl() {
        when(s3ClientProvider.getIfAvailable()).thenReturn(s3Client);

        UploadImageResponse res = service.upload(
                new UploadImageRequest("image/png", b64(new byte[]{1, 2, 3})));

        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(put.capture(), any(RequestBody.class));
        assertThat(put.getValue().bucket()).isEqualTo("algoj-images");
        assertThat(put.getValue().key()).startsWith("problems/").endsWith(".png");
        assertThat(put.getValue().contentType()).isEqualTo("image/png");
        assertThat(put.getValue().cacheControl()).contains("immutable");
        assertThat(res.url())
                .startsWith("https://algoj-images.s3.ap-northeast-2.amazonaws.com/problems/")
                .endsWith(".png");
    }

    @Test
    void upload_rejectsUnsupportedContentType() {
        when(s3ClientProvider.getIfAvailable()).thenReturn(s3Client);

        assertThatThrownBy(() -> service.upload(
                new UploadImageRequest("application/pdf", b64(new byte[]{1}))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_TYPE_NOT_ALLOWED);
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void upload_rejectsOversizedImage() {
        when(s3ClientProvider.getIfAvailable()).thenReturn(s3Client);

        assertThatThrownBy(() -> service.upload(
                new UploadImageRequest("image/png", b64(new byte[701 * 1024]))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_TOO_LARGE);
    }

    @Test
    void upload_failsClosedWhenS3NotConfigured() {
        when(s3ClientProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.upload(
                new UploadImageRequest("image/png", b64(new byte[]{1}))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_STORAGE_NOT_CONFIGURED);
    }
}
