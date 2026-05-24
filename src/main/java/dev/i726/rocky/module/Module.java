package dev.i726.rocky.module;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.EventManager;
import dev.i726.rocky.module.setting.Setting;

import net.minecraft.client.MinecraftClient;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public abstract class Module implements Serializable {
        private final List<Setting<?>> settings = new ArrayList<>();
        public final EventManager eventManager = Rocky.INSTANCE.eventManager;
        protected MinecraftClient mc;
        private CharSequence name;
        private CharSequence description;
        private boolean enabled;
        private int key;
        private Category category;

        // Modules in this set are considered "minor" (cosmetic / rarely-needed).
        // They can be hidden per-panel via the filter button in the GUI header.
        private static final Set<String> MINOR_NAMES = Set.of(
                "No Bounce", "Night Vision", "Hide Players", "Show Armor", "Show Health",
                "Anti AFK", "Auto Respawn", "Self Destruct", "Version Spoof",
                "Pack Spoof", "Ping Spoof", "Search", "Freecam"
        );

        public Module(CharSequence name, CharSequence description, int key, Category category) {
                this.name = name;
                this.description = description;
                this.enabled = false;
                this.key = key;
                this.category = category;
        }

        /** Returns true if this module is tagged as cosmetic/rarely-used. */
        public boolean isMinor() {
                return MINOR_NAMES.contains(name.toString());
        }

        public void toggle() {
                if (mc == null) mc = MinecraftClient.getInstance();
                if (Rocky.mc == null) Rocky.mc = MinecraftClient.getInstance();
                enabled = !enabled;
                System.out.println("[Rocky] Module " + getName() + " toggled: " + (enabled ? "ON" : "OFF"));
                if (enabled)
                        onEnable();
                else onDisable();
        }

        public CharSequence getName() {
                return name;
        }

        public boolean isEnabled() {
                return enabled;
        }

        public CharSequence getDescription() {
                return description;
        }

        public int getKey() {
                return key;
        }

        public Category getCategory() {
                return category;
        }

        public void setCategory(Category category) {
                this.category = category;
        }

        public void setName(CharSequence name) {
                this.name = name;
        }

        public void setDescription(CharSequence description) {
                this.description = description;
        }

        public void setKey(int key) {
                this.key = key;
        }

        public void clearKey() {
                this.key = -1;
        }

        public List<Setting<?>> getSettings() {
                return settings;
        }

        public void onEnable() {}

        public void onDisable() {}

        public void addSetting(Setting<?> setting) {
                this.settings.add(setting);
        }

        public void addSettings(Setting<?>... settings) {
                this.settings.addAll(Arrays.asList(settings));
        }

        public void setEnabled(boolean enabled) {
                if (mc == null) mc = MinecraftClient.getInstance();
                if (Rocky.mc == null) Rocky.mc = MinecraftClient.getInstance();
                if (this.enabled != enabled) {
                        System.out.println("[Rocky] Module " + getName() + " set to: " + (enabled ? "ON" : "OFF"));
                }
                this.enabled = enabled;
                if (enabled)
                        onEnable();
                else onDisable();
        }

        public void setEnabledStatus(boolean enabled) {
                this.enabled = enabled;
        }

}
