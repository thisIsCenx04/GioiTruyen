package com.storyplatform.quest.api;

import com.storyplatform.quest.application.QuestService;
import com.storyplatform.quest.application.dto.QuestDtos;
import com.storyplatform.shared.api.ApiException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quest management. The admin chooses from a fixed list of quest types - the
 * rule that measures each lives in the backend, so there is no free-form
 * scripting to get wrong.
 */
@RestController
@RequestMapping("/admin/quests")
public class AdminQuestController {

    private final JdbcClient jdbc;
    private final QuestService questService;

    public AdminQuestController(JdbcClient jdbc, QuestService questService) {
        this.jdbc = jdbc;
        this.questService = questService;
    }

    /** The quest types available, with a description of how each is measured. */
    @GetMapping("/types")
    public List<QuestDtos.QuestTypeOption> types() {
        return questService.questTypes();
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<QuestDtos.AdminQuestRow> list() {
        return jdbc.sql("""
                        SELECT id, quest_type, title, description, target_value,
                               reward_coin, reward_gem, sort_order, is_active
                        FROM quest_definitions
                        ORDER BY sort_order, title
                        """)
                .query((rs, rowNum) -> new QuestDtos.AdminQuestRow(
                        rs.getString("id"),
                        rs.getString("quest_type"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getInt("target_value"),
                        rs.getInt("reward_coin"),
                        rs.getInt("reward_gem"),
                        rs.getInt("sort_order"),
                        rs.getBoolean("is_active")))
                .list();
    }

    @PostMapping
    @Transactional
    public QuestDtos.AdminQuestRow create(@RequestBody QuestDtos.UpsertQuestRequest request) {
        String id = UUID.randomUUID().toString();
        Validated valid = validate(request);
        jdbc.sql("""
                        INSERT INTO quest_definitions
                            (id, quest_type, title, description, target_value,
                             reward_coin, reward_gem, sort_order, is_active)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, valid.questType(), valid.title(), valid.description(), valid.targetValue(),
                        valid.rewardCoin(), valid.rewardGem(), valid.sortOrder(), valid.active())
                .update();
        return find(id);
    }

    @PutMapping("/{id}")
    @Transactional
    public QuestDtos.AdminQuestRow update(
            @PathVariable String id,
            @RequestBody QuestDtos.UpsertQuestRequest request
    ) {
        find(id);
        Validated valid = validate(request);
        jdbc.sql("""
                        UPDATE quest_definitions
                        SET quest_type = ?, title = ?, description = ?, target_value = ?,
                            reward_coin = ?, reward_gem = ?, sort_order = ?, is_active = ?,
                            updated_at = NOW(3)
                        WHERE id = ?
                        """)
                .params(valid.questType(), valid.title(), valid.description(), valid.targetValue(),
                        valid.rewardCoin(), valid.rewardGem(), valid.sortOrder(), valid.active(), id)
                .update();
        return find(id);
    }

    /**
     * Deactivates rather than deletes: progress rows reference the quest, and a
     * reader who already earned a reward should keep that history.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public void deactivate(@PathVariable String id) {
        find(id);
        jdbc.sql("UPDATE quest_definitions SET is_active = FALSE, updated_at = NOW(3) WHERE id = ?")
                .param(id)
                .update();
    }

    private QuestDtos.AdminQuestRow find(String id) {
        return list().stream()
                .filter(row -> row.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "quest.not_found",
                        "Quest not found", "Không tìm thấy nhiệm vụ này."));
    }

    private Validated validate(QuestDtos.UpsertQuestRequest request) {
        String questType = request.questType() == null
                ? "" : request.questType().trim().toUpperCase(Locale.ROOT);
        boolean known = questService.questTypes().stream()
                .anyMatch(option -> option.value().equals(questType));
        if (!known) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "quest.invalid_type",
                    "Invalid quest type", "Hãy chọn một loại nhiệm vụ có sẵn.");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "quest.invalid_title",
                    "Missing title", "Chưa nhập tên nhiệm vụ.");
        }
        int target = request.targetValue() == null ? 1 : request.targetValue();
        if (target < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "quest.invalid_target",
                    "Invalid target", "Chỉ tiêu phải lớn hơn 0.");
        }
        int coin = request.rewardCoin() == null ? 0 : request.rewardCoin();
        int gem = request.rewardGem() == null ? 0 : request.rewardGem();
        if (coin < 0 || gem < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "quest.invalid_reward",
                    "Invalid reward", "Phần thưởng không được âm.");
        }
        return new Validated(
                questType,
                request.title().trim(),
                request.description() == null ? null : request.description().trim(),
                target, coin, gem,
                request.sortOrder() == null ? 0 : request.sortOrder(),
                request.active() == null || request.active());
    }

    private record Validated(
            String questType, String title, String description, int targetValue,
            int rewardCoin, int rewardGem, int sortOrder, boolean active) {
    }
}
