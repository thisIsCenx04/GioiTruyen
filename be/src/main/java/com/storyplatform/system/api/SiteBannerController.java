package com.storyplatform.system.api;

import com.storyplatform.system.application.SiteBannerService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Banner images the public pages read; slot to image URL. */
@RestController
@RequestMapping("/site/banners")
public class SiteBannerController {

    private final SiteBannerService bannerService;

    public SiteBannerController(SiteBannerService bannerService) {
        this.bannerService = bannerService;
    }

    @GetMapping
    public Map<String, String> banners() {
        return bannerService.publicBanners();
    }
}
