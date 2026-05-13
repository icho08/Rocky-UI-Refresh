package dev.i726.rocky.managers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static dev.i726.rocky.Rocky.mc;

public final class FriendManager {

    private static final Path FRIENDS_PATH =
            Paths.get(System.getProperty("user.home"), ".rocky", "friends.json");

    private final Gson gson = new Gson();
    private Set<String> friends = new HashSet<>();

    public FriendManager() {
        load();
    }

    public void addFriend(PlayerEntity player) {
        friends.add(player.getName().getString());
        save();
    }

    public void removeFriend(PlayerEntity player) {
        friends.remove(player.getName().getString());
        save();
    }

    public boolean isFriend(PlayerEntity player) {
        return friends.contains(player.getName().getString());
    }

    public boolean isFriend(String name) {
        return friends.contains(name);
    }

    public List<String> getFriends() {
        return new ArrayList<>(friends);
    }

    public boolean isAimingOverFriend() {
        if (mc.crosshairTarget instanceof EntityHitResult hitResult) {
            Entity entity = hitResult.getEntity();
            if (entity instanceof PlayerEntity player) {
                return isFriend(player);
            }
        }
        return false;
    }

    public void save() {
        try {
            Files.createDirectories(FRIENDS_PATH.getParent());
            Files.writeString(FRIENDS_PATH, gson.toJson(friends));
        } catch (IOException e) {
            System.err.println("[Rocky] Failed to save friends: " + e.getMessage());
        }
    }

    public void load() {
        try {
            if (!Files.exists(FRIENDS_PATH)) return;
            Type type = new TypeToken<HashSet<String>>() {}.getType();
            Set<String> loaded = gson.fromJson(Files.readString(FRIENDS_PATH), type);
            if (loaded != null) friends = loaded;
        } catch (Exception e) {
            System.err.println("[Rocky] Failed to load friends: " + e.getMessage());
        }
    }
}
