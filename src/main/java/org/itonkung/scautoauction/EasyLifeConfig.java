package org.itonkung.scautoauction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class EasyLifeConfig {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("easylife-in-siamcraft.json");
    private static EasyLifeConfig current = new EasyLifeConfig();

    public String startingPrice = "100";
    public String minimumIncrement = "10%";
    public String buyoutPrice = "0";
    public String durationSeconds = "60";
    public String overtimeSeconds = "11";
    public String startTime = "after1m";
    public boolean autoUpdate = true;
    public int betweenListingsTicks = 20;

    public static synchronized EasyLifeConfig get() {
        return current;
    }

    public static synchronized void load() {
        if (!Files.isRegularFile(PATH)) return;
        try {
            EasyLifeConfig loaded = JSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), EasyLifeConfig.class);
            if (loaded != null) {
                loaded.validate();
                current = loaded;
            }
        } catch (IOException | JsonParseException | IllegalArgumentException ex) {
            System.err.println("[Easylife in Siamcraft] Could not load settings: " + ex);
        }
    }

    public static synchronized void save(EasyLifeConfig config) {
        config.validate();
        try {
            Files.createDirectories(PATH.getParent());
            Path temp = Files.createTempFile(PATH.getParent(), "easylife-", ".json.tmp");
            try {
                Files.writeString(temp, JSON.toJson(config), StandardCharsets.UTF_8);
                try {
                    Files.move(temp, PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temp, PATH, StandardCopyOption.REPLACE_EXISTING);
                }
                current = config;
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not save Easylife settings", ex);
        }
    }

    public EasyLifeConfig copy() {
        EasyLifeConfig copy = new EasyLifeConfig();
        copy.startingPrice = startingPrice;
        copy.minimumIncrement = minimumIncrement;
        copy.buyoutPrice = buyoutPrice;
        copy.durationSeconds = durationSeconds;
        copy.overtimeSeconds = overtimeSeconds;
        copy.startTime = startTime;
        copy.autoUpdate = autoUpdate;
        copy.betweenListingsTicks = betweenListingsTicks;
        return copy;
    }

    public String[] formValues() {
        return new String[]{startingPrice, minimumIncrement, buyoutPrice, durationSeconds, overtimeSeconds, startTime};
    }

    public void validate() {
        if (!digits(startingPrice, 12)) throw new IllegalArgumentException("Invalid starting price");
        if (minimumIncrement == null || !minimumIncrement.matches("[0-9]{1,10}(?:\\.[0-9]{1,3})?%?"))
            throw new IllegalArgumentException("Invalid minimum increment");
        if (!digits(buyoutPrice, 12)) throw new IllegalArgumentException("Invalid buyout price");
        if (!digits(durationSeconds, 6) || Integer.parseInt(durationSeconds) < 1)
            throw new IllegalArgumentException("Invalid auction duration");
        if (!digits(overtimeSeconds, 5)) throw new IllegalArgumentException("Invalid overtime");
        if (startTime == null || startTime.isBlank() || startTime.length() > 32)
            throw new IllegalArgumentException("Invalid start time");
        if (betweenListingsTicks < 5 || betweenListingsTicks > 200)
            throw new IllegalArgumentException("Delay must be 5–200 ticks");
    }

    private static boolean digits(String value, int limit) {
        return value != null && value.length() <= limit && value.matches("[0-9]+");
    }
}
