package com.storyplatform.catalog.application.port;

public interface ChapterCursorCodec {

    String encode(Cursor cursor);

    Cursor decode(String value);

    record Cursor(String storyId, int number, String id) {
    }
}
