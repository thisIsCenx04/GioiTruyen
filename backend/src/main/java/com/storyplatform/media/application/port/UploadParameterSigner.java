package com.storyplatform.media.application.port;

import java.util.Map;

@FunctionalInterface
public interface UploadParameterSigner {

    String sign(Map<String, String> parameters);
}
