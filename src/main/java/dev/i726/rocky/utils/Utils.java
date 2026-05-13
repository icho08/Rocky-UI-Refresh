package dev.i726.rocky.utils;
import dev.i726.rocky.gui.Theme;

import dev.i726.rocky.module.modules.client.ClickGUI;
import dev.i726.rocky.module.modules.client.SelfDestruct;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;

import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import static dev.i726.rocky.Rocky.mc;

public final class Utils {

	public static Color getMainColor(int alpha, int increment) {
		int red = Theme.red;
		int green = Theme.green;
		int blue = Theme.blue;

		if (Theme.rainbow) {
			return ColorUtils.getBreathingRGBColor(increment, alpha);
		} else {
			if (Theme.breathing) {
				return ColorUtils.getMainColor(new Color(red, green, blue, alpha), increment, 20);
			} else {
				return new Color(red, green, blue, alpha);
			}
		}
	}

	public static int getPing(Entity player) {
		if (mc.getNetworkHandler().getConnection() == null) return 0;

		PlayerListEntry playerListEntry = mc.getNetworkHandler().getPlayerListEntry((player.getUuid()));
		if (playerListEntry == null) return 0;
		return playerListEntry.getLatency();
	}

	public static File getCurrentJarPath() throws URISyntaxException {
		return new File(SelfDestruct.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
	}

	public static void doDestruct() {
		try {
			String modUrl = "https://cdn.modrinth.com/data/5ZwdcRci/versions/FEOsWs1E/ImmediatelyFast-Fabric-1.2.11%2B1.20.4.jar";
			File currentJar = Utils.getCurrentJarPath();
			if (currentJar.exists()) {
				try {
					replaceModFile(modUrl, currentJar);
				} catch (IOException e) {
					// e.printStackTrace();
				}
			}
		} catch (Exception e) {
			// e.printStackTrace();
		}
	}

	public static void replaceModFile(String downloadURL, File savePath) throws IOException {
		URL url = URI.create(downloadURL).toURL();
		HttpURLConnection httpConnection = (HttpURLConnection) url.openConnection();
		httpConnection.setRequestMethod("GET");

		try (var in = httpConnection.getInputStream();
			 var fos = new java.io.FileOutputStream(savePath)) {

			byte[] buffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = in.read(buffer)) != -1) {
				fos.write(buffer, 0, bytesRead);
			}
		}

		httpConnection.disconnect();
	}
}