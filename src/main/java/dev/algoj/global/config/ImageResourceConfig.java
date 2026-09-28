package dev.algoj.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Serves locally stored statement images (IMAGE_DIR) at /images/**, so the
 * public URL is the API's own domain and the box needs no extra nginx config —
 * nginx already forwards everything to the active API.
 *
 * Files sit under random UUID names and never change, so they are cached for a
 * year. Only files are served (no directory listing). Security headers for this
 * path are set in SecurityConfig.
 */
@Configuration
@ConditionalOnExpression("!'${app.images.dir:}'.isEmpty()")
public class ImageResourceConfig implements WebMvcConfigurer {

    public static final String PATH_PREFIX = "/images/";

    private final String dir;

    public ImageResourceConfig(@Value("${app.images.dir}") String dir) {
        this.dir = dir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(PATH_PREFIX + "**")
                .addResourceLocations(Path.of(dir).toUri().toString())
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }
}
