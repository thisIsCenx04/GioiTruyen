package com.storyplatform.system.api;

import com.storyplatform.system.application.SiteBannerService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Banner settings tab of the admin dashboard. */
@RestController
@RequestMapping("/admin/banners")
public class AdminSiteBannerController {

    private final SiteBannerService bannerService;

    public AdminSiteBannerController(SiteBannerService bannerService) {
        this.bannerService = bannerService;
    }

    @GetMapping
    public List<SiteBannerService.BannerSlot> list() {
        return bannerService.slots();
    }

    @PostMapping("/{slot}")
    public SiteBannerService.BannerSlot upload(
            @PathVariable String slot,
            @RequestParam("image") MultipartFile image
    ) {
        return bannerService.upload(slot, image);
    }

    @DeleteMapping("/{slot}")
    public SiteBannerService.BannerSlot reset(@PathVariable String slot) {
        return bannerService.reset(slot);
    }
}
