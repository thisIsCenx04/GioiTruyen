package com.storyplatform.catalog.domain;

/**
 * Which shelf a story belongs to.
 *
 * <p>Both formats store their text as rows in {@code chapters} and are read the
 * same way, chapter by chapter; they differ only in the word budget a long
 * upload is cut at and in where the story is listed. The flag cannot be derived
 * from the chapter count, since either format may hold any number of them.
 */
public enum StoryFormat {

    /** A serialised novel, cut at 800 words when a file carries no headings. */
    SERIAL,

    /** A Zhihu-style story, cut at 1400 words and listed on the Zhihu page. */
    ONESHOT
}
