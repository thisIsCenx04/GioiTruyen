package com.storyplatform.system.application;

import com.storyplatform.system.application.dto.ActiveAdvertisementResponse;
import com.storyplatform.system.application.dto.AdEventRequest;
import com.storyplatform.system.application.dto.AdvertisementResponse;
import com.storyplatform.system.application.dto.UpsertAdvertisementRequest;
import com.storyplatform.system.domain.AdEvent;
import com.storyplatform.system.domain.Advertisement;
import com.storyplatform.system.domain.AdvertisementPlacement;
import com.storyplatform.system.infrastructure.AdEventRepository;
import com.storyplatform.system.infrastructure.AdvertisementRepository;
import com.storyplatform.shared.api.ApiException;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.springframework.http.HttpStatus;

@Service
public class AdvertisementService {

    private static final int DEFAULT_COOLDOWN_SECONDS = 600;
    private static final int DEFAULT_MAX_CLICKS_PER_DAY = 5;

    private final AdvertisementRepository advertisementRepository;
    private final AdEventRepository adEventRepository;
    private final JdbcClient jdbc;
    private final Clock clock;

    @Autowired
    public AdvertisementService(
            AdvertisementRepository advertisementRepository,
            AdEventRepository adEventRepository,
            JdbcClient jdbc
    ) {
        this(advertisementRepository, adEventRepository, jdbc, Clock.systemUTC());
    }

