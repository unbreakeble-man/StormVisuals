package dev.stormvisuals.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stormvisuals.keybind.Keybinds;
import dev.stormvisuals.keybind.PressKeyMapping;
import dev.stormvisuals.config.ConfigManager;
import dev.stormvisuals.config.ConfigState;
import dev.stormvisuals.render.MtsdfFont;
import dev.stormvisuals.module.Fullbright;
import dev.stormvisuals.module.Removals;
import dev.stormvisuals.module.CustomCrosshair;
import dev.stormvisuals.module.CustomHand;
import dev.stormvisuals.module.TargetESP;
import dev.stormvisuals.module.Zoom;
import dev.stormvisuals.module.ItemScroller;
import dev.stormvisuals.module.TntTimer;
import dev.stormvisuals.module.TotemBar;
import dev.stormvisuals.module.ChestSorter;
import dev.stormvisuals.module.CommandSafe;
import dev.stormvisuals.module.Auction;
import dev.stormvisuals.module.FuntimeQuests;
import dev.stormvisuals.render.BackdropBlurRenderer;
import dev.stormvisuals.render.PngIconRenderer;
import dev.stormvisuals.render.RoundedRectRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** In-game version of the 880x560 click GUI mockup. Fullbright is connected to client lighting. */
public final class ClickGuiScreen extends Screen {
    private static final int WIDTH = 880;
    private static final int HEIGHT = 560;
    private static final int HEADER = 54;
    private static final int SIDEBAR = 164;
    private static final int CONTENT_X = SIDEBAR + 16;
    private static final int CONTENT_Y = HEADER + 16;
    private static final int CARD_WIDTH = 336;
    private static final int CARD_GAP = 12;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int MUTED = 0x80FFFFFF;
    private static final int BLUE = 0xFF408CFF;
    private static final String[] CATEGORIES = {"Render", "World", "Utils", "Keybinds", "Configs", "Waypoints"};
    private static final Identifier LOGO = icon("logo");
    private static final Identifier SEARCH = icon("search");
    private static final Identifier DROPDOWN = icon("dropdown");
    private static final Identifier[] NAV_ICONS = {icon("render"), icon("world"), icon("utils"),
            icon("keybinds"), icon("configs"), icon("waypoints")};
    private static final String[] GROUPS = {"Визуал", "Утилиты", "Прочее"};
    private static final String[] FULLBRIGHT_MODES = {"Яркость", "Эффект", "Динамическое"};
    private static final String[] EFFECTS = {"Огнестойкость", "Скорость", "Сила"};
    private static final List<Module> MODULES = new ArrayList<>(createModules());

    // This method body can grow through standard HotSwap without changing the class schema.
    private static List<Module> createModules() {
        return List.of(
            new Module("Эффекты", "P", "Показывает выбранный эффект", "Render", true, true),
            new Module("TargetESP", "R", "Подсветка цели под прицелом", "Render", false, false),
            new Module("Removals", "", "Скрывает мешающие эффекты", "Render", false, false),
            new Module("Jumpcircles", "", "Круги при прыжке", "Render", true, false),
            new Module("FullBright", "", "Светлее в тёмных местах", "Render", false, false),
            new Module("CustomCrosshair", "", "Свой стиль прицела", "Render", false, false),
            new Module("CustomHand", "", "Водные волны на руке и предмете", "Render", false, false),
            new Module("Точки маршрута", "M", "Ориентиры и расстояние", "World", false, false),
            new Module("Zoom", "C", "Плавное приближение при удержании бинда", "Utils", false, false),
            new Module("Item Scroller", "", "Shift + ЛКМ: быстрое перемещение предметов", "Utils", false, false),
            new Module("TNT Timer", "", "Оценка времени до взрыва рядом с TNT", "Utils", false, false),
            new Module("Totem Bar", "", "Количество тотемов над хотбаром", "Utils", false, false),
            new Module("Chest Sorter", "", "Кнопка сортировки в интерфейсе сундука", "Utils", false, false),
            new Module("AucHelper", "", "Перевыставление и сортировка аукциона по цене", "Utils", false, false),
            new Module("Funtime Quests", "", "Квесты на *.funtime.su; локальный подсчёт со сверкой по серверу", "Utils", false, false),
            new Module("Command Safe", "", "Подтверждение опасных команд", "Utils", false, false)
        );
    }

    private static KeyMapping openKey;
    private final BackdropBlurRenderer backdrop = new BackdropBlurRenderer();
    private final List<Card> cards = new ArrayList<>(MODULES.size());
    private final Card[] allCards = new Card[MODULES.size()];
    private final Motion window = new Motion(0, 20);
    private final Motion content = new Motion(1, 32);
    private final Motion selectionY = new Motion(96, 24);
    private final Motion scrolling = new Motion(0, 28);
    private final Motion searchGlow = new Motion(0, 24);
    private final Motion[] navHover = new Motion[CATEGORIES.length];
    private String category = "Render";
    private String selectedCategory = "Render";
    private String pendingCategory;
    private String query = "";
    private float queryWidth;
    private boolean searchFocused;
    private boolean closing;
    private Screen nextScreen;
    private boolean focusCrosshairOnLayout;
    private boolean filterDirty = true;
    private float scale;
    private float originX;
    private float originY;
    private float drawScale;
    private float drawX;
    private float drawY;
    private float renderAlpha = 1;
    private float maxScroll;
    private long lastTyping = System.nanoTime();
    private float mouseLocalX;
    private float mouseLocalY;
    private Card draggingScale;
    private final KeybindsPanel keybinds;
    private final ConfigsPanel configs = new ConfigsPanel(this::refreshConfigCards);

    private ClickGuiScreen() {
        super(Component.literal("StormVisuals"));
        for (int i = 0; i < MODULES.size(); i++) {
            MODULES.get(i).refreshKeyLabel();
            allCards[i] = new Card(MODULES.get(i));
        }
        for (int i = 0; i < navHover.length; i++) navHover[i] = new Motion(0, 24);
        keybinds = new KeybindsPanel(MODULES.stream().map(module -> (KeyMapping) module.binding).toList(),
                binding -> MODULES.stream().filter(module -> module.binding == binding).forEach(Module::refreshKeyLabel));
        window.to(1);
    }

