package dev.blinkwhite.remoteinventory.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.blinkwhite.remoteinventory.Reference;
import net.minecraft.server.level.ServerPlayer;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class Translations {
    private static final Gson GSON = new Gson();
    private static final Map<String, Map<String, String>> TRANSLATIONS = new HashMap<>();
    private static final String[] LANGUAGES = {"en_us", "zh_cn", "zh_tw", "lzh"};
    private static final String LANG_PATH = "/assets/remote-inventory-server/lang/%s.json";

    private Translations() {}

    public static void init() {
        for (String lang : LANGUAGES) {
            try (Reader reader = new InputStreamReader(
                    Translations.class.getResourceAsStream(String.format(LANG_PATH, lang)))) {
                Type type = new TypeToken<Map<String, String>>(){}.getType();
                Map<String, String> map = GSON.fromJson(reader, type);
                if (map != null) {
                    TRANSLATIONS.put(lang, map);
                }
            } catch (Exception e) {
                Reference.LOGGER.warn("Failed to load language: {}", lang, e);
            }
        }
        Reference.LOGGER.info("Loaded {} language(s)", TRANSLATIONS.size());
    }

    public static String translate(String language, String key, Object... args) {
        Map<String, String> langMap = TRANSLATIONS.getOrDefault(language, TRANSLATIONS.get("en_us"));
        if (langMap == null) return key;
        String template = langMap.getOrDefault(key, key);
        try {
            return String.format(template, args);
        } catch (Exception e) {
            return template;
        }
    }

    public static String getPlayerLanguage(ServerPlayer player) {
        //#if MC >= 12005
        try {
            return player.clientInformation().language();
        } catch (Exception ignored) {}
        //#endif
        return "en_us";
    }
}