    AdvertisementService(
            AdvertisementRepository advertisementRepository,
            AdEventRepository adEventRepository,
            JdbcClient jdbc,
            Clock clock
    ) {
        this.advertisementRepository = advertisementRepository;
        this.adEventRepository = adEventRepository;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<ActiveAdvertisementResponse> active(AdvertisementPlacement placement) {
        Instant now = Instant.now(clock);
        return advertisementRepository.findActive(placement.name(), now).map(this::toActiveResponse);
    }

    @Transactional(readOnly = true)
    public List<AdvertisementResponse> list() {
        return StreamSupport.stream(advertisementRepository.findAll().spliterator(), false)
                .sorted(Comparator.comparing(Advertisement::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdvertisementResponse get(UUID id) {
        return toResponse(find(id));
    }

    @Transactional
    public AdvertisementResponse create(UpsertAdvertisementRequest request) {
        validate(request);
        Instant now = Instant.now(clock);
        UUID id = UUID.randomUUID();

        // The id is assigned here, so an explicit INSERT is used; repository.save()
        // would treat the populated id as an existing row and emit an UPDATE that
        // matches nothing.
        jdbc.sql("""
                        INSERT INTO advertisements
                            (id, name, type, image_url, target_url, placement, cooldown_seconds,
                             max_clicks_per_day, priority, trigger_every_n_views, start_at, end_at,
                             is_active, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?)
                        """)
                .params(id.toString(), request.name().trim(), request.type().name(),
                        blankToNull(request.imageUrl()), request.targetUrl().trim(),
                        request.placement().name(), request.cooldownSeconds(),
                        request.maxClicksPerDay(), request.priority(),
                        request.startAt() == null ? null : java.sql.Timestamp.from(request.startAt()),
                        request.endAt() == null ? null : java.sql.Timestamp.from(request.endAt()),
                        request.active(),
                        java.sql.Timestamp.from(now), java.sql.Timestamp.from(now))
                .update();

        return toResponse(find(id));
    }

    @Transactional
    public AdvertisementResponse update(UUID id, UpsertAdvertisementRequest request) {
        validate(request);
        Advertisement advertisement = find(id);
        apply(advertisement, request);
        advertisement.setUpdatedAt(Instant.now(clock));
        return toResponse(advertisementRepository.save(advertisement));
    }

    @Transactional
    public AdvertisementResponse setEnabled(UUID id, boolean enabled) {
        Advertisement advertisement = find(id);
        advertisement.setIsActive(enabled);
        advertisement.setUpdatedAt(Instant.now(clock));
        return toResponse(advertisementRepository.save(advertisement));
    }

    /**
     * Ad events cascade-delete with the advertisement, so a link that has already
     * collected clicks is deactivated instead of removed to keep its analytics.
     */
    @Transactional
    public void delete(UUID id) {
        Advertisement advertisement = find(id);
        if (adEventRepository.countByAdvertisementId(id) > 0) {
            advertisement.setIsActive(false);
            advertisement.setUpdatedAt(Instant.now(clock));
            advertisementRepository.save(advertisement);
            return;
        }
        advertisementRepository.delete(advertisement);
    }

    @Transactional
    public void track(UUID advertisementId, AdEventRequest request, String ipHash) {
        Advertisement advertisement = find(advertisementId);
        if (!Boolean.TRUE.equals(advertisement.getIsActive())) {
            throw new ApiException(HttpStatus.CONFLICT, "advertisement.inactive", "Advertisement inactive", "Advertisement is inactive");
        }
        AdEvent event = new AdEvent();
        event.setId(UUID.randomUUID());
        event.setAdvertisementId(advertisementId);
        event.setUserId(request.userId());
        event.setSessionId(request.sessionId());
        event.setStoryId(request.storyId());
        event.setPageUrl(request.pageUrl());
        event.setIpHash(ipHash);
        event.setEventType(request.eventType());
        event.setCreatedAt(Instant.now(clock));
        adEventRepository.save(event);
    }

    private ActiveAdvertisementResponse toActiveResponse(Advertisement advertisement) {
        return new ActiveAdvertisementResponse(
                advertisement.getId(),
                advertisement.getTargetUrl(),
                valueOrDefault(advertisement.getCooldownSeconds(), DEFAULT_COOLDOWN_SECONDS),
                valueOrDefault(advertisement.getMaxClicksPerDay(), DEFAULT_MAX_CLICKS_PER_DAY)
        );
    }

    private Advertisement find(UUID id) {
        return advertisementRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "advertisement.not_found", "Advertisement not found", "Advertisement not found"));
    }

    private void apply(Advertisement advertisement, UpsertAdvertisementRequest request) {
        advertisement.setName(request.name());
        advertisement.setType(request.type());
        advertisement.setImageUrl(blankToNull(request.imageUrl()));
        advertisement.setTargetUrl(request.targetUrl());
        advertisement.setPlacement(request.placement());
        advertisement.setCooldownSeconds(request.cooldownSeconds());
        advertisement.setMaxClicksPerDay(request.maxClicksPerDay());
        advertisement.setPriority(request.priority());
        advertisement.setStartAt(request.startAt());
        advertisement.setEndAt(request.endAt());
        advertisement.setIsActive(request.active());
        advertisement.setTriggerEveryNView(null);
    }

    private void validate(UpsertAdvertisementRequest request) {
        if (request.endAt() != null && request.startAt() != null && !request.endAt().isAfter(request.startAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "advertisement.invalid_window", "Invalid advertisement window", "endAt must be after startAt");
        }
        try {
            URI uri = new URI(request.targetUrl());
            if (!List.of("http", "https").contains(uri.getScheme())) {
                throw new URISyntaxException(request.targetUrl(), "Unsupported scheme");
            }
        } catch (URISyntaxException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "advertisement.invalid_target_url", "Invalid target URL", "targetUrl must be an absolute http(s) URL");
        }
    }

    private AdvertisementResponse toResponse(Advertisement advertisement) {
        return new AdvertisementResponse(
                advertisement.getId(),
                advertisement.getName(),
                advertisement.getType(),
                advertisement.getImageUrl(),
                advertisement.getTargetUrl(),
                advertisement.getPlacement(),
                valueOrDefault(advertisement.getCooldownSeconds(), DEFAULT_COOLDOWN_SECONDS),
                valueOrDefault(advertisement.getMaxClicksPerDay(), DEFAULT_MAX_CLICKS_PER_DAY),
                valueOrDefault(advertisement.getPriority(), 0),
                advertisement.getStartAt(),
                advertisement.getEndAt(),
                Boolean.TRUE.equals(advertisement.getIsActive()),
                advertisement.getCreatedAt(),
                advertisement.getUpdatedAt()
        );
    }

    private static int valueOrDefault(Integer value, int fallback) {
        return value == null || value < 0 ? fallback : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
