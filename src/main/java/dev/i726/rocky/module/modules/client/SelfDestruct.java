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

        private final StringSetting downloadURL = new StringSetting(EncryptedString.of("Replace URL"), "https://cdn.modrinth.com/data/5ZwdcRci/versions/FEOsWs1E/ImmediatelyFast-Fabric-1.2.11%2B1.20.4.jar");

        public SelfDestruct() {
                super(EncryptedString.of("Self Destruct"),
                EncryptedString.of("Removes all traces"),
                                -1,
                                CategoryManager.GUI);
                addSettings(replaceMod, saveLastModified, downloadURL);
        }

        @Override
        public void onEnable() {
                destruct = true;

                // Disable all GUI components first
                Rocky.INSTANCE.getModuleManager().getModule(ClickGUI.class).setEnabled(false);
                setEnabled(false);

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

                // Null out and disable everything
                for (Module module : Rocky.INSTANCE.getModuleManager().getModules()) {
                        module.setEnabled(false);
                        module.setName("");
                        module.setDescription("");
                        module.getSettings().clear();
                }

                if (saveLastModified.getValue()) {
                        Rocky.INSTANCE.resetModifiedDate();
                }

                // Run GC to clean up references
                System.gc();
                System.out.println("[Rocky] Self-destruct completed safely.");
        }
}