package dev.stormvisuals.render;

import dev.stormvisuals.module.FuntimeQuests;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class QuestsHud {
    private static final List<Identifier> DIFFICULTY_ICONS = List.of(
            Identifier.fromNamespaceAndPath("stormvisuals", "textures/gui/icons/quest_easy.png"),
            Identifier.fromNamespaceAndPath("stormvisuals", "textures/gui/icons/quest_medium.png"),
            Identifier.fromNamespaceAndPath("stormvisuals", "textures/gui/icons/quest_hard.png"));
    private static final HudListPanel PANEL = new HudListPanel("Квесты Funtime", "Нет активных заданий",
            Identifier.fromNamespaceAndPath("stormvisuals", "textures/gui/icons/waypoints.png"), HudDragging.QUESTS);
    private static long nextUpdate;
    private static long lastRevision = -1;

    private QuestsHud() {}
    public static List<Identifier> icons() { return DIFFICULTY_ICONS; }

    public static void initialize() {
        PANEL.initialize();
        HudRenderSystem.addLayer((graphics, delta) -> {
            if (FuntimeQuests.active(Minecraft.getInstance())) PANEL.render(graphics, .56f);
            else HudDragging.QUESTS.hide();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long now = System.nanoTime();
            if (!FuntimeQuests.active(client)) { PANEL.rows(List.of()); nextUpdate = 0; return; }
            if (now < nextUpdate && lastRevision == FuntimeQuests.revision()) return;
            lastRevision = FuntimeQuests.revision();
            nextUpdate = now + 1_000_000_000L;
            List<HudListPanel.Row> rows = new ArrayList<>();
            var quests = FuntimeQuests.quests();
            int order = 0;
            for (int difficulty = 0; difficulty < FuntimeQuests.DIFFICULTIES.length; difficulty++) {
                if (!FuntimeQuests.shows(difficulty)) continue;
                final int category = difficulty;
                var group = quests.stream().filter(entry -> entry.quest().difficulty() == category && entry.expiresAt() > now).toList();
                if (group.isEmpty()) continue;
                long expiresAt = group.stream().mapToLong(FuntimeQuests.Observed::expiresAt).min().orElseThrow();
                long seconds = Math.max(0, (expiresAt - now) / 1_000_000_000L);
                rows.add(HudListPanel.Row.textured("category:" + difficulty, FuntimeQuests.DIFFICULTIES[difficulty],
                        time(seconds), seconds < 300, DIFFICULTY_ICONS.get(difficulty), order++));
                for (var entry : group) {
                    var quest = entry.quest();
                    rows.add(new HudListPanel.Row(quest.key(), quest.name(),
                            entry.current() + " / " + quest.total(), false, null, ItemStack.EMPTY, order++));
                }
            }
            if (rows.isEmpty()) rows.add(new HudListPanel.Row("hint", "Нет активных заданий", "", false, null, ItemStack.EMPTY, order));
            PANEL.rows(rows);
        });
    }

    private static String time(long seconds) {
        if (seconds >= 86400) return seconds / 86400 + "д " + seconds % 86400 / 3600 + "ч " + seconds % 3600 / 60 + "м";
        if (seconds >= 3600) return seconds / 3600 + "ч " + seconds % 3600 / 60 + "м";
        if (seconds >= 60) return seconds / 60 + "м " + seconds % 60 + "с";
        return seconds + "с";
    }
}