    public static void initialize() {
        BackdropBlurRenderer.initialize();
        List<Identifier> icons = new ArrayList<>(List.of(NAV_ICONS));
        icons.add(LOGO);
        icons.add(SEARCH);
        icons.add(DROPDOWN);
        icons.add(HudSettingsScreen.CLOSE_ICON);
        icons.addAll(dev.stormvisuals.render.WatermarkHud.icons());
        icons.addAll(dev.stormvisuals.render.PotionsHud.icons());
        icons.addAll(dev.stormvisuals.render.QuestsHud.icons());
        PngIconRenderer.initialize(List.copyOf(icons));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.stormvisuals.click_gui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("stormvisuals", "general"))));
        for (Module module : MODULES) {
            int defaultKey = module.key.isEmpty() ? GLFW.GLFW_KEY_UNKNOWN : module.key.charAt(0);
            module.binding = new PressKeyMapping(module.name, InputConstants.Type.KEYSYM, defaultKey, openKey.getCategory());
            KeyBindingHelper.registerKeyBinding(module.binding);
            if (module.name.equals("Zoom")) Zoom.configure(module.enabled, module.binding);
        }
        List<KeyMapping> persistentBindings = new ArrayList<>();
        persistentBindings.add(openKey);
        MODULES.forEach(module -> persistentBindings.add(module.binding));
        Keybinds.initialize(persistentBindings, openKey.getCategory());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            for (Module module : MODULES) {
                int presses = module.binding.takePresses();
                if (module.name.equals("Zoom")) continue;
                for (int i = 0; i < presses; i++) {
                    if (client.screen == null && client.player != null && client.level != null && client.isWindowActive()) toggleModule(module);
                }
            }
            while (openKey.consumeClick()) {
                if (client.screen instanceof ClickGuiScreen gui) {
                    if (!gui.keybinds.isBinding()) gui.onClose();
                } else if (client.screen == null && client.player != null) {
                    client.setScreen(new ClickGuiScreen());
                }
            }
        });
    }

    public static List<KeyMapping> activeHudBindings() {
        return MODULES.stream().filter(module -> module.binding != null && !module.binding.isUnbound()
                && (module.name.equals("Zoom") ? module.enabled && module.binding.isDown()
                : module.crosshair ? CustomCrosshair.settings().enabled : module.enabled))
                .map(module -> (KeyMapping) module.binding).toList();
    }

    /** Called between frames by LiveReload; existing modules, settings and bindings retain identity. */
    public static int refreshModules() {
        if (!dev.stormvisuals.dev.LiveReload.isApplying()) throw new IllegalStateException("Not applying a development reload");
        List<Module> definitions = createModules();
        if (definitions.size() > 64) throw new IllegalStateException("At most 64 modules are supported by the config format");
        var names = new java.util.HashSet<String>();
        for (Module definition : definitions) {
            if (!names.add(definition.name)) throw new IllegalStateException("Duplicate module: " + definition.name);
        }
        long additions = definitions.stream().filter(definition -> MODULES.stream()
                .noneMatch(existing -> existing.name.equals(definition.name))).count();
        if (MODULES.size() + additions > 64) throw new IllegalStateException("At most 64 registered modules are supported; restart to remove old cards");
        int added = 0;
        for (Module definition : definitions) {
            if (MODULES.stream().anyMatch(existing -> existing.name.equals(definition.name))) continue;
            int defaultKey = definition.key.isEmpty() ? GLFW.GLFW_KEY_UNKNOWN : definition.key.charAt(0);
            // Fabric forbids registering keys after Options construction. The vanilla constructor
            // registers input dispatch; our own keybind panel and config handle this live binding.
            definition.binding = new PressKeyMapping(definition.name, InputConstants.Type.KEYSYM, defaultKey, openKey.getCategory());
            MODULES.add(definition);
            configureUtility(definition);
            added++;
        }
        if (added > 0) {
            List<KeyMapping> bindings = new ArrayList<>();
            bindings.add(openKey);
            MODULES.forEach(module -> bindings.add(module.binding));
            Keybinds.updateFunctions(bindings);
            KeyMapping.resetMapping();
            ConfigManager.changed();
        }
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.screen instanceof ClickGuiScreen previous) {
            ClickGuiScreen refreshed = new ClickGuiScreen();
            refreshed.category = previous.category;
            refreshed.selectedCategory = previous.selectedCategory;
            refreshed.query = previous.query;
            refreshed.queryWidth = previous.queryWidth;
            client.setScreen(refreshed);
        }
        return added;
    }

    public static List<KeyMapping> hudBindings() {
        return MODULES.stream().filter(module -> module.binding != null && !module.binding.isUnbound())
                .map(module -> (KeyMapping) module.binding).toList();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // The custom backdrop is limited to the window and ignores the menu blur option.
    }

    @Override
    public void onClose() {
        if (closing) return;
        for (Card card : allCards) card.crosshairControls.finish();
        CustomCrosshair.flush();
        closing = true;
        searchFocused = false;
        draggingScale = null;
        keybinds.blur();
        window.to(0);
    }

    @Override
    public void tick() {
        // Release GPU resources before the next frame is extracted, never mid-draw.
        if (closing && window.update(System.nanoTime()) < 0.004F) minecraft.setScreen(nextScreen);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        updateDimensions(now);
        float opacity = Math.clamp(window.value, 0, 1);
        if (opacity < 0.001F) return;
        if (pendingCategory != null && content.update(now) < 0.035F) {
            category = pendingCategory;
            pendingCategory = null;
            filterDirty = true;
            scrolling.snap(0);
            content.snap(0);
            content.to(1);
        }
        if (filterDirty) filterCards();
        layoutCards(now);
        mouseLocalX = (mouseX - drawX) / drawScale;
        mouseLocalY = (mouseY - drawY) / drawScale;
        float pixelsX = minecraft.getMainRenderTarget().width / (float) width;
        float pixelsY = minecraft.getMainRenderTarget().height / (float) height;
        backdrop.prepare(minecraft.getMainRenderTarget(), originX * pixelsX, originY * pixelsY,
                WIDTH * scale * pixelsX, HEIGHT * scale * pixelsY, now);
        graphics.pose().pushMatrix();
        graphics.pose().translate(drawX, drawY);
        graphics.pose().scale(drawScale, drawScale);
        renderAlpha = opacity;
        for (int i = 12; i >= 1; i--) {
            float spread = i * 1.25F;
            rect(graphics, -spread, 3 - spread, WIDTH + spread * 2, HEIGHT + spread * 2,
                    32 + spread, 0x03000000);
        }
        backdrop.draw(graphics, WIDTH, HEIGHT, 32, opacity);
        rect(graphics, 0, 0, WIDTH, HEIGHT, 32, 0xC4000000);
        fill(graphics, 0, HEADER - 1, WIDTH, HEADER, 0x0FFFFFFF);
        fill(graphics, SIDEBAR - 1, HEADER, SIDEBAR, HEIGHT, 0x12FFFFFF);
        drawHeader(graphics, now);
        drawSidebar(graphics, now);
        graphics.enableScissor(SIDEBAR, HEADER, WIDTH, HEIGHT);
        float section = Math.clamp(content.update(now), 0, 1);
        renderAlpha = opacity * section;
        graphics.pose().pushMatrix();
        graphics.pose().translate((1 - section) * 10, 0);
        if (category.equals("Configs")) configs.render(graphics, mouseLocalX - (1 - section) * 10,
                mouseLocalY, renderAlpha, query);
        else if (category.equals("Keybinds")) keybinds.render(graphics, mouseLocalX - (1 - section) * 10,
                mouseLocalY, renderAlpha);
        else {
            for (Card card : cards) drawCard(graphics, card, now);
            if (cards.isEmpty()) label(graphics, "Нет модулей", SIDEBAR + 20, CONTENT_Y + 16, 13, MUTED);
        }
        graphics.pose().popMatrix();
        graphics.disableScissor();
        graphics.pose().popMatrix();
        renderAlpha = 1;
    }

    private void drawHeader(GuiGraphics graphics, long now) {
        PngIconRenderer.draw(graphics, LOGO, 19, 11, 32, color(BLUE));
        label(graphics, "StormVisuals", 52, 17, 18, WHITE);
        searchGlow.to(searchFocused || inside(mouseLocalX, mouseLocalY, 620, 11, 236, 32) ? 1 : 0);
        rect(graphics, 620, 11, 236, 32, 8, mix(0x0AFFFFFF, 0x16408CFF, searchGlow.update(now)));
        PngIconRenderer.draw(graphics, SEARCH, 632, 18, 18, color(MUTED));
        graphics.enableScissor(658, 12, 844, 43);
        float textX = 663 - Math.max(0, queryWidth - 172);
        label(graphics, query.isEmpty() ? "Поиск" : query, textX, 20, 12,
                query.isEmpty() ? 0x6BFFFFFF : WHITE);
        if (searchFocused) {
            double idle = (now - lastTyping) / 1_000_000_000.0;
            float caretAlpha = idle < 0.65 ? 1 : (float) (0.5 + 0.5 * Math.cos((idle - 0.65) * Math.PI * 2 / 1.1));
            rect(graphics, textX + queryWidth + 2, 19, 1.2F, 14, 0,
                    Math.round(caretAlpha * 255) << 24 | 0xFFFFFF);
        }
        graphics.disableScissor();
    }

    private void drawSidebar(GuiGraphics graphics, long now) {
        rect(graphics, 10, selectionY.update(now), SIDEBAR - 20, 36, 8, 0x38408CFF);
        for (int group = 0; group < 3; group++) {
            int headingY = headingY(group);
            if (group > 0) fill(graphics, 12, headingY - 12, SIDEBAR - 12, headingY - 11, 0x12FFFFFF);
            label(graphics, GROUPS[group], 22, headingY, 12, 0x6BFFFFFF);
            for (int i = 0; i < 2; i++) {
                int index = group * 2 + i;
                String name = CATEGORIES[index];
                int y = headingY + 24 + 42 * i;
                navHover[index].to(inside(mouseLocalX, mouseLocalY, 10, y, SIDEBAR - 20, 36) ? 1 : 0);
                float hover = navHover[index].update(now);
                if (hover > 0.005F) rect(graphics, 10, y, SIDEBAR - 20, 36, 8, Math.round(hover * 10) << 24 | 0xFFFFFF);
                float selected = Math.max(0, 1 - Math.abs(selectionY.value - y) / 36);
                PngIconRenderer.draw(graphics, NAV_ICONS[index], 22, y + 9, 18,
                        color(mix(MUTED, 0xFF78ADFF, selected)));
                label(graphics, name, 50, y + 11, 13, mix(0xBFFFFFFF, WHITE, selected));
            }
        }
    }

    private void drawCard(GuiGraphics graphics, Card card, long now) {
        Module module = card.module;
        if (module.crosshair) module.enabled = CustomCrosshair.settings().enabled;
        float x = card.x;
        float y = card.y - scrolling.value;
        if (y + card.height < HEADER || y >= HEIGHT) return;
        float localMouseX = mouseLocalX - (1 - Math.clamp(content.value, 0, 1)) * 10;
        boolean overDropdown = card.crosshairControls.overOpenDropdown(localMouseX, mouseLocalY)
                || card.dropdownOpen && inside(localMouseX, mouseLocalY, x + CARD_WIDTH - 164, y + 80,
                152, 28 + dropdownHeight(card));
        card.hover.to(!closing && pendingCategory == null && !overDropdown
                && inside(localMouseX, mouseLocalY, x, y, CARD_WIDTH, card.height) ? 1 : 0);
        float hover = card.hover.update(now);
        card.enabled.to(module.enabled ? 1 : 0);
        float enabled = card.enabled.update(now);
        rect(graphics, x, y, CARD_WIDTH, card.height, 12,
                mix(mix(0x0AFFFFFF, 0x12FFFFFF, hover), 0x22408CFF, enabled));
        label(graphics, module.name, x + 12, y + 16, 15, WHITE);
        if (keybinds.isBinding(module.binding) || !module.binding.isUnbound()) label(graphics, keybinds.isBinding(module.binding) ? "[...]" : module.keyLabel, x + 12 + module.nameWidth + 8, y + 19, 12, MUTED);
        drawSwitch(graphics, x + CARD_WIDTH - 46, y + 14, enabled);
        label(graphics, module.description, x + 12, y + 41, 12, MUTED);
        if (card.expansion.value < 0.002F || module.utility && card.crosshairControls.height() == 0) return;
        graphics.enableScissor((int) x, (int) (y + 70), (int) (x + CARD_WIDTH), (int) Math.ceil(y + card.height));
        float previousAlpha = renderAlpha;
        renderAlpha *= Math.clamp(card.expansion.value * 2, 0, 1);
        fill(graphics, x + 12, y + 70, x + CARD_WIDTH - 12, y + 71, 0x12FFFFFF);
        if (module.crosshair || module.customHand || module.utility) {
            card.crosshairControls.render(graphics,x+12,y+80,CARD_WIDTH-24,
                    mouseLocalX,mouseLocalY,renderAlpha);
            renderAlpha=previousAlpha;
            graphics.disableScissor();
            return;
        }
        float dropdownShift = dropdownHeight(card);
        if (module.effects) {
            label(graphics, "Эффект", x + 12, y + 89, 12, 0xCCFFFFFF);
            label(graphics, "Показывать таймер", x + 12, y + 127 + dropdownShift, 12, 0xCCFFFFFF);
            drawSwitch(graphics, x + CARD_WIDTH - 46, y + 125 + dropdownShift, card.timer.update(now));
            label(graphics, "Скрывать без эффекта", x + 12, y + 163 + dropdownShift, 12, 0xCCFFFFFF);
            drawSwitch(graphics, x + CARD_WIDTH - 46, y + 161 + dropdownShift, card.hide.update(now));
        }
        if (module.fullbright || module.removals || module.targetEsp) {
            label(graphics, module.targetEsp ? "Цели" : module.removals ? "Скрывать" : "Режим", x + 12, y + 89, 12, 0xCCFFFFFF);
            if (module.fullbright && module.effectIndex != 1) drawFullbrightSlider(graphics, card, x, y + dropdownShift, now);
            if (module.targetEsp) {
                card.crosshairControls.render(graphics, x + 12, y + 118 + dropdownShift, CARD_WIDTH - 24,
                        mouseLocalX - (1 - Math.clamp(content.value, 0, 1)) * 10, mouseLocalY, renderAlpha);
                float colorShift = dropdownShift + card.crosshairControls.height() - 32;
                label(graphics, "Цвет", x + 12, y + 165 + colorShift, 12, 0xCCFFFFFF);
                rect(graphics, x + CARD_WIDTH - 164, y + 156 + colorShift, 152, 28, 6, 0xFF202124);
                rect(graphics, x + CARD_WIDTH - 151, y + 163 + colorShift, 14, 14, 4, 0xFF000000 | TargetESP.color());
                label(graphics, "Настроить", x + CARD_WIDTH - 128, y + 165 + colorShift, 11, WHITE);
            }
            drawDropdown(graphics, card, x, y, now);
            renderAlpha = previousAlpha;
            graphics.disableScissor();
            return;
        }
        float labelY = module.effects ? y + 199 + dropdownShift : y + 89;
        float trackY = labelY + 26;
        label(graphics, "Масштаб", x + 12, labelY, 12, 0xCCFFFFFF);
        drawSliderValue(graphics, card.sizeLabel, x + CARD_WIDTH - 12, labelY);
        rect(graphics, x + 12, trackY, CARD_WIDTH - 24, 4, 2, 0x22FFFFFF);
        float filled = (CARD_WIDTH - 24) * (card.size.update(now) - 50) / 150;
        if (filled > 0) rect(graphics, x + 12, trackY, filled, 4, 2, BLUE);
        rect(graphics, x + 7 + filled, trackY - 4, 12, 12, 6, WHITE);
        if (module.effects) drawDropdown(graphics, card, x, y, now);
        renderAlpha = previousAlpha;
        graphics.disableScissor();
    }

    private void drawFullbrightSlider(GuiGraphics graphics, Card card, float x, float y, long now) {
        boolean brightness = card.module.effectIndex == 0;
        label(graphics, brightness ? "Сила яркости" : "Дальность свечения", x + 12, y + 124, 12, 0xCCFFFFFF);
        drawSliderValue(graphics, card.lightValueLabel, x + CARD_WIDTH - 12, y + 124);
        float value = brightness ? card.module.brightness : card.module.lightRadius / 10;
        card.light.to(value);
        float filled = (CARD_WIDTH - 24) * Math.clamp(card.light.update(now), 0, 1);
        rect(graphics, x + 12, y + 150, CARD_WIDTH - 24, 4, 2, 0x22FFFFFF);
        if (filled > 0) rect(graphics, x + 12, y + 150, filled, 4, 2, BLUE);
        rect(graphics, x + 7 + filled, y + 146, 12, 12, 6, WHITE);
    }
    private void drawSliderValue(GuiGraphics graphics, String text, float right, float y) {
        SliderValue.draw(graphics, text, right, y - 6, renderAlpha);
    }

    private void drawDropdown(GuiGraphics graphics, Card card, float x, float y, long now) {
        float listX = x + CARD_WIDTH - 164;
        float progress = Math.clamp(card.dropdown.value, 0, 1);
        String[] options = dropdownOptions(card.module);
        float listHeight = (4 + options.length * 28) * progress;
        float mouseX = mouseLocalX - (1 - Math.clamp(content.value, 0, 1)) * 10;
        card.dropdownHover.to(!card.dropdownOpen && progress < .001F && inside(mouseX, mouseLocalY, listX, y + 80, 152, 28) ? 1 : 0);
        // One continuous shape grows downward while later settings move by the same amount.
        rect(graphics, listX, y + 80, 152, 28 + listHeight, 6,
                mix(0xFF202124, 0xFF292A2D, card.dropdownOpen || progress > .001F ? 0 : card.dropdownHover.update(now)));
        label(graphics, card.module.targetEsp ? TargetESP.selectionLabel() : card.module.removals ? card.module.removalLabel : options[card.module.effectIndex], listX + 9, y + 88, 11, WHITE);
        PngIconRenderer.draw(graphics, DROPDOWN, (int) listX + 135, (int) y + 87, 14, color(MUTED));
        if (listHeight < 0.1F) return;
        graphics.enableScissor((int) Math.floor(listX), (int) Math.floor(y + 108),
                (int) Math.ceil(listX + 152), (int) Math.ceil(y + 108 + listHeight));
        float previousAlpha = renderAlpha;
        renderAlpha *= Math.clamp(progress * 2, 0, 1);
        for (int i = 0; i < dropdownOptions(card.module).length; i++) {
            float optionY = y + 112 + i * 28;
            card.optionHover[i].to(card.dropdownOpen && inside(mouseX, mouseLocalY, listX + 4, optionY, 144, 24) ? 1 : 0);
            float hover = card.optionHover[i].update(now);
            rect(graphics, listX + 4, optionY, 144, 24, 4,
                    mix(optionSelected(card.module, i) ? 0x30408CFF : 0x00FFFFFF,
                            optionSelected(card.module, i) ? 0x48408CFF : 0x16FFFFFF, hover));
            label(graphics, options[i], listX + 9, optionY + 7, 11, WHITE);
            if ((card.module.removals || card.module.targetEsp) && optionSelected(card.module, i)) rect(graphics, listX + 134, optionY + 9, 6, 6, 3, BLUE);
        }
        renderAlpha = previousAlpha;
        graphics.disableScissor();
    }

    private static float dropdownHeight(Card card) {
        return card.module.effects || card.module.fullbright || card.module.removals || card.module.targetEsp
                ? (4 + dropdownOptions(card.module).length * 28) * Math.clamp(card.dropdown.value, 0, 1) : 0;
    }

    private static void toggleModule(Module module) {
        if (module.crosshair) {
            CustomCrosshair.setEnabled(!CustomCrosshair.settings().enabled);
            module.enabled=CustomCrosshair.settings().enabled;
            return;
        }
        module.enabled = !module.enabled;
        if (module.fullbright) Fullbright.configure(module.enabled, module.effectIndex);
        if (module.removals) Removals.setEnabled(module.enabled);
        if (module.targetEsp) TargetESP.configure(module.enabled);
        if (module.customHand) CustomHand.setEnabled(module.enabled);
        configureUtility(module);
        ConfigManager.changed();
    }

    private static void configureUtility(Module module) {
        switch (module.name) {
            case "Zoom" -> Zoom.configure(module.enabled, module.binding);
            case "Item Scroller" -> ItemScroller.setEnabled(module.enabled);
            case "TNT Timer" -> TntTimer.setEnabled(module.enabled);
            case "Totem Bar" -> TotemBar.setEnabled(module.enabled);
            case "Chest Sorter" -> ChestSorter.setEnabled(module.enabled);
            case "AucHelper" -> Auction.setEnabled(module.enabled);
            case "Funtime Quests" -> FuntimeQuests.setEnabled(module.enabled);
            case "Command Safe" -> CommandSafe.setEnabled(module.enabled);
            default -> { }
        }
    }

    private static String[] dropdownOptions(Module module) {
        return module.targetEsp ? TargetESP.OPTIONS : module.removals ? Removals.OPTIONS : module.fullbright ? FULLBRIGHT_MODES : EFFECTS;
    }

    private void drawSwitch(GuiGraphics graphics, float x, float y, float progress) {
        progress = Math.clamp(progress, 0, 1);
        rect(graphics, x, y, 34, 20, 10, mix(0x16FFFFFF, BLUE, progress));
        rect(graphics, x + 3 + 14 * progress, y + 3, 14, 14, 7, WHITE);
    }

    private void updateDimensions(long now) {
        scale = Math.max(0.01F, Math.min(0.55F, Math.min(width * 0.60F / WIDTH, height * 0.60F / HEIGHT)));
        originX = (width - WIDTH * scale) * 0.5F;
        originY = (height - HEIGHT * scale) * 0.5F;
        float progress = Math.clamp(window.update(now), 0, 1);
        drawScale = scale * (0.95F + 0.05F * progress);
        drawX = (width - WIDTH * drawScale) * 0.5F;
        drawY = (height - HEIGHT * drawScale) * 0.5F + (1 - progress) * 10 * scale;
    }

    private void filterCards() {
        cards.clear();
        String filter = query.toLowerCase(Locale.ROOT);
        for (Card card : allCards) {
            Module module = card.module;
            if (!module.category.equals(category) && !(category.equals("Waypoints") && module.category.equals("World"))) continue;
            if (!module.filterName.contains(filter) && !module.filterDescription.contains(filter)) continue;
            card.x = CONTENT_X + (cards.size() % 2) * (CARD_WIDTH + CARD_GAP);
            cards.add(card);
        }
        filterDirty = false;
    }

    private void layoutCards(long now) {
        float leftY = CONTENT_Y;
        float rightY = CONTENT_Y;
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            refreshCrosshair(card);
            card.expansion.to(card.module.expanded ? 1 : 0);
            card.dropdown.to(card.dropdownOpen && card.module.expanded ? 1 : 0);
            card.dropdown.update(now);
            card.height = 70 + (((card.module.crosshair || card.module.customHand || card.module.utility) ? card.crosshairControls.height() + (card.module.utility && card.crosshairControls.height() == 0 ? 0 : 18) : card.module.removals ? 42 : card.module.targetEsp ? 94 + card.crosshairControls.height() : card.module.fullbright ? (card.module.effectIndex == 1 ? 50 : 100) : card.module.effects ? 176 : 72) + dropdownHeight(card)) * Math.clamp(card.expansion.update(now), 0, 1);
            card.y = i % 2 == 0 ? leftY : rightY;
            if (i % 2 == 0) leftY += card.height + CARD_GAP;
            else rightY += card.height + CARD_GAP;
        }
        maxScroll = Math.max(0, Math.max(leftY, rightY) - CARD_GAP + 16 - HEIGHT);
        if(focusCrosshairOnLayout){
            for(Card card:cards)if(card.module.crosshair)scrolling.snap(Math.clamp(card.y-CONTENT_Y,0,maxScroll));
            focusCrosshairOnLayout=false;
        }
        scrolling.to(Math.clamp(scrolling.target, 0, maxScroll));
        scrolling.update(now);
    }

    private static boolean optionSelected(Module module, int index) {
        return module.targetEsp ? TargetESP.selected(index) : module.removals ? Removals.selected(index) : index == module.effectIndex;
    }

    private void refreshCrosshair(Card card) {
        if(!card.module.crosshair || card.crosshairRevision==CustomCrosshair.revision())return;
        card.crosshairRevision=CustomCrosshair.revision();
        card.crosshairControls.clear();
        card.crosshairControls.preset(()->{
            onClose();
            nextScreen=new CrosshairEditorScreen();
        });
    }

    public static ClickGuiScreen openCrosshair() {
        for(Module module:MODULES)if(module.crosshair)module.expanded=true;
        ClickGuiScreen gui=new ClickGuiScreen();
        gui.focusCrosshairOnLayout=true;
        return gui;
    }

    private void selectCategory(String name, int navY) {
        if (name.equals(selectedCategory)) return;
        for(Card card:allCards){card.crosshairControls.finish();card.crosshairControls.cancel();}
        selectedCategory = name;
        keybinds.blur();
        configs.cancelInput();
        if (name.equals("Configs")) configs.refresh();
        pendingCategory = name;
        selectionY.to(navY);
        content.to(0);
        draggingScale = null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (keybinds.isBinding()) {
            keybinds.click(0, 0, event.button());
            return true;
        }
        if (closing || window.value < 0.6F) return true;
        for(Card card:allCards)card.crosshairControls.finish();
        long now = System.nanoTime();
        updateDimensions(now);
        if (filterDirty) filterCards();
        layoutCards(now);
        float x = (float) ((event.x() - drawX) / drawScale);
        float y = (float) ((event.y() - drawY) / drawScale);
        float contentOffset = (1 - Math.clamp(content.value, 0, 1)) * 10;
        if (category.equals("Configs") && configs.modalOpen())
            return configs.click(x - contentOffset, y, event.button());
        for(Card card:allCards)if((card.module.crosshair || card.module.targetEsp || card.module.customHand || card.module.utility) && (!cards.contains(card)
                || !inside(x-contentOffset,y,card.x,card.y-scrolling.value+70,CARD_WIDTH,card.height-70)))card.crosshairControls.cancel();
        for (Card card : allCards) {
            if (card.dropdownOpen && (!cards.contains(card) || !inside(x - contentOffset, y,
                    card.x + CARD_WIDTH - 164, card.y - scrolling.value + 80, 152,
                    28 + (4 + dropdownOptions(card.module).length * 28) * Math.clamp(card.dropdown.value, 0, 1)))) card.dropdownOpen = false;
        }
        if (!inside(x, y, 0, 0, WIDTH, HEIGHT)) return false;
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            searchFocused = inside(x, y, 620, 11, 236, 32);
            if (searchFocused) { keybinds.blur(); configs.blur(); lastTyping = now; return true; }
            for (int group = 0; group < 3; group++) {
                for (int i = 0; i < 2; i++) {
                    int navY = headingY(group) + 24 + 42 * i;
                    if (inside(x, y, 10, navY, SIDEBAR - 20, 36)) {
                        selectCategory(CATEGORIES[group * 2 + i], navY);
                        return true;
                    }
                }
            }
        }
        if (pendingCategory != null || content.value < 0.9F || x < SIDEBAR || y < CONTENT_Y || y >= HEIGHT - 16) return true;
        x -= (1 - Math.clamp(content.value, 0, 1)) * 10;
        if (category.equals("Configs")) return configs.click(x, y, event.button());
        if (category.equals("Keybinds")) return keybinds.click(x, y, event.button());
        ConfigManager.changed();
        for (Card card : cards) {
            float cardY = card.y - scrolling.value;
            if (!inside(x, y, card.x, cardY, CARD_WIDTH, card.height)) continue;
            Module module = card.module;
            float relativeY = y - cardY;
            if((module.crosshair || module.customHand || module.utility) && module.expanded && card.expansion.value>.95F && relativeY>=70) {
                card.crosshairControls.position(card.x+12,cardY+80,CARD_WIDTH-24);
                card.crosshairControls.click(x,y,event.button());
                return true;
            }
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                keybinds.beginBinding(module.binding);
                searchFocused = false;
                draggingScale = null;
                card.dropdownOpen = false;
                return true;
            }
            if ((module.effects || module.fullbright || module.removals || module.targetEsp) && module.expanded && card.expansion.value > 0.95F) {
                float listX = card.x + CARD_WIDTH - 164;
                boolean headerHit = inside(x, relativeY, listX, 80, 152, 28);
                boolean listHit = card.dropdown.value > 0.001F && inside(x, relativeY, listX, 108, 152, (4 + dropdownOptions(card.module).length * 28) * Math.clamp(card.dropdown.value, 0, 1));
                if (headerHit && (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT || event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
                    card.dropdownOpen = !card.dropdownOpen;
                    return true;
                }
                if (listHit) {
                    if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) card.dropdownOpen = false;
                    else if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && card.dropdownOpen && card.dropdown.value > 0.95F) {
                        for (int i = 0; i < dropdownOptions(card.module).length; i++) {
                            if (inside(x, relativeY, listX + 4, 112 + i * 28, 144, 24)) {
                                if (module.targetEsp) {
                                    TargetESP.toggleTarget(i);
                                    break;
                                }
                                if (module.removals) {
                                    Removals.toggle(i);
                                    module.removalLabel = "Выбрано: " + Removals.selectedCount();
                                    break;
                                }
                                module.effectIndex = i;

                                if (module.fullbright) {
                                    Fullbright.configure(module.enabled, i);
                                    card.lightValueLabel = String.format(Locale.ROOT, "%.2f", i == 0 ? module.brightness : module.lightRadius);
                                }
                                card.dropdownOpen = false;
                                break;
                            }
                        }
                    }
                    return true;
                }
            }
            if (module.targetEsp && module.expanded && card.expansion.value > 0.95F) {
                float controlsY = cardY + 118 + dropdownHeight(card);
                if (inside(x, y, card.x + 12, controlsY, CARD_WIDTH - 24, card.crosshairControls.height())) {
                    card.crosshairControls.position(card.x + 12, controlsY, CARD_WIDTH - 24);
                    card.crosshairControls.click(x, y, event.button());
                    return true;
                }
            }
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                module.expanded = !module.expanded;
                if (!module.expanded) card.dropdownOpen = false;
                card.expansion.to(module.expanded ? 1 : 0);
                return true;
            }
            if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
            if (relativeY < 70) {
                toggleModule(module);
                card.enabled.to(module.enabled ? 1 : 0);
                return true;
            }
            if (!module.expanded || card.expansion.value < 0.95F) return true;
            if (relativeY >= 108) relativeY -= dropdownHeight(card);
            if (module.effects) {
                if (relativeY >= 120 && relativeY < 149) { module.showTimer = !module.showTimer; card.timer.to(module.showTimer ? 1 : 0); return true; }
                if (relativeY >= 155 && relativeY < 185) { module.hideWithoutEffect = !module.hideWithoutEffect; card.hide.to(module.hideWithoutEffect ? 1 : 0); return true; }
            }
            if (module.fullbright) {
                if (module.effectIndex != 1 && relativeY >= 142 && relativeY <= 162) {
                    draggingScale = card;
                    updateSlider(card, x);
                }
                return true;
            }
            if (module.targetEsp) {
                if (inside(x, relativeY, card.x + CARD_WIDTH - 164, 124 + card.crosshairControls.height(), 152, 28))
                    minecraft.setScreen(new TargetEspColorScreen(this));
                return true;
            }
            if (module.removals) return true;
            int sliderY = module.effects ? 221 : 111;
            if (relativeY >= sliderY && relativeY <= sliderY + 18) {
                draggingScale = card;
                updateSlider(card, x);
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if(!closing){
            updateDimensions(System.nanoTime());
            float x=(float)((event.x()-drawX)/drawScale)-(1-Math.clamp(content.value,0,1))*10;
            float y=(float)((event.y()-drawY)/drawScale);
            for(Card card:allCards)if((card.module.crosshair || card.module.customHand || card.module.utility) && card.crosshairControls.drag(x,y))return true;
        }
        if (draggingScale == null || closing) return false;
        updateDimensions(System.nanoTime());
        updateSlider(draggingScale, (float) ((event.x() - drawX) / drawScale));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        for(Card card:allCards)if(card.crosshairControls.release())return true;
        if (draggingScale == null) return false;
        draggingScale = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (closing || pendingCategory != null) return false;
        updateDimensions(System.nanoTime());
        float localX = (float) ((x - drawX) / drawScale);
        float localY = (float) ((y - drawY) / drawScale);
        if (!inside(localX, localY, SIDEBAR, HEADER, WIDTH - SIDEBAR, HEIGHT - HEADER)) return false;
        if (category.equals("Configs")) return configs.scroll(vertical);
        if (category.equals("Keybinds")) return keybinds.scroll(vertical);
        scrolling.to(Math.clamp(scrolling.target - (float) vertical * 40, 0, maxScroll));
        return true;
    }

    private void updateSlider(Card card, float mouseX) {
        ConfigManager.changed();
        if (card.module.fullbright) {
            float value = Math.round(Math.clamp((mouseX - card.x - 12) / (CARD_WIDTH - 24), 0, 1) * 100) / 100F;
            if (card.module.effectIndex == 0) {
                card.module.brightness = value;
                Fullbright.setBrightness(value);
            } else {
                card.module.lightRadius = value * 10;
                Fullbright.setRadius(value * 10);
            }
            card.lightValueLabel = String.format(Locale.ROOT, "%.2f", card.module.effectIndex == 0 ? value : value * 10);
            return;
        }
        int size = Math.clamp(Math.round(50 + (mouseX - card.x - 12) * 150 / (CARD_WIDTH - 24)), 50, 200);
        if (card.module.size == size) return;
        card.module.size = size;
        card.size.to(size);
        card.sizeLabel = size + "%";
        card.sizeWidth = MtsdfFont.width(card.sizeLabel, 11);
    }

    private void queryChanged() {
        keybinds.filter(query);
        queryWidth = query.isEmpty() ? 0 : MtsdfFont.width(query, 12);
        lastTyping = System.nanoTime();
        filterDirty = true;
        scrolling.snap(0);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (category.equals("Configs") && configs.key(event)) return true;
        if (closing) return true;
        if (keybinds.isBinding() || category.equals("Keybinds") && !searchFocused) {
            if (keybinds.key(event)) return true;
        }
        if (event.isEscape()) {
            for(Card card:cards)if((card.module.crosshair || card.module.targetEsp || card.module.customHand || card.module.utility) && card.crosshairControls.closePopup())return true;
            for (Card card : allCards) {
                if (card.dropdownOpen) { card.dropdownOpen = false; return true; }
            }
        }
        if (event.isEscape() || openKey.matches(event)) { onClose(); return true; }
        if (closing) return true;
        for(Card card:cards)if((card.module.crosshair || card.module.customHand || card.module.utility) && card.module.expanded && card.crosshairControls.key(event))return true;
        if (!searchFocused) return super.keyPressed(event);
        if (event.key() == GLFW.GLFW_KEY_ENTER) { searchFocused = false; return true; }
        if (event.key() == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
            query = query.substring(0, query.offsetByCodePoints(query.length(), -1));
            queryChanged();
        } else if (event.isSelectAll()) {
            query = "";
            queryChanged();
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (category.equals("Configs") && configs.character(event)) return true;
        if (keybinds.isBinding() || category.equals("Keybinds") && !searchFocused) {
            if (keybinds.character(event)) return true;
        }
        for(Card card:cards)if((card.module.crosshair || card.module.customHand || card.module.utility) && card.module.expanded && card.crosshairControls.character(event))return true;
        if (!searchFocused || closing) return false;
        if (event.isAllowedChatCharacter() && query.codePointCount(0, query.length()) < 32) {
            query += event.codepointAsString();
            queryChanged();
        }
        return true;
    }

    @Override
    public void removed() {
        for(Card card:allCards)card.crosshairControls.finish();
        CustomCrosshair.flush();
        backdrop.close();
        super.removed();
    }

    private static int headingY(int group) {
        return switch (group) { case 0 -> 72; case 1 -> 198; default -> 324; };
    }

    public static void captureConfig(ConfigState state) {
        Keybinds.capture(state);
        state.guiKey = openKey.saveString();
        for (Module module : MODULES) {
            ConfigState.ModuleState saved = new ConfigState.ModuleState();
            saved.enabled = module.crosshair ? CustomCrosshair.settings().enabled : module.enabled;
            saved.expanded = module.expanded;
            saved.showTimer = module.showTimer;
            saved.hideWithoutEffect = module.hideWithoutEffect;
            saved.effectIndex = module.effectIndex;
            saved.size = module.size;
            saved.brightness = module.brightness;
            saved.lightRadius = module.lightRadius;
            if (module.customHand) {
                saved.bothHands = CustomHand.bothHands();
                saved.handSpeed = CustomHand.speed();
                saved.handOpacity = CustomHand.opacity();
            }
            saved.key = module.binding.saveString();
            switch (module.name) {
                case "Zoom" -> Zoom.capture(saved);
                case "Chest Sorter" -> ChestSorter.capture(saved);
                case "AucHelper" -> Auction.capture(saved);
                case "Funtime Quests" -> FuntimeQuests.capture(saved);
                case "TNT Timer" -> TntTimer.capture(saved);
                case "Totem Bar" -> TotemBar.capture(saved);
                case "Command Safe" -> CommandSafe.capture(saved);
                default -> { }
            }
            state.modules.put(module.name, saved);
        }
    }

    public static void validateConfig(ConfigState state) {
        Keybinds.validateConfig(state);
        if (state.guiKey != null) InputConstants.getKey(state.guiKey);
        for (ConfigState.ModuleState module : state.modules.values())
            if (module.key != null) InputConstants.getKey(module.key);
    }

    public static void applyConfig(ConfigState state) {
        for (Module module : MODULES) {
            ConfigState.ModuleState saved = state.modules.get(module.name);
            if (saved == null && module.name.equals("AucHelper")) saved = state.modules.get("Auction");
            if (saved == null) {
                saved = new ConfigState.ModuleState();
                saved.enabled = module.defaultEnabled;
                saved.effectIndex = module.targetEsp ? 1 : 0;
            }
            module.enabled = module.crosshair ? CustomCrosshair.settings().enabled : saved.enabled;
            module.expanded = saved.expanded;
            module.showTimer = saved.showTimer;
            module.hideWithoutEffect = saved.hideWithoutEffect;
            module.effectIndex = Math.clamp(saved.effectIndex, 0, dropdownOptions(module).length - 1);
            module.size = saved.size;
            module.brightness = saved.brightness;
            module.lightRadius = saved.lightRadius;
            if (saved.key != null) module.binding.setKey(InputConstants.getKey(saved.key));
            module.refreshKeyLabel();
            switch (module.name) {
                case "Zoom" -> Zoom.restore(saved);
                case "Chest Sorter" -> ChestSorter.restore(saved);
                case "AucHelper" -> Auction.restore(saved);
                case "Funtime Quests" -> FuntimeQuests.restore(saved);
                case "TNT Timer" -> TntTimer.restore(saved);
                case "Totem Bar" -> TotemBar.restore(saved);
                case "Command Safe" -> CommandSafe.restore(saved);
                default -> { }
            }
            configureUtility(module);
            if (module.fullbright) {
                Fullbright.setBrightness(module.brightness);
                Fullbright.setRadius(module.lightRadius);
                Fullbright.configure(module.enabled, module.effectIndex);
            }
            if (module.removals) {
                Removals.restore(module.enabled, state.removals);
                module.removalLabel = "Выбрано: " + Removals.selectedCount();
            }
            if (module.targetEsp) TargetESP.restore(module.enabled, state.targets, state.targetStyle, state.targetColor);
            if (module.customHand) {
                CustomHand.setBothHands(saved.bothHands);
                CustomHand.setSpeed(saved.handSpeed);
                CustomHand.setOpacity(saved.handOpacity);
                CustomHand.setEnabled(module.enabled);
            }
        }
        if (state.guiKey != null) openKey.setKey(InputConstants.getKey(state.guiKey));
        Keybinds.restore(state);
        KeyMapping.resetMapping();
    }

    private void refreshConfigCards() {
        draggingScale = null;
        keybinds.blur();
        for (int i = 0; i < allCards.length; i++) allCards[i] = new Card(MODULES.get(i));
        filterDirty = true;
    }

    private static Identifier icon(String name) {
        return Identifier.fromNamespaceAndPath("stormvisuals", "textures/gui/icons/" + name + ".png");
    }

    private static boolean inside(float x, float y, float left, float top, float width, float height) {
        return x >= left && y >= top && x < left + width && y < top + height;
    }

    private int color(int argb) {
        return Math.round((argb >>> 24) * Math.clamp(renderAlpha, 0, 1)) << 24 | argb & 0xFFFFFF;
    }

    private static int mix(int from, int to, float progress) {
        progress = Math.clamp(progress, 0, 1);
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = from >>> shift & 255;
            int b = to >>> shift & 255;
            result |= Math.round(a + (b - a) * progress) << shift;
        }
        return result;
    }

    private void rect(GuiGraphics graphics, float x, float y, float width, float height, float radius, int argb) {
        if (width <= 0 || height <= 0) return;
        RoundedRectRenderer.draw(graphics, new RoundedRectRenderer.Rect(x, y, width, height,
                radius, radius, radius, radius, color(argb)));
    }

    private void fill(GuiGraphics graphics, float x0, float y0, float x1, float y1, int argb) {
        rect(graphics, x0, y0, x1 - x0, y1 - y0, 0, argb);
    }

    private void label(GuiGraphics graphics, String text, float x, float y, float size, int argb) {
        MtsdfFont.draw(graphics, text, x, y, size, color(argb));
    }

    private static final class Card {
        final CrosshairControls crosshairControls;
        long crosshairRevision=-1;
        final Module module;
        final Motion expansion;
        final Motion enabled;
        final Motion timer;
        final Motion hide;
        final Motion size;
        final Motion light;
        final Motion hover = new Motion(0, 24);
        final Motion dropdown = new Motion(0, 26);
        final Motion dropdownHover = new Motion(0, 24);
        final Motion[] optionHover;
        boolean dropdownOpen;
        int x;
        float y;
        float height;
        String lightValueLabel = "1.00";
        String sizeLabel;
        float sizeWidth;

        Card(Module module) {
            this.module = module;
            crosshairControls = new CrosshairControls(() -> {}, () -> {}, module.customHand || module.utility ? ConfigManager::changed : module.targetEsp ? () -> {} : CustomCrosshair::changed);
            switch (module.name) {
                case "Zoom" -> {
                    crosshairControls.number("Сила приближения", 1.5f, 10, .25f, Zoom::strength, Zoom::strength);
                    crosshairControls.number("Скорость перехода", 4, 24, 1, Zoom::speed, Zoom::speed);
                }
                case "TNT Timer" -> crosshairControls.number("Дистанция отображения", 4, 96, 1, TntTimer::distance, TntTimer::distance);
                case "Totem Bar" -> crosshairControls.toggle("Скрывать при нуле", TotemBar::hideZero, TotemBar::hideZero);
                case "Chest Sorter" -> crosshairControls.choice("Режим сортировки", ChestSorter.MODES, ChestSorter::mode, ChestSorter::mode);
                case "AucHelper" -> {
                    crosshairControls.toggle("Автоперевыставление", Auction::autoRelist, Auction::autoRelist);
                    crosshairControls.toggle("Сортировка по цене", Auction::sortPrice, Auction::sortPrice);
                    crosshairControls.toggle("Отображать таймер", Auction::showTimer, Auction::showTimer);
                }
                case "Command Safe" -> {
                    for (int i = 0; i < CommandSafe.COMMANDS.length; i++) {
                        final int index = i;
                        crosshairControls.toggle("/" + CommandSafe.COMMANDS[i], () -> CommandSafe.protects(index), on -> CommandSafe.protect(index, on));
                    }
                }
                case "Funtime Quests" -> {
                    for (int i = 0; i < FuntimeQuests.DIFFICULTIES.length; i++) {
                        final int difficulty = i;
                        crosshairControls.toggle(FuntimeQuests.DIFFICULTIES[i], () -> FuntimeQuests.shows(difficulty),
                                on -> FuntimeQuests.show(difficulty, on));
                    }
                }
                default -> { }
            }
            if (module.targetEsp) crosshairControls.choice("Стиль", TargetESP.STYLES, TargetESP::style, TargetESP::setStyle);
            if (module.customHand) {
                crosshairControls.toggle("Обе руки", CustomHand::bothHands, CustomHand::setBothHands);
                crosshairControls.number("Скорость шейдера", 0, 5, 0.05F, CustomHand::speed, CustomHand::setSpeed);
                crosshairControls.number("Прозрачность шейдера", 0, 100, 1, CustomHand::opacity, CustomHand::setOpacity);
            }
            lightValueLabel = String.format(Locale.ROOT, "%.2f", module.effectIndex == 0 ? module.brightness : module.lightRadius);
            optionHover = new Motion[dropdownOptions(module).length];
            for (int i = 0; i < optionHover.length; i++) optionHover[i] = new Motion(0, 24);
            expansion = new Motion(module.expanded ? 1 : 0, 26);
            enabled = new Motion(module.enabled ? 1 : 0, 26);
            timer = new Motion(module.showTimer ? 1 : 0, 26);
            hide = new Motion(module.hideWithoutEffect ? 1 : 0, 26);
            size = new Motion(module.size, 32);
            light = new Motion(module.effectIndex == 0 ? module.brightness : module.lightRadius / 10, 32);
            sizeLabel = module.size + "%";
            sizeWidth = MtsdfFont.width(sizeLabel, 11);
        }
    }

    /** Exact critically damped spring: keeps velocity when reversed and is independent of FPS. */
    static final class Motion {
        float value;
        float target;
        double velocity;
        final double speed;
        long time = System.nanoTime();

        Motion(float value, double speed) { this.value = this.target = value; this.speed = speed; }
        void to(float target) { this.target = target; }
        void snap(float value) { this.value = this.target = value; velocity = 0; time = System.nanoTime(); }
        float update(long now) {
            double dt = Math.max(0, (now - time) / 1_000_000_000.0);
            time = now;
            if (value == target && velocity == 0) return value;
            double difference = value - target;
            double step = (velocity + speed * difference) * dt;
            double decay = Math.exp(-speed * dt);
            value = (float) (target + (difference + step) * decay);
            velocity = (velocity - speed * step) * decay;
            if (Math.abs(value - target) < 0.0005 && Math.abs(velocity) < 0.005) { value = target; velocity = 0; }
            return value;
        }
    }

    private static final class Module {
        final boolean crosshair;
        final boolean customHand;
        final boolean utility;
        final String name;
        final String key;
        final String description;
        final String category;
        final boolean effects;
        final boolean fullbright;
        final boolean removals;
        final boolean targetEsp;
        final boolean defaultEnabled;
        String removalLabel = "Выбрано: 0";
        final String filterName;
        final String filterDescription;
        String keyLabel;
        PressKeyMapping binding;
        final float nameWidth;
        boolean enabled;
        boolean expanded;
        boolean showTimer = true;
        boolean hideWithoutEffect = true;
        int effectIndex;
        float brightness = 1;
        float lightRadius = 10;
        int size = 100;

        void refreshKeyLabel() {
            keyLabel = binding == null || binding.isUnbound() ? "" : "[" + binding.getTranslatedKeyMessage().getString() + "]";
        }

        Module(String name, String key, String description, String category, boolean enabled, boolean effects) {
            this.crosshair=name.equals("CustomCrosshair");
            this.customHand=name.equals("CustomHand");
            this.utility=category.equals("Utils");
            this.name = name;
            this.key = key;
            this.description = description;
            this.category = category;
            this.enabled = enabled;
            this.defaultEnabled = enabled;
            this.effects = effects;
            this.fullbright = name.equals("FullBright");
            this.removals = name.equals("Removals");
            this.targetEsp = name.equals("TargetESP");
            filterName = name.toLowerCase(Locale.ROOT);
            filterDescription = description.toLowerCase(Locale.ROOT);
            keyLabel = key.isEmpty() ? "" : "[" + key + "]";
            nameWidth = MtsdfFont.width(name, 15);
        }
    }
}
