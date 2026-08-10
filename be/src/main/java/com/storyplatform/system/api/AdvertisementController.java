package com.storyplatform.system.api;

import com.storyplatform.system.application.AdvertisementService;
import com.storyplatform.system.application.dto.AdEventRequest;
import com.storyplatform.system.application.dto.ActiveAdvertisementResponse;
import com.storyplatform.system.domain.AdvertisementPlacement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/advertisements")
public class AdvertisementController {

    private final AdvertisementService advertisementService;

    public AdvertisementController(AdvertisementService advertisementService) {
        this.advertisementService = advertisementService;
    }

    @GetMapping("/active")
    public ResponseEntity<ActiveAdvertisementResponse> active(
            @RequestParam(defaultValue = "GLOBAL_CLICK") AdvertisementPlacement placement
    ) {
        return advertisementService.active(placement)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{advertisementId}/events")
    public ResponseEntity<Void> track(
            @PathVariable UUID advertisementId,
            @Valid @RequestBody AdEventRequest request,
            HttpServletRequest servletRequest
    ) {
        advertisementService.track(advertisementId, request, ipHash(servletRequest));
        return ResponseEntity.accepted().build();
    }

    private String ipHash(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ip.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
