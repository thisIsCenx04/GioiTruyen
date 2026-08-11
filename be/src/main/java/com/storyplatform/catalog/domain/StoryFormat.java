package com.storyplatform.catalog.domain;

/**
 * How a story is meant to be read.
 *
 * <p>Both formats store their text as rows in {@code chapters}; a {@link #ONESHOT}
 * simply keeps exactly one. The distinction cannot be derived from the chapter
 * count, because a serial also has a single chapter on the day it is created,
 * and the reader must pick a layout before any chapter has been fetched.
 */
public enum StoryFormat {

    /** A serialised novel read chapter by chapter. */
    SERIAL,

    /** A Zhihu-style short story read start to finish on one page. */
    ONESHOT
}
