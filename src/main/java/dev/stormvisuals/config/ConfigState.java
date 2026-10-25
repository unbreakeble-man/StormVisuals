package dev.stormvisuals.config;

import dev.stormvisuals.module.CrosshairSettings;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/** Only user settings belong here; world entities, animations and editor history are transient. */
public final class ConfigState {
    public int version = 1;
    public Map<String, ModuleState> modules = new LinkedHashMap<>();
    public String guiKey;
    public List<CommandBind> commandBinds = new ArrayList<>();
    public CrosshairSettings crosshair = new CrosshairSettings();
    public int removals;
    public int targets = 2, targetStyle, targetColor = 0xFF1F6E;
    public Position watermark = new Position(), potions = new Position();
    public Position cooldowns = new Position(), hotkeys = new Position();
    public Position quests = new Position();
    public Position auctionTimer = new Position();

    public static final class ModuleState {
        public boolean enabled, expanded;
        public boolean showTimer = true, hideWithoutEffect = true;
        public int effectIndex, size = 100;
        public float brightness = 1, lightRadius = 10;
        public boolean bothHands = true;
        public float handSpeed = 1, handOpacity = 50;
        public String key;
        public float zoomStrength = 4, zoomSpeed = 12, tntDistance = 32;
        public boolean hideZero;
        public int chestSortMode;
        public boolean auctionAutoRelist, auctionSortPrice, auctionShowTimer;
        public int protectedCommands = 127;
        public int questDifficulties = 7;

        public void validate() {
            effectIndex = Math.clamp(effectIndex, 0, 4);
            size = Math.clamp(size, 50, 200);
            brightness = CrosshairSettings.limit(brightness, 0, 1, 1);
            lightRadius = CrosshairSettings.limit(lightRadius, 0, 10, 10);
            handSpeed = CrosshairSettings.limit(handSpeed, 0, 5, 1);
            handOpacity = CrosshairSettings.limit(handOpacity, 0, 100, 50);
            zoomStrength = CrosshairSettings.limit(zoomStrength, 1.5f, 10, 4);
            zoomSpeed = CrosshairSettings.limit(zoomSpeed, 4, 24, 12);
            tntDistance = CrosshairSettings.limit(tntDistance, 4, 96, 32);
            chestSortMode = Math.clamp(chestSortMode, 0, 2);
            protectedCommands &= 127;
            questDifficulties &= 7;
            if (key != null && key.length() > 80) throw new IllegalArgumentException("Некорректная клавиша");
        }
    }

    public static final class Position {
        public boolean moved;
        public boolean centerX, centerY;
        public float x, y;
        public float scale = 1, cornerRadius = -1;
        public List<String> favorites = new ArrayList<>();
        public void validate() {
            x = CrosshairSettings.limit(x, 0, 1, 0);
            y = CrosshairSettings.limit(y, 0, 1, 0);
            scale = CrosshairSettings.limit(scale, .5f, 2.5f, 1);
            cornerRadius = CrosshairSettings.limit(cornerRadius, -1, 22, -1);
            if (favorites == null) favorites = new ArrayList<>();
            if (favorites.size() > 256 || favorites.stream().anyMatch(key -> key == null || key.isBlank() || key.length() > 256))
                throw new IllegalArgumentException("Некорректное избранное HUD");
            favorites = new ArrayList<>(new java.util.LinkedHashSet<>(favorites));
        }
    }

    public static final class CommandBind {
        public String command, key;
    }

    public void validate() {
        if (version != 1) throw new IllegalArgumentException("Неподдерживаемая версия конфига");
        if (modules == null || modules.size() > 64 || modules.containsValue(null))
            throw new IllegalArgumentException("Некорректные настройки модулей");
        modules.values().forEach(ModuleState::validate);
        if (guiKey != null && guiKey.length() > 80) throw new IllegalArgumentException("Некорректная клавиша GUI");
        if (commandBinds == null || commandBinds.size() > 128 || commandBinds.stream().anyMatch(bind ->
                bind == null || bind.command == null || bind.command.isBlank() || bind.command.length() > 256
                        || bind.key == null || bind.key.length() > 80))
            throw new IllegalArgumentException("Некорректные бинды команд");
        if (crosshair == null || watermark == null || potions == null)
            throw new IllegalArgumentException("Неполный конфиг");
        crosshair.validate();
        removals &= 31;
        targets &= 15;
        targetStyle = Math.clamp(targetStyle, 0, 3);
        targetColor &= 0xFFFFFF;
        watermark.validate();
        potions.validate();
        if (cooldowns == null) cooldowns = new Position();
        if (hotkeys == null) hotkeys = new Position();
        cooldowns.validate();
        hotkeys.validate();
        if (quests == null) quests = new Position();
        quests.validate();
        if (auctionTimer == null) auctionTimer = new Position();
        auctionTimer.validate();
    }
}
