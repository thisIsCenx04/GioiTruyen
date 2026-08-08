package com.storyplatform.discovery.application.port;

public interface SearchCursorCodec {

    String encode(String atlasToken);

    String decode(String cursor);
}
