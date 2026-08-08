package com.storyplatform.discovery.api;

import com.storyplatform.discovery.application.HomeOperations;
import com.storyplatform.discovery.application.HomeReadModel;
import com.storyplatform.shared.api.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Objects;

@RestController
public final class HomeController {

    private static final CacheControl PUBLIC_CACHE =
            CacheControl.maxAge(Duration.ofMinutes(2))
                    .cachePublic()
                    .staleWhileRevalidate(Duration.ofMinutes(10));

    private final HomeOperations homes;

    public HomeController(HomeOperations homes) {
        this.homes = Objects.requireNonNull(homes, "homes");
    }

    @GetMapping("/home")
    public ResponseEntity<HomeReadModel> get(
            @RequestParam(defaultValue = "vi-VN") String locale,
            @RequestHeader(
                    name = "If-None-Match",
                    required = false
            ) String ifNoneMatch
    ) {
        try {
            HomeReadModel model = homes.get(locale);
            String etag = "\"home-%s\"".formatted(model.version());
            if (etag.equals(ifNoneMatch)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .eTag(etag)
                        .cacheControl(PUBLIC_CACHE)
                        .build();
            }
            return ResponseEntity.ok()
                    .eTag(etag)
                    .cacheControl(PUBLIC_CACHE)
                    .body(model);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "LOCALE_INVALID",
                    "Home request rejected",
                    exception.getMessage()
            );
        }
    }
}
