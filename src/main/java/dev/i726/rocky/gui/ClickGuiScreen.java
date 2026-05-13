package dev.i726.rocky.gui;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.gui.components.CategoryPanel;
import dev.i726.rocky.managers.ProfileManager;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClickGuiScreen extends Screen {

    private static final List<CategoryPanel> panels = new ArrayList<>();
    private static boolean initialized = false;

    private static final int BAR_Y  = 5;
    private static final int BAR_H  = 28;
    private static final int SB_W   = 170;
    private static final int SB_PAD = 8;

    private String  searchQuery  = "";
    private boolean searchFocused = false;

    public ClickGuiScreen() {
        super(Text.literal("Rocky"));
    }

    @Override
    protected void init() {
        if (!initialized) {
            setupPanels();
            initialized = true;
        } else {
            for (CategoryPanel panel : panels)
                panel.clampToScreen(this.width, this.height);
        }
    }

    private void setupPanels() {
        panels.clear();
        List<Category> topLevel = Arrays.asList(
                CategoryManager.COMBAT,
                CategoryManager.PLAYER,
                CategoryManager.VISUAL,
                CategoryManager.MISC
        );
        ProfileManager pm = Rocky.INSTANCE.getProfileManager();
        int startX = Math.max(8, this.width / 2 - 295);
        int startY = 42;
        for (int i = 0; i < topLevel.size(); i++) {
            Category cat = topLevel.get(i);
            List<Module> catModules = getModulesForCategory(cat);
            if (!catModules.isEmpty()) {
                float px = startX + i * (CategoryPanel.WIDTH + 5);
                float py = startY;
                double[] saved = pm.getPanelPosition(cat.getName());
                if (saved != null) {
                    px = (float) saved[0];
                    py = (float) saved[1];
                }
                panels.add(new CategoryPanel(cat.getName(), catModules, px, py));
            }
        }
    }

    private List<Module> getModulesForCategory(Category cat) {
        List<Module> result = new ArrayList<>();
        for (Module m : Rocky.INSTANCE.moduleManager.getModules()) {
            Category mCat = m.getCategory();
            if (mCat == null) continue;
            if (mCat.equals(cat) || (mCat.getParent() != null && mCat.getParent().equals(cat)))
                result.add(m);
        }
        return result;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, GuiTheme.rgba(5, 4, 10, 175));

        renderTopBar(ctx, mouseX, mouseY);

        for (CategoryPanel panel : panels)
            panel.render(ctx, mouseX, mouseY, delta);
    }

    private void renderTopBar(DrawContext ctx, int mouseX, int mouseY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Color ac = GuiTheme.accent();
        int acInt = GuiTheme.accentInt();

        // ── Floating "Rocky Client" title — no background ───────────────────
        int ty = BAR_Y + 10;
        ctx.drawText(mc.textRenderer, "Rocky", 12, ty, acInt, true);
        int rockyW = mc.textRenderer.getWidth("Rocky");
        ctx.drawText(mc.textRenderer, " Client", 12 + rockyW, ty, GuiTheme.textPrimary(), true);
        int clientW = mc.textRenderer.getWidth(" Client");
        String ver = Rocky.INSTANCE.getVersion().trim();
        ctx.drawText(mc.textRenderer, "  " + ver,
                12 + rockyW + clientW, ty,
                GuiTheme.rgba(90, 86, 128, 255), true);

        // ── Search bar ──────────────────────────────────────────────────────
        int sbX = this.width - SB_W - SB_PAD;
        int sbY = BAR_Y + 4;
        int sbH = BAR_H - 8;

        boolean hoveringSearch = mouseX >= sbX && mouseX < sbX + SB_W
                              && mouseY >= sbY && mouseY < sbY + sbH;

        // Border: always accent-tinted, brighter when focused/hovered
        int borderAlpha = searchFocused ? 200 : hoveringSearch ? 110 : 60;
        ctx.fill(sbX - 1, sbY - 1, sbX + SB_W + 1, sbY + sbH + 1,
                GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), borderAlpha));
        // Background
        ctx.fill(sbX, sbY, sbX + SB_W, sbY + sbH, GuiTheme.rgba(8, 7, 14, 215));
        // Left accent sliver when focused
        if (searchFocused) {
            ctx.fill(sbX, sbY, sbX + 2, sbY + sbH, acInt);
        }

        // Search icon
        int iconColor = searchFocused ? acInt : GuiTheme.textSecondary();
        ctx.drawText(mc.textRenderer, "\u2315", sbX + 4, sbY + 4, iconColor, false);

        // Text / placeholder
        int textX = sbX + 16;
        int textY = sbY + 4;
        if (searchQuery.isEmpty() && !searchFocused) {
            ctx.drawText(mc.textRenderer, "Search modules...", textX, textY,
                    GuiTheme.textSecondary(), false);
        } else {
            ctx.enableScissor(textX, sbY, sbX + SB_W - 4, sbY + sbH);
            ctx.drawText(mc.textRenderer, searchQuery, textX, textY,
                    GuiTheme.textPrimary(), false);
            ctx.disableScissor();
            // Blinking cursor
            if (searchFocused && (System.currentTimeMillis() / 530) % 2 == 0) {
                int cursorX = textX + mc.textRenderer.getWidth(searchQuery);
                ctx.fill(cursorX, sbY + 3, cursorX + 1, sbY + sbH - 3, GuiTheme.textPrimary());
            }
        }
    }

    private int searchBarX()  { return this.width - SB_W - SB_PAD; }
    private int searchBarY()  { return BAR_Y + 4; }
    private int searchBarH()  { return BAR_H - 8; }

    private boolean isOverSearchBar(double mx, double my) {
        int sbX = searchBarX(), sbY = searchBarY(), sbH = searchBarH();
        return mx >= sbX && mx < sbX + SB_W && my >= sbY && my < sbY + sbH;
    }

    private void updateFilter() {
        for (CategoryPanel panel : panels)
            panel.setFilter(searchQuery);
    }

    @Override
    public boolean mouseClicked(Click click, boolean canDoubleClick) {
        double mouseX = click.x(), mouseY = click.y();
        int button = click.button();

        if (button == 0) {
            if (isOverSearchBar(mouseX, mouseY)) {
                searchFocused = true;
                return true;
            } else {
                searchFocused = false;
            }
        }

        for (int i = panels.size() - 1; i >= 0; i--)
            if (panels.get(i).mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(click, canDoubleClick);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        double mouseX = click.x(), mouseY = click.y();
        int button = click.button();
        for (CategoryPanel panel : panels)
            if (panel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mouseX = click.x(), mouseY = click.y();
        int button = click.button();
        for (CategoryPanel panel : panels) panel.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (CategoryPanel panel : panels)
            if (panel.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key(), scanCode = input.scancode(), modifiers = input.modifiers();

        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                    updateFilter();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = "";
                    updateFilter();
                } else {
                    searchFocused = false;
                }
                return true;
            }
            return true;
        }

        for (CategoryPanel panel : panels)
            if (panel.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!input.isValidChar()) return super.charTyped(input);
        char chr = (char) input.codepoint();
        int modifiers = input.modifiers();

        if (searchFocused) {
            searchQuery += chr;
            updateFilter();
            return true;
        }

        for (CategoryPanel panel : panels)
            if (panel.charTyped(chr, modifiers)) return true;
        return super.charTyped(input);
    }

    @Override
    public void removed() {
        try {
            ProfileManager pm = Rocky.INSTANCE.getProfileManager();
            for (CategoryPanel panel : panels) {
                pm.setPanelPosition(panel.getName(), panel.getX(), panel.getY());
            }
            pm.saveProfile("default");
        } catch (Exception e) {
            System.err.println("[Rocky] Failed to save profile: " + e.getMessage());
            e.printStackTrace();
        }
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public static void resetPanels() {
        initialized = false;
        panels.clear();
    }
}
