package dev.stormvisuals.gui;

import dev.stormvisuals.config.AccountsState;
import dev.stormvisuals.config.OfflineAccounts;
import dev.stormvisuals.render.MtsdfFont;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Always available from the title screen; offline profiles never enter the module registry. */
public final class AltManagerScreen extends Screen {
    private static final int W = 880, H = 560, LIST_X = 288, LIST_Y = 136, LIST_BOTTOM = 500;
    private final Screen parent;
    private final ClickGuiScreen.Motion opening = new ClickGuiScreen.Motion(0, 22);
    private final ClickGuiScreen.Motion scroll = new ClickGuiScreen.Motion(0, 28);
    private final List<AbstractWidget> form = new ArrayList<>();
    private final List<AccountCard> cards = new ArrayList<>();
    private StormEditBox search, nickname;
    private StormButton add, random;
    private long revision = -1;
    private boolean dirty = true;
    private float scale, originX, originY;
    private String message = "", menuAccount, lastQuery = "";
    private float menuX, menuY;

    public AltManagerScreen(Screen parent) { super(Component.literal("AltManager")); this.parent = parent; opening.to(1); }
    public static void initialize() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof TitleScreen)) return;
            // Top-right corner is outside the centered vanilla menu, even at GUI scale 4.
            Screens.getButtons(screen).add(new StormButton(Math.max(4, width - 114), 8, 106, 24,
                    "AltManager", () -> client.setScreen(new AltManagerScreen(screen))));
        });
    }
    @Override protected void init() {
        String query = search == null ? "" : search.getValue();
        String name = nickname == null ? "" : nickname.getValue();
        form.clear(); cards.clear(); menuAccount = null;
        search = new StormEditBox(LIST_X, 84, 568, 34, "Поиск аккаунтов");
        search.setMaxLength(32); search.setValue(query);
        lastQuery = query;
        search.setResponder(value -> {
            if (!lastQuery.equals(value)) { lastQuery = value; dirty = true; scroll.snap(0); menuAccount = null; }
        });
        form.add(addRenderableWidget(search));
        nickname = new StormEditBox(24, 296, 224, 34, "Никнейм");
        nickname.setMaxLength(16); nickname.setValue(name);
        form.add(addRenderableWidget(nickname));
        add = new StormButton(24, 342, 224, 32, "Добавить аккаунт", this::addAccount);
        form.add(addRenderableWidget(add));
        random = new StormButton(24, 386, 224, 32, "Случайный ник", () -> { nickname.setValue(OfflineAccounts.randomName()); setFocused(nickname); });
        form.add(addRenderableWidget(random));
        form.add(addRenderableWidget(new StormButton(24, 506, 224, 30, "Вернуться в главное меню", this::onClose)));
        dirty = true;
    }
    private void addAccount() {
        try { OfflineAccounts.add(nickname.getValue()); nickname.setValue(""); message = "Аккаунт добавлен"; dirty = true; }
        catch (IllegalArgumentException error) { message = error.getMessage(); }
    }
    private void select(String name) {
        try { OfflineAccounts.select(name); message = "Выбран " + name; }
        catch (IllegalArgumentException error) { message = error.getMessage(); }
    }
    private void refresh() {
        if (!dirty && revision == OfflineAccounts.revision()) return;
        for (AccountCard card : cards) removeWidget(card);
        cards.clear();
        String query = search.getValue().toLowerCase(Locale.ROOT);
        for (String name : OfflineAccounts.accounts()) if (name.toLowerCase(Locale.ROOT).contains(query)) {
            int i = cards.size();
            AccountCard card = new AccountCard(name, LIST_X + (i % 2) * 290, LIST_Y + (i / 2) * 116);
            cards.add(addRenderableWidget(card));
        }
        revision = OfflineAccounts.revision(); dirty = false;
        scroll.to(Math.clamp(scroll.target, 0, maxScroll()));
    }
    private float maxScroll() { return Math.max(0, ((cards.size() + 1) / 2) * 116 - 12 - (LIST_BOTTOM - LIST_Y)); }
    private void dimensions() {
        float progress = Math.clamp(opening.update(System.nanoTime()), 0, 1);
        scale = Math.max(.01f, Math.min(1, Math.min((width - 24f) / W, (height - 24f) / H))) * (.96f + .04f * progress);
        originX = (width - W * scale) / 2;
        originY = (height - H * scale) / 2 + (1 - progress) * 10 * scale;
    }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float delta) { }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        dimensions(); refresh();
        float mx = (mouseX - originX) / scale, my = (mouseY - originY) / scale;
        float offset = scroll.update(System.nanoTime());
        g.fillGradient(0, 0, width, height, 0xFF090F1B, 0xFF060911);
        g.pose().pushMatrix(); g.pose().translate(originX, originY); g.pose().scale(scale, scale);
        CrosshairControls.box(g, 0, 0, W, H, 24, 0xFF101824);
        CrosshairControls.box(g, 0, 0, 272, H, 24, 0xFF131E30);
        g.fill(272, 64, 273, H - 24, 0x15408CFF);
        MtsdfFont.draw(g, "StormVisuals", 24, 24, 19, 0xFFFFFFFF);
        MtsdfFont.draw(g, "AltManager", LIST_X, 23, 22, 0xFFFFFFFF);
        MtsdfFont.draw(g, "OFFLINE", 783, 28, 10, 0xFF79AEFF);
        g.fill(24, 62, W - 24, 63, 0x12FFFFFF);
        MtsdfFont.draw(g, "Текущий аккаунт", 24, 88, 12, 0xFF9AACC8);
        CrosshairControls.box(g, 24, 120, 224, 126, 14, 0x21408CFF);
        var current = minecraft.getUser();
        PlayerFaceRenderer.draw(g, net.minecraft.client.resources.DefaultPlayerSkin.get(current.getProfileId()), 40, 137, 44);
        MtsdfFont.draw(g, current.getName(), 96, 144, Math.min(16, 138 * 16 / Math.max(1, MtsdfFont.width(current.getName(), 16))), 0xFFFFFFFF);
        MtsdfFont.draw(g, "Активная сессия", 96, 168, 10, 0xFF9AACC8);
        CrosshairControls.box(g, 40, 207, 6, 6, 3, 0xFF88DBAC);
        MtsdfFont.draw(g, "Готов к игре", 54, 203, 11, 0xFF88DBAC);
        MtsdfFont.draw(g, "Добавить offline-профиль", 24, 270, 12, 0xFFCCD5E4);
        MtsdfFont.draw(g, "3–16 символов: A–Z, 0–9, _", 24, 432, 10, 0xFF7F91AE);
        MtsdfFont.draw(g, "Для локальных миров и offline-серверов", 24, 453, 9, 0xFF7F91AE);
        add.active = random.active = OfflineAccounts.ready();
        nickname.setEditable(OfflineAccounts.ready());
        for (AbstractWidget widget : form) widget.render(g, (int) mx, (int) my, delta);
        g.enableScissor(LIST_X - 2, LIST_Y, W - 20, LIST_BOTTOM);
        for (AccountCard card : cards) {
            card.setY(Math.round(card.baseY - offset));
            card.visible = card.getBottom() > LIST_Y && card.getY() < LIST_BOTTOM;
            card.active = OfflineAccounts.ready();
            if (card.visible) card.render(g, (int) mx, (int) my, delta);
        }
        g.disableScissor();
        if (cards.isEmpty()) {
            MtsdfFont.draw(g, OfflineAccounts.ready() ? "Аккаунты не найдены" : "Загрузка аккаунтов...", LIST_X + 20, 207, 17, 0xFFCCD5E4);
            MtsdfFont.draw(g, search.getValue().isEmpty() ? "Добавьте никнейм слева или создайте случайный." : "Измените поисковый запрос.",
                    LIST_X + 20, 239, 11, 0xFF7F91AE);
        }
        if (maxScroll() > 0) {
            float track = LIST_BOTTOM - LIST_Y;
            float bar = Math.max(24, track * track / (maxScroll() + track));
            CrosshairControls.box(g, W - 17, LIST_Y, 3, track, 1.5f, 0x16FFFFFF);
            CrosshairControls.box(g, W - 17, LIST_Y + (track - bar) * offset / maxScroll(), 3, bar, 1.5f, 0xFF408CFF);
        }
        String status = OfflineAccounts.error().isEmpty() ? message : OfflineAccounts.error();
        g.enableScissor(LIST_X, 508, W - 24, 549);
        MtsdfFont.draw(g, status.isEmpty() ? "Сохранено аккаунтов: " + OfflineAccounts.accounts().size() : status,
                LIST_X, 518, 11, OfflineAccounts.error().isEmpty() ? 0xFF9AACC8 : 0xFFFF7979);
        g.disableScissor();
        if (menuAccount != null) {
            CrosshairControls.box(g, menuX, menuY, 160, 76, 8, 0xFF24344D);
            String[] actions = {"Выбрать", "Скопировать ник", "Удалить"};
            for (int i = 0; i < actions.length; i++) {
                float top = menuY + 3 + i * 24;
                if (inside(mx, my, menuX + 3, top, 154, 23)) CrosshairControls.box(g, menuX + 3, top, 154, 23, 5, 0x30408CFF);
                MtsdfFont.draw(g, actions[i], menuX + 12, top + 6, 11, i == 2 ? 0xFFFF7979 : 0xFFFFFFFF);
            }
        }
        g.pose().popMatrix();
    }
    @Override public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        dimensions();
        float x = (float) ((e.x() - originX) / scale), y = (float) ((e.y() - originY) / scale);
        if (menuAccount != null) {
            String name = menuAccount; menuAccount = null;
            if (e.button() == 0 && inside(x, y, menuX, menuY + 3, 160, 72)) {
                int action = (int) ((y - menuY - 3) / 24);
                if (action == 0) select(name);
                if (action == 1) { minecraft.keyboardHandler.setClipboard(name); message = "Ник скопирован"; }
                if (action == 2) { OfflineAccounts.remove(name); message = "Аккаунт удалён"; dirty = true; }
                return true;
            }
        }
        if (opening.value < .8f) return true;
        if (x >= LIST_X && y >= LIST_Y && y < LIST_BOTTOM) {
            for (AccountCard card : cards) if (card.visible && card.isMouseOver(x, y)) {
                if (e.button() == 1 || inside(x, y, card.getX() + 242, card.getY() + 64, 22, 26)) {
                    menuAccount = card.name;
                    menuX = card.getX() + 112;
                    menuY = Math.clamp(card.getY() + 58, LIST_Y + 2, LIST_BOTTOM - 78);
                    setFocused(null);
                    return true;
                }
                if (e.button() == 0) { select(card.name); setFocused(card); return true; }
            }
            setFocused(null); return true;
        }
        // Partially clipped cards must never consume clicks outside the list viewport.
        if (x >= LIST_X && y >= LIST_BOTTOM) { setFocused(null); return true; }
        return super.mouseClicked(new MouseButtonEvent(x, y, e.buttonInfo()), doubleClick);
    }
    @Override public boolean mouseReleased(MouseButtonEvent e) {
        return super.mouseReleased(new MouseButtonEvent((e.x() - originX) / scale, (e.y() - originY) / scale, e.buttonInfo()));
    }
    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        return super.mouseDragged(new MouseButtonEvent((e.x() - originX) / scale, (e.y() - originY) / scale, e.buttonInfo()), dx / scale, dy / scale);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!inside((float) ((x - originX) / scale), (float) ((y - originY) / scale), LIST_X, LIST_Y, 568, LIST_BOTTOM - LIST_Y)) return false;
        menuAccount = null;
        scroll.to(Math.clamp(scroll.target - (float) vertical * 44, 0, maxScroll()));
        return true;
    }
    @Override public boolean keyPressed(KeyEvent e) {
        if (e.isEscape() && menuAccount != null) { menuAccount = null; return true; }
        if ((e.key() == GLFW.GLFW_KEY_ENTER || e.key() == GLFW.GLFW_KEY_KP_ENTER) && nickname.isFocused()) { addAccount(); return true; }
        return super.keyPressed(e);
    }
    @Override public boolean charTyped(CharacterEvent e) { return super.charTyped(e); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    private static boolean inside(float x, float y, float left, float top, float w, float h) {
        return x >= left && y >= top && x < left + w && y < top + h;
    }
    private final class AccountCard extends StormButton {
        final String name;
        final java.util.UUID uuid;
        final net.minecraft.world.entity.player.PlayerSkin skin;
        final int baseY;
        final ClickGuiScreen.Motion highlight = new ClickGuiScreen.Motion(0, 24);
        AccountCard(String name, int x, int y) {
            super(x, y, 278, 104, name, () -> select(name)); this.name = name; this.baseY = y;
            uuid = AccountsState.uuid(name); skin = net.minecraft.client.resources.DefaultPlayerSkin.get(uuid);
        }
        @Override protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
            boolean selected = minecraft.getUser().getProfileId().equals(uuid);
            highlight.to(selected || isHoveredOrFocused() ? 1 : 0);
            float h = highlight.update(System.nanoTime());
            CrosshairControls.box(g, getX(), getY(), getWidth(), getHeight(), 12, Math.round(10 + h * 40) << 24 | 0x408CFF);
            if (selected) CrosshairControls.box(g, getX() + 1, getY() + 15, 3, 74, 1.5f, 0xFF408CFF);
            PlayerFaceRenderer.draw(g, skin, getX() + 16, getY() + 16, 32);
            MtsdfFont.draw(g, name, getX() + 60, getY() + 17, 13, 0xFFFFFFFF);
            MtsdfFont.draw(g, "Offline-профиль", getX() + 60, getY() + 38, 10, 0xFF7F91AE);
            CrosshairControls.box(g, getX() + 16, getY() + 64, 214, 26, 6, selected ? 0x33408CFF : 0x18FFFFFF);
            String label = selected ? "Выбран" : "Выбрать аккаунт";
            MtsdfFont.draw(g, label, getX() + 123 - MtsdfFont.width(label, 11) / 2, getY() + 72, 11, selected ? 0xFF79AEFF : 0xFFCCD5E4);
            CrosshairControls.box(g, getX() + 242, getY() + 64, 22, 26, 6, 0x18FFFFFF);
            MtsdfFont.draw(g, "...", getX() + 247, getY() + 72, 12, 0xFFCCD5E4);
        }
    }
}
