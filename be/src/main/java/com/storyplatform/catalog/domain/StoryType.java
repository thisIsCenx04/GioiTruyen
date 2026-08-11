package com.storyplatform.catalog.domain;

/**
 * Editorial classification used by the catalog shelves.
 *
 * <p>Separate from {@link StoryContentType} (which describes the medium) and
 * {@link StoryFormat} (which describes serial vs one-page). Everything defaults
 * to {@link #TEXT}; the other values are deliberate editorial choices.
 */
public enum StoryType {

    /** Plain prose. The default for every story. */
    TEXT,

    /** Narrated audio. */
    AUDIO,

    /** Exclusive to the platform; gets the top shelf. */
    EXCLUSIVE,

    /** Original writing rather than a translation. */
    ORIGINAL
}
