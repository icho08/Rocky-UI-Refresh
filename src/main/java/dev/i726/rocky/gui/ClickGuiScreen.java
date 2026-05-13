package dev.i726.rocky.gui;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.gui.components.CategoryPanel;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
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
    public boolean mouseClicked(Click click, boolean canDoubleClick) {
        double mouseX = click.x(), mouseY = click.y();
        int button = click.button();
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
        for (CategoryPanel panel : panels)
            if (panel.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!input.isValidChar()) return super.charTyped(input);
        char chr = (char) input.codepoint();
        int modifiers = input.modifiers();
        for (CategoryPanel panel : panels)
            if (panel.charTyped(chr, modifiers)) return true;
        return super.charTyped(input);
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
