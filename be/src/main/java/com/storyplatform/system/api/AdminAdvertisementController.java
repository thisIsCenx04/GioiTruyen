package com.storyplatform.system.api;

import com.storyplatform.system.application.AdvertisementService;
import com.storyplatform.system.application.dto.AdvertisementResponse;
import com.storyplatform.system.application.dto.UpsertAdvertisementRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/advertisements")
public class AdminAdvertisementController {

    private final AdvertisementService advertisementService;

    public AdminAdvertisementController(AdvertisementService advertisementService) {
        this.advertisementService = advertisementService;
    }

    @GetMapping
    public List<AdvertisementResponse> list() {
        return advertisementService.list();
    }

    @GetMapping("/{id}")
    public AdvertisementResponse get(@PathVariable UUID id) {
        return advertisementService.get(id);
    }

    @PostMapping
    public AdvertisementResponse create(@Valid @RequestBody UpsertAdvertisementRequest request) {
        return advertisementService.create(request);
    }

    @PutMapping("/{id}")
    public AdvertisementResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpsertAdvertisementRequest request
    ) {
        return advertisementService.update(id, request);
    }

    @PatchMapping("/{id}/enabled")
    public AdvertisementResponse setEnabled(@PathVariable UUID id, @RequestParam boolean enabled) {
        return advertisementService.setEnabled(id, enabled);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        advertisementService.delete(id);
    }
}
