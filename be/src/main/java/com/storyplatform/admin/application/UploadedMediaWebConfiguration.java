package com.storyplatform.admin.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves admin-uploaded story assets from disk so the covers written by
 * {@link StoryMediaStorage} resolve at the URL stored on the story row.
 */
@Configuration
public class UploadedMediaWebConfiguration implements WebMvcConfigurer {

    private final StoryMediaStorage storyMedia;
    private final String handlerPath;

    public UploadedMediaWebConfiguration(
            StoryMediaStorage storyMedia,
            @Value("${app.media.local.handler-path:/uploads}") String handlerPath
    ) {
        this.storyMedia = storyMedia;
        this.handlerPath = handlerPath.replaceAll("/+$", "");
    }

    /**
     * Registered without the servlet context path: handler patterns are matched
     * against the path inside the context, while the URL saved on the story row
     * is absolute and therefore includes it.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(handlerPath + "/**")
                .addResourceLocations(storyMedia.uploadRoot().toUri().toString());
    }
}
