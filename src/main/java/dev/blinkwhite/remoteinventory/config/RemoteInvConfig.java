package dev.blinkwhite.remoteinventory.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.blinkwhite.remoteinventory.Reference;
import lombok.Getter;
import lombok.Setter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class RemoteInvConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = Path.of("config", Reference.MOD_ID + ".json");

    @Getter
    private static double maxInteractionDistance = 32.0;
    @Getter
    @Setter
    private static boolean distanceLimitEnabled = true;
    @Getter
    private static final Set<String> whitelist = new HashSet<>();
    @Getter
    private static final Set<String> blacklist = new HashSet<>();
    @Setter
    @Getter
    private static boolean whitelistEnabled;

    private RemoteInvConfig() {}

    public static void load() {
        if (!Files.exists(CONFIG_FILE)) return;
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            if (data == null) return;
            maxInteractionDistance = clamp(data.distance);
            distanceLimitEnabled = data.distanceLimitEnabled != null ? data.distanceLimitEnabled : true;
            whitelistEnabled = data.whitelistEnabled;
            whitelist.clear();
            if (data.whitelist != null) whitelist.addAll(data.whitelist);
            blacklist.clear();
            if (data.blacklist != null) blacklist.addAll(data.blacklist);
            Reference.LOGGER.info("Loaded config: distance={}, whitelist={}, blacklist={}",
                    maxInteractionDistance, whitelist.size(), blacklist.size());
        } catch (Exception e) {
            Reference.LOGGER.error("Failed to load config", e);
        }
    }

    public static void save() {
        ConfigData data = new ConfigData();
        data.distance = maxInteractionDistance;
        data.distanceLimitEnabled = distanceLimitEnabled;
        data.whitelistEnabled = whitelistEnabled;
        data.whitelist = new HashSet<>(whitelist);
        data.blacklist = new HashSet<>(blacklist);
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            Reference.LOGGER.error("Failed to save config", e);
        }
    }

    public static void setMaxInteractionDistance(double distance) {
        maxInteractionDistance = clamp(distance);
        save();
    }

    private static double clamp(double value) {
        return Math.max(1.0, Math.min(value, 256.0));
    }

    public static void addToWhitelist(String id) {
        whitelist.add(id);
        save();
    }

    public static void removeFromWhitelist(String id) {
        whitelist.remove(id);
        save();
    }

    public static void clearWhitelist() {
        whitelist.clear();
        save();
    }

    public static void addToBlacklist(String id) {
        blacklist.add(id);
        save();
    }

    public static void removeFromBlacklist(String id) {
        blacklist.remove(id);
        save();
    }

    public static void clearBlacklist() {
        blacklist.clear();
        save();
    }

    public static void toggleWhitelist(boolean enabled) {
        whitelistEnabled = enabled;
        save();
    }

    public static boolean isBlockAllowed(String blockId) {
        if (whitelistEnabled) {
            return whitelist.contains(blockId);
        }
        if (!blacklist.isEmpty()) {
            return !blacklist.contains(blockId);
        }
        return true;
    }

    private static class ConfigData {
        double distance = 32.0;
        Boolean distanceLimitEnabled;
        boolean whitelistEnabled;
        Set<String> whitelist = new HashSet<>();
        Set<String> blacklist = new HashSet<>();
    }
}