package org.itonkung.scautoauction;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Easylife-only localizer: Thai by default without changing Minecraft language. */
public final class EasyLifeText {
    private static final Map<String, String> THAI = read("th_th");
    private static final Map<String, String> ENGLISH = read("en_us");

    private EasyLifeText() { }

    public static Component of(String key, Object... args) {
        return Component.literal(get(key, args));
    }

    public static String get(String key, Object... args) {
        return getIn(EasyLifeSystemConfig.get().uiLanguage, key, args);
    }

    public static String getIn(String language, String key, Object... args) {
        var current = "en_us".equals(language) ? ENGLISH : THAI;
        String value = current.getOrDefault(key, THAI.getOrDefault(key,
            ENGLISH.getOrDefault(key, key)));
        if (args.length == 0) return value;
        try {
            return String.format(Locale.ROOT, value, args);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    private static Map<String, String> read(String language) {
        Map<String, String> values = new HashMap<>();
        String path = "/assets/scautoauction/lang/" + language + ".json";
        try (InputStream stream = EasyLifeText.class.getResourceAsStream(path)) {
            if (stream == null) return values;
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                values.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (Exception e) {
            System.err.println("[Easylife] Failed to load " + language + " UI strings: " + e);
        }
        return values;
    }
}
