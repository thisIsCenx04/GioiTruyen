package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.catalog.domain.Category;
import com.storyplatform.catalog.infrastructure.persistence
        .MongoCategoryDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

public final class CategoryTaxonomyMigration implements MongoMigration {

    private static final String CHECKSUM =
            "86681661d93e06e5fd4f2d57f1c65e66bd9ddf11e09d1282186011f184b9cc9c";

    private static final List<Seed> SEEDS = List.of(
            seed(1, "fantasy", "Kỳ ảo", Category.Group.GENRE, 10),
            seed(2, "romance", "Lãng mạn", Category.Group.GENRE, 20),
            seed(3, "mystery", "Bí ẩn", Category.Group.GENRE, 30),
            seed(4, "science-fiction", "Khoa học viễn tưởng",
                    Category.Group.GENRE, 40),
            seed(5, "ancient", "Cổ đại", Category.Group.SETTING, 10),
            seed(6, "modern", "Hiện đại", Category.Group.SETTING, 20),
            seed(7, "urban", "Đô thị", Category.Group.SETTING, 30),
            seed(8, "post-apocalyptic", "Hậu tận thế",
                    Category.Group.SETTING, 40),
            seed(9, "uplifting", "Chữa lành", Category.Group.TONE, 10),
            seed(10, "dark", "U tối", Category.Group.TONE, 20),
            seed(11, "humorous", "Hài hước", Category.Group.TONE, 30),
            seed(12, "suspenseful", "Kịch tính", Category.Group.TONE, 40),
            seed(13, "happy-ending", "Kết thúc có hậu",
                    Category.Group.ENDING, 10),
            seed(14, "bittersweet", "Buồn vui đan xen",
                    Category.Group.ENDING, 20),
            seed(15, "tragic", "Bi kịch", Category.Group.ENDING, 30),
            seed(16, "open-ending", "Kết thúc mở",
                    Category.Group.ENDING, 40),
            seed(17, "slow-burn", "Tình cảm chậm",
                    Category.Group.RELATIONSHIP, 10),
            seed(18, "rivals-to-lovers", "Từ đối đầu đến yêu",
                    Category.Group.RELATIONSHIP, 20),
            seed(19, "friends-to-lovers", "Từ bạn thành yêu",
                    Category.Group.RELATIONSHIP, 30),
            seed(20, "found-family", "Gia đình được chọn",
                    Category.Group.RELATIONSHIP, 40),
            seed(21, "original", "Sáng tác", Category.Group.FORMAT, 10),
            seed(22, "translated", "Chuyển ngữ",
                    Category.Group.FORMAT, 20),
            seed(23, "anthology", "Tuyển tập", Category.Group.FORMAT, 30),
            seed(24, "serial", "Dài kỳ", Category.Group.FORMAT, 40)
    );

    @Override
    public long version() {
        return 12;
    }

    @Override
    public String name() {
        return "seed grouped category taxonomy and indexes";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoCategoryDocument.COLLECTION)
                .createIndex(new Index()
                        .named("category_group_slug_unique")
                        .on("group", Sort.Direction.ASC)
                        .on("slug", Sort.Direction.ASC)
                        .unique());
        mongo.indexOps(MongoCategoryDocument.COLLECTION)
                .createIndex(new Index()
                        .named("category_active_group_order")
                        .on("active", Sort.Direction.ASC)
                        .on("groupOrder", Sort.Direction.ASC)
                        .on("sortOrder", Sort.Direction.ASC)
                        .on("slug", Sort.Direction.ASC));
        for (Seed seed : SEEDS) {
            mongo.upsert(
                    Query.query(new Criteria().andOperator(
                            Criteria.where("group").is(seed.group()),
                            Criteria.where("slug").is(seed.slug())
                    )),
                    new Update()
                            .setOnInsert("_id", seed.id())
                            .setOnInsert("group", seed.group())
                            .setOnInsert("slug", seed.slug())
                            .set("name", seed.name())
                            .set("groupOrder", seed.group().sortOrder())
                            .set("sortOrder", seed.sortOrder())
                            .set("active", true)
                            .set("version", 1L),
                    MongoCategoryDocument.COLLECTION
            );
        }
    }

    private static Seed seed(
            int id,
            String slug,
            String name,
            Category.Group group,
            int sortOrder
    ) {
        return new Seed(
                "10000000-0000-4000-8000-%012d".formatted(id),
                slug,
                name,
                group,
                sortOrder
        );
    }

    private record Seed(
            String id,
            String slug,
            String name,
            Category.Group group,
            int sortOrder
    ) {
    }
}
