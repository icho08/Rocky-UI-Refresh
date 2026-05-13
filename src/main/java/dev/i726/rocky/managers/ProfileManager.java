package dev.i726.rocky.managers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.i726.rocky.Rocky;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ProfileManager {
	private final Gson g = new Gson();
	private Path configDir;
	private final Map<String, double[]> panelPositions = new HashMap<>();

	public ProfileManager() {
		setupPath();
	}

	private void setupPath() {
		String temp = System.getProperty("user.home");
		configDir = Paths.get(temp, ".rocky", "profiles");
		try {
			Files.createDirectories(configDir);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public List<String> getProfiles() {
		try {
			return Files.list(configDir)
					.filter(Files::isRegularFile)
					.filter(p -> p.toString().endsWith(".json"))
					.map(p -> p.getFileName().toString().replace(".json", ""))
					.collect(Collectors.toList());
		} catch (IOException e) {
			return new ArrayList<>();
		}
	}

	public void deleteProfile(String name) {
		try {
			Path path = configDir.resolve(name + ".json");
			Files.deleteIfExists(path);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void loadProfile(String name) {
		try {
			Path profilePath = configDir.resolve(name + ".json");
			if (!Files.exists(profilePath)) return;

			JsonObject profile = g.fromJson(Files.readString(profilePath), JsonObject.class);
			if (profile == null) return;

			// Load Modules & Settings
			if (profile.has("modules")) {
				JsonObject modulesJson = profile.getAsJsonObject("modules");
				for (Module module : Rocky.INSTANCE.getModuleManager().getModules()) {
					String moduleKey = module.getName().toString();
					JsonElement moduleJson = modulesJson.get(moduleKey);
					if (moduleJson == null || !moduleJson.isJsonObject()) continue;
					
					JsonObject moduleConfig = moduleJson.getAsJsonObject();
					JsonElement enabledJson = moduleConfig.get("enabled");
					if (enabledJson != null) module.setEnabled(enabledJson.getAsBoolean());

					// Load category
					JsonElement categoryJson = moduleConfig.get("category");
					if (categoryJson != null) {
						String catName = categoryJson.getAsString();
						Category cat = CategoryManager.getCategories().stream()
							.filter(c -> c.getName().equals(catName))
							.findFirst().orElse(null);
						if (cat != null) module.setCategory(cat);
					}

					for (Setting<?> setting : module.getSettings()) {
						String settingKey = setting.getName().toString();
						JsonElement settingJson = moduleConfig.get(settingKey);
						if (settingJson == null) continue;

						try {
							if (setting instanceof BooleanSetting booleanSetting) {
								booleanSetting.setValue(settingJson.getAsBoolean());
							} else if (setting instanceof ModeSetting<?> modeSetting) {
								modeSetting.setModeIndex(settingJson.getAsInt());
							} else if (setting instanceof NumberSetting numberSetting) {
								numberSetting.setValue(settingJson.getAsDouble());
							} else if (setting instanceof KeybindSetting keybindSetting) {
								keybindSetting.setKey(settingJson.getAsInt());
							} else if (setting instanceof MinMaxSetting minMaxSetting) {
								if (settingJson.isJsonObject()) {
									JsonObject minMaxObject = settingJson.getAsJsonObject();
									minMaxSetting.setMinValue(minMaxObject.get("1").getAsDouble());
									minMaxSetting.setMaxValue(minMaxObject.get("2").getAsDouble());
								}
							}
						} catch (Exception e) {}
					}
				}
			}

			// Load Panel Positions
			if (profile.has("panels")) {
				JsonObject panelsJson = profile.getAsJsonObject("panels");
				for (String catName : panelsJson.keySet()) {
					JsonObject pos = panelsJson.getAsJsonObject(catName);
					panelPositions.put(catName, new double[]{pos.get("x").getAsDouble(), pos.get("y").getAsDouble()});
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void saveProfile(String name) {
		try {
			Path profilePath = configDir.resolve(name + ".json");
			JsonObject profile = new JsonObject();

			// Save Modules
			JsonObject modulesJson = new JsonObject();
			for (Module module : Rocky.INSTANCE.getModuleManager().getModules()) {
				JsonObject moduleConfig = new JsonObject();
				moduleConfig.addProperty("enabled", module.isEnabled());
				moduleConfig.addProperty("category", module.getCategory().getName());

				for (Setting<?> setting : module.getSettings()) {
					String settingKey = setting.getName().toString();
					if (setting instanceof BooleanSetting booleanSetting) {
						moduleConfig.addProperty(settingKey, booleanSetting.getValue());
					} else if (setting instanceof ModeSetting<?> modeSetting) {
						moduleConfig.addProperty(settingKey, modeSetting.getModeIndex());
					} else if (setting instanceof NumberSetting numberSetting) {
						moduleConfig.addProperty(settingKey, numberSetting.getValue());
					} else if (setting instanceof KeybindSetting keybindSetting) {
						moduleConfig.addProperty(settingKey, keybindSetting.getKey());
					} else if (setting instanceof MinMaxSetting minMaxSetting) {
						JsonObject minMaxObject = new JsonObject();
						minMaxObject.addProperty("1", minMaxSetting.getMinValue());
						minMaxObject.addProperty("2", minMaxSetting.getMaxValue());
						moduleConfig.add(settingKey, minMaxObject);
					}
				}
				modulesJson.add(module.getName().toString(), moduleConfig);
			}
			profile.add("modules", modulesJson);

			// Save Panel Positions
			JsonObject panelsJson = new JsonObject();
			for (Map.Entry<String, double[]> entry : panelPositions.entrySet()) {
				JsonObject pos = new JsonObject();
				pos.addProperty("x", entry.getValue()[0]);
				pos.addProperty("y", entry.getValue()[1]);
				panelsJson.add(entry.getKey(), pos);
			}
			profile.add("panels", panelsJson);

			Files.writeString(profilePath, g.toJson(profile));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void setPanelPosition(String catName, double x, double y) {
		panelPositions.put(catName, new double[]{x, y});
	}

	public double[] getPanelPosition(String catName) {
		return panelPositions.get(catName);
	}
}
