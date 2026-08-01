package com.storyplatform.catalog.application;

public interface ChapterAccessOperations {

    ChapterAccessView status(String chapterId, String userId);

    ChapterAccessView unlock(String chapterId, String userId);

    void requireReadable(String chapterId, String userId);

    static ChapterAccessOperations unrestricted() {
        return new ChapterAccessOperations() {
            @Override
            public ChapterAccessView status(String chapterId, String userId) {
                return new ChapterAccessView(
                        chapterId,
                        "",
                        "",
                        0,
                        true,
                        userId != null,
                        0
                );
            }

            @Override
            public ChapterAccessView unlock(String chapterId, String userId) {
                return status(chapterId, userId);
            }

            @Override
            public void requireReadable(String chapterId, String userId) {
            }
        };
    }

    record ChapterAccessView(
            String chapterId,
            String storyId,
            String chapterTitle,
            long priceXu,
            boolean unlocked,
            boolean authenticated,
            long availableXu
    ) {
    }
}
