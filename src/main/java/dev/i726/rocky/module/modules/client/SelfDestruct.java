package dev.i726.rocky.module.modules.client;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.events.ButtonListener;
import dev.i726.rocky.gui.ClickGuiScreen;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.*;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.Utils;
import org.lwjgl.glfw.GLFW;

import java.io.File;

public final class SelfDestruct extends Module implements ButtonListener {
        public static boolean destruct = false;

        private final KeybindSetting panicKey = new KeybindSetting(
                        EncryptedString.of("Panic Key"), GLFW.GLFW_KEY_END, false)
                        .setDescription(EncryptedString.of("Press this key anywhere to instantly trigger Self Destruct"));

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
                addSettings(panicKey, replaceMod, saveLastModified, keepSettings, downloadURL);
                // Register panic key listener permanently so it works without enabling the module
                Rocky.INSTANCE.getEventManager().add(ButtonListener.class, this);
        }

        @Override
        public void onButtonPress(ButtonEvent event) {
                if (event.action != GLFW.GLFW_PRESS) return;
                if (destruct) return;
                int pk = panicKey.getKey();
                if (pk != -1 && pk == event.button) {
                        triggerDestruct();
                }
        }

        @Override
        public void onEnable() {
                triggerDestruct();
        }

        private void triggerDestruct() {
                destruct = true;

                Rocky.INSTANCE.getModuleManager().getModule(ClickGUI.class).setEnabled(false);
                setEnabledStatus(false);

                Rocky.INSTANCE.getProfileManager().saveProfile("default");

                if (mc.currentScreen instanceof ClickGuiScreen) {
                        Rocky.INSTANCE.guiInitialized = false;
                        mc.execute(() -> mc.setScreen(null));
                }

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

                System.gc();
        }
}
