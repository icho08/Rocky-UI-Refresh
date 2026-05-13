package dev.i726.rocky;

import dev.i726.rocky.auth.AuthManager;
import dev.i726.rocky.managers.ProfileManager;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.io.IOException;

public final class Main implements ClientModInitializer {

        private static final boolean RESET_SETTINGS_ON_STARTUP = false;

        @Override
        public void onInitializeClient() {
                AuthManager auth = new AuthManager();
                AuthManager.AuthResult result = auth.authenticate();

                if (!result.isAuthorized()) {
                        System.err.println("[Rocky] Authentication failed: " + result.getMessage());
                        System.err.println("[Rocky] Your HWID: " + auth.getHwid());
                        System.out.println("[Rocky] Bypassing auth - continuing initialization...");
                }

                System.out.println("[Rocky] Initialized successfully.");

                if (RESET_SETTINGS_ON_STARTUP) {
                        new ProfileManager().deleteProfile("default");
                }

                try {
                        new Rocky();
                } catch (InterruptedException | IOException ignored) {}
        }

        private void shutdownWithMessage(String reason) {
                System.err.println("[Rocky] Authentication Failed: " + reason);
        }
}
