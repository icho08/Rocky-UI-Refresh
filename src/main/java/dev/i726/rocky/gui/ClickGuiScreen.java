package dev.i726.rocky.gui;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.gui.components.CategoryPanel;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClickGuiScreen extends Screen {

    private static final List<CategoryPanel> panels = new ArrayList<>();
    private static boolean initialized = false;

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
        int startX = Math.max(8, this.width / 2 - 295);
        int startY = 40;
        for (int i = 0; i < topLevel.size(); i++) {
            Category cat = topLevel.get(i);
            List<Module> catModules = getModulesForCategory(cat);
            if (!catModules.isEmpty()) {
                panels.add(new CategoryPanel(cat.getName(), catModules,
                        startX + i * (CategoryPanel.WIDTH + 5), startY));
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, GuiTheme.rgba(5, 4, 10, 175));

        for (CategoryPanel panel : panels)
            panel.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = panels.size() - 1; i >= 0; i--)
            if (panels.get(i).mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        for (CategoryPanel panel : panels)
            if (panel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (CategoryPanel panel : panels) panel.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (CategoryPanel panel : panels)
            if (panel.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (CategoryPanel panel : panels)
            if (panel.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        for (CategoryPanel panel : panels)
            if (panel.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
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
