package dev.i726.rocky.module.modules.client;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.gui.ClickGuiScreen;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.*;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.Utils;

import java.io.File;

public final class SelfDestruct extends Module {
        public static boolean destruct = false;

        private final BooleanSetting replaceMod = new BooleanSetting(EncryptedString.of("Replace Mod"), true)
                        .setDescription(EncryptedString.of("Replaces the mod with a decoy JAR to hide it from screenshares"));

        private final BooleanSetting saveLastModified = new BooleanSetting(EncryptedString.of("Save Last Modified"), true)
                        .setDescription(EncryptedString.of("Saves the last modified date after self destruct"));

        private final BooleanSetting keepSettings = new BooleanSetting(EncryptedString.of("Keep Settings"), false)
                        .setDescription(EncryptedString.of("Saves your module config to disk before removing the mod"));

        private final StringSetting downloadURL = new StringSetting(EncryptedString.of("Replace URL"), "");

        public SelfDestruct() {
                super(EncryptedString.of("Self Destruct"),
                EncryptedString.of("Removes all traces"),
                                -1,
                                CategoryManager.GUI);
                addSettings(replaceMod, saveLastModified, keepSettings, downloadURL);
        }

        @Override
        public void onEnable() {
                destruct = true;

                // Disable all GUI components first
                Rocky.INSTANCE.getModuleManager().getModule(ClickGUI.class).setEnabled(false);
                setEnabled(false);

                // Always save settings to disk first so they can be restored later
                Rocky.INSTANCE.getProfileManager().saveProfile("default");

                if (mc.currentScreen instanceof ClickGuiScreen) {
                        Rocky.INSTANCE.guiInitialized = false;
                        mc.execute(() -> mc.setScreen(null));
                }

                // Replace JAR in background
                if (replaceMod.getValue()) {
                        new Thread(() -> {
                                try {
                                        String modUrl = downloadURL.getValue();
                                        File currentJar = Utils.getCurrentJarPath();
                                        if (currentJar.exists()) {
                                                Utils.replaceModFile(modUrl, currentJar);
                                        }
                                } catch (Exception ignored) {}
                        }).start();
                }

                // If not keeping settings, wipe all module data from memory
                if (!keepSettings.getValue()) {
                        for (Module module : Rocky.INSTANCE.getModuleManager().getModules()) {
                                module.setEnabled(false);
                                module.setName("");
                                module.setDescription("");
                                module.getSettings().clear();
                        }
                } else {
                        for (Module module : Rocky.INSTANCE.getModuleManager().getModules()) {
                                module.setEnabled(false);
                        }
                }

                if (saveLastModified.getValue()) {
                        Rocky.INSTANCE.resetModifiedDate();
                }

                // Run GC to clean up references
                System.gc();
        }
}