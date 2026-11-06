package org.itonkung.scautoauction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Separate settings for the selected Easylife workflow.
 * Auction settings remain in easylife-in-siamcraft.json.
 */
public final class EasyLifeSystemConfig {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("easylife-systems.json");
    private static EasyLifeSystemConfig current = new EasyLifeSystemConfig();

    public String activeSystem = "auction";
    public int extractorPort = 8787;

    public static synchronized EasyLifeSystemConfig get() {
        return current;
    }

    public static synchronized void load() {
        if (!Files.isRegularFile(PATH)) return;
        try {
            EasyLifeSystemConfig loaded = JSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), EasyLifeSystemConfig.class);
            if (loaded != null) {
                loaded.validate();
                current = loaded;
            }
        } catch (IOException | RuntimeException error) {
            System.err.println("[Easylife] Could not load system settings: " + error);
        }
    }

    public static synchronized void save(EasyLifeSystemConfig config) {
        config.validate();
        try {
            Files.createDirectories(PATH.getParent());
            Path tmp = Files.createTempFile(PATH.getParent(), "easylife-system-", ".tmp");
            try {
                Files.writeString(tmp, JSON.toJson(config), StandardCharsets.UTF_8);
                Files.move(tmp, PATH, StandardCopyOption.REPLACE_EXISTING);
                current = config.copy();
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Cannot save systems configuration", error);
        }
    }

    public EasyLifeSystemConfig copy() {
        EasyLifeSystemConfig copy = new EasyLifeSystemConfig();
        copy.activeSystem = activeSystem;
        copy.extractorPort = extractorPort;
        return copy;
    }

    public void validate() {
        if (!"auction".equals(activeSystem) && !"price_extractor".equals(activeSystem)) {
            throw new IllegalArgumentException("Unknown system");
        }
        if (extractorPort < 1024 || extractorPort > 65535) {
            throw new IllegalArgumentException("Web port must be 1024-65535");
        }
    }
}
