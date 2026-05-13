package dev.i726.rocky.gui.vape;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.gui.vape.components.ModuleButton;
import dev.i726.rocky.gui.vape.components.Panel;
import dev.i726.rocky.gui.vape.components.settings.KeybindSetting;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RockyGui extends Screen {

    private final List<Panel> panels = new ArrayList<>();
    private boolean[] mouseState = new boolean[3];
    private boolean[] keyState   = new boolean[512];
    private TextFieldWidget searchField;
    private String searchText = "";

    // ─── Layout ──────────────────────────────────────────────────────────────
    public  static final int PANEL_W   = 160;
    private static final int GAP_X     = 8;
    private static final int HEADER_H  = 30;     // top bar
    private static final int HOVER_H   = 20;     // description strip below header
    private static final int PANELS_Y  = HEADER_H + HOVER_H + 6; // where panels start
    private static final int SEARCH_W  = 180;
    private static final int SEARCH_H  = 14;

    private static final int[] POLL_KEYS = {
        GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_RIGHT_SHIFT, GLFW.GLFW_KEY_LEFT_SHIFT,
        GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D,
        GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_TAB
    };

    public RockyGui() { super(Text.literal("Rocky GUI")); }

    @Override
    protected void init() {
        panels.clear();

        int fieldX = width / 2 - SEARCH_W / 2;
        int fieldY = (HEADER_H - SEARCH_H) / 2;
        searchField = new TextFieldWidget(textRenderer, fieldX, fieldY, SEARCH_W, SEARCH_H,
                Text.literal("Search"));
        searchField.setPlaceholder(Text.literal("Search modules..."));
        searchField.setChangedListener(this::onSearchChanged);
        searchField.setDrawsBackground(false);
        addDrawableChild(searchField);

        rebuildPanels();
    }

    private void onSearchChanged(String text) {
        searchText = text.toLowerCase();
        rebuildPanels();
    }

    private void rebuildPanels() {
        panels.clear();
        int x = 10, y = PANELS_Y;

        for (Category cat : CategoryManager.getCategories().stream()
                .filter(Category::isSubcategory).collect(Collectors.toList())) {
            List<Module> mods = getFilteredModules(cat);
            if (mods.isEmpty()) continue;

            double[] pos = Rocky.INSTANCE.getProfileManager().getPanelPosition(cat.getName());
            if (pos != null) {
                panels.add(new Panel(cat, pos[0], pos[1], PANEL_W, searchText));
            } else {
                panels.add(new Panel(cat, x, y, PANEL_W, searchText));
                x += PANEL_W + GAP_X;
                if (x > width - PANEL_W - 10) { x = 10; y += 220; }
            }
        }
    }

    private List<Module> getFilteredModules(Category cat) {
        List<Module> mods = Rocky.INSTANCE.getModuleManager().getModulesInCategory(cat);
        if (searchText.isEmpty()) return mods;
        return mods.stream()
                .filter(m -> m.getName().toString().toLowerCase().contains(searchText)
                          || m.getDescription().toString().toLowerCase().contains(searchText))
                .collect(Collectors.toList());
    }

    @Override
    public void removed() {
        for (Panel p : panels)
            Rocky.INSTANCE.getProfileManager().setPanelPosition(
                    p.getCategory().getName(), p.getX(), p.getY());
        Rocky.INSTANCE.getProfileManager().saveProfile("default");
    }

    @Override public void renderBackground(DrawContext c, int mx, int my, float d) {}
    @Override public void renderInGameBackground(DrawContext c) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (Rocky.mc == null) Rocky.mc = MinecraftClient.getInstance();
        MinecraftClient mc = MinecraftClient.getInstance();

        // Reset hover every frame before panels render
        ModuleButton.hoveredModule = null;

        handlePollingInput(mc, mouseX, mouseY);

        // ── Background dim ─────────────────────────────────────────────────
        context.fill(0, 0, width, height, new Color(0, 0, 0, 145).getRGB());

        // ── Header bar — flat rectangle ────────────────────────────────────
        context.fill(0, 0, width, HEADER_H, new Color(12, 12, 12, 248).getRGB());
        // Bottom border of header (1px cyan)
        context.fill(0, HEADER_H - 1, width, HEADER_H, VapeTheme.ACCENT.getRGB());

        // ROCKY wordmark (with shadow for weight)
        int wy = (HEADER_H - 8) / 2;
        context.drawText(mc.textRenderer, "ROCKY", 12, wy, VapeTheme.ACCENT.getRGB(), true);

        // Search box — bordered rectangle, glows cyan when active
        int sfx = searchField.getX() - 6;
        int sfy = searchField.getY() - 3;
        int sfw = SEARCH_W + 12;
        int sfh = SEARCH_H + 6;
        boolean searching = !searchText.isEmpty();
        // Box fill
        context.fill(sfx, sfy, sfx + sfw, sfy + sfh,
                searching ? new Color(12, 28, 30, 235).getRGB() : new Color(18, 18, 18, 235).getRGB());
        // 4-sided border
        int searchBorder = searching ? new Color(34, 211, 238, 160).getRGB() : new Color(255, 255, 255, 32).getRGB();
        context.fill(sfx,           sfy,           sfx + sfw,     sfy + 1,     searchBorder); // top
        context.fill(sfx,           sfy + sfh - 1, sfx + sfw,     sfy + sfh,   searchBorder); // bottom
        context.fill(sfx,           sfy,           sfx + 1,       sfy + sfh,   searchBorder); // left
        context.fill(sfx + sfw - 1, sfy,           sfx + sfw,     sfy + sfh,   searchBorder); // right
        // Subtle inner glow when searching
        if (searching) {
            context.fill(sfx + 1, sfy + 1, sfx + sfw - 1, sfy + 2, new Color(34, 211, 238, 20).getRGB());
            context.fill(sfx + 1, sfy + sfh - 2, sfx + sfw - 1, sfy + sfh - 1, new Color(34, 211, 238, 12).getRGB());
        }
        // Search text / placeholder
        String displaySearch = searchText.isEmpty() ? "search modules..." : searchText;
        int searchTextColor = searchText.isEmpty() ? new Color(62, 62, 68).getRGB() : VapeTheme.TEXT_DIM.getRGB();
        context.drawText(mc.textRenderer, displaySearch, sfx + 6, sfy + (sfh - 8) / 2, searchTextColor, false);

        // Active count badge — right side of header
        long active = Rocky.INSTANCE.getModuleManager().getModules().stream()
                .filter(Module::isEnabled).count();
        String activeStr = active + " active";
        int aw  = mc.textRenderer.getWidth(activeStr);
        int abPad = 6;
        int abW = aw + abPad * 2;
        int abH = 13;
        int abX = width - abW - 10;
        int abY = (HEADER_H - abH) / 2;
        // Badge fill
        context.fill(abX, abY, abX + abW, abY + abH, new Color(34, 211, 238, 22).getRGB());
        // Badge border — top + bottom only for a subtle pill-band feel
        context.fill(abX, abY, abX + abW, abY + 1, new Color(34, 211, 238, 100).getRGB());
        context.fill(abX, abY + abH - 1, abX + abW, abY + abH, new Color(34, 211, 238, 60).getRGB());
        context.drawText(mc.textRenderer, activeStr, abX + abPad, abY + (abH - 8) / 2, VapeTheme.ACCENT.getRGB(), false);

        // Draw Minecraft widget (handles cursor blink etc.)
        super.render(context, mouseX, mouseY, delta);

        // ── Panels ─────────────────────────────────────────────────────────
        for (Panel panel : panels)
            panel.render(context, mouseX, mouseY, delta);

        // ── Hover description strip — drawn AFTER panels so it's on top ────
        // Background strip below header
        context.fill(0, HEADER_H, width, HEADER_H + HOVER_H, new Color(8, 8, 8, 220).getRGB());
        context.fill(0, HEADER_H + HOVER_H - 1, width, HEADER_H + HOVER_H,
                new Color(255, 255, 255, 8).getRGB());

        Module hm = ModuleButton.hoveredModule;
        if (hm != null) {
            int stripY = HEADER_H + (HOVER_H - 8) / 2;

            // Module name (cyan)
            String hmName = hm.getName().toString();
            context.drawText(mc.textRenderer, hmName, 12, stripY, VapeTheme.ACCENT.getRGB(), false);

            // Divider dot
            int dotX = 12 + mc.textRenderer.getWidth(hmName) + 5;
            context.drawText(mc.textRenderer, "—", dotX, stripY, new Color(45, 45, 45).getRGB(), false);

            // Description (muted)
            String desc = (hm.getDescription() != null) ? hm.getDescription().toString() : "";
            if (!desc.isEmpty()) {
                int descX = dotX + mc.textRenderer.getWidth("—") + 5;
                int maxDescW = width - descX - 80;
                if (mc.textRenderer.getWidth(desc) > maxDescW)
                    desc = mc.textRenderer.trimToWidth(desc, maxDescW - 4) + "..";
                context.drawText(mc.textRenderer, desc, descX, stripY,
                        new Color(110, 110, 115).getRGB(), false);
            }

            // Settings count — right side
            int nSettings = (int) hm.getSettings().stream().count();
            if (nSettings > 0) {
                String sStr = nSettings + " settings";
                int sw2 = mc.textRenderer.getWidth(sStr);
                context.drawText(mc.textRenderer, sStr, width - sw2 - 12, stripY,
                        new Color(60, 60, 65).getRGB(), false);
            }
        }
    }

    private void handlePollingInput(MinecraftClient mc, int mouseX, int mouseY) {
        long handle = mc.getWindow().getHandle();

        for (int i = 0; i < 2; i++) {
            boolean down = GLFW.glfwGetMouseButton(handle, i) == GLFW.GLFW_PRESS;
            if (down && !mouseState[i]) onMouseClick(mouseX, mouseY, i);
            else if (!down && mouseState[i]) onMouseRelease(mouseX, mouseY, i);
            mouseState[i] = down;
        }

        if (KeybindSetting.isAnyBinding) {
            for (int i = 32; i < 348; i++) pollKey(mc, handle, i);
        } else {
            for (int key : POLL_KEYS) pollKey(mc, handle, key);
        }
    }

    private void pollKey(MinecraftClient mc, long handle, int key) {
        if (key < 0 || key >= keyState.length) return;
        boolean down = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        if (down && !keyState[key]) {
            if (key == GLFW.GLFW_KEY_ESCAPE && !KeybindSetting.isAnyBinding)
                mc.execute(() -> mc.setScreen(null));
            else
                onKeyPress(key);
        }
        keyState[key] = down;
    }

    private void onMouseClick(double mx, double my, int button) {
        if (mx >= searchField.getX() && mx <= searchField.getX() + SEARCH_W
                && my >= searchField.getY() && my <= searchField.getY() + SEARCH_H) return;
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel p = panels.get(i);
            if (mx >= p.x && mx <= p.x + p.width && my >= p.y && my <= p.y + p.getTotalHeight()) {
                p.mouseClicked(mx, my, button);
                panels.remove(i);
                panels.add(p);
                return;
            }
        }
    }

    private void onMouseRelease(double mx, double my, int button) {
        for (Panel p : panels) p.mouseReleased(mx, my, button);
    }

    private void onKeyPress(int keyCode) {
        for (Panel panel : panels)
            for (ModuleButton btn : panel.getButtons())
                if (btn.onKey(keyCode)) return;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        return true; // panels are draggable so no global scroll needed
    }

    @Override
    public boolean shouldPause() { return false; }
}
