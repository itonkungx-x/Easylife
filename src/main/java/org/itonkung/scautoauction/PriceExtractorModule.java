package org.itonkung.scautoauction;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scans the server-supplied item lore of visible /worth slots, no mouse hover needed. */
public final class PriceExtractorModule implements AutomationModule {
    private static final KeyMapping TOGGLE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.scautoauction.extractor", InputConstants.Type.KEYBOARD, InputConstants.KEY_F7, KeyMapping.Category.MISC));
    private static final Pattern PRICE = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?)");
    private static final Gson JSON = new Gson();
    private static final int MAX_ITEMS = 2048;
    private static final Map<String, PriceEntry> ENTRIES = new LinkedHashMap<>();
    private static volatile String lastItem = "ยังไม่พบข้อมูล";
    private static volatile String lastError = "";
    private static volatile int itemCount;

    private HttpServer server;
    private int serverPort = -1;
    private int ticks;

    public static int getFoundCount() { return itemCount; }
    public static String getLastFoundName() { return lastItem; }
    public static String getStatus() { return lastError; }

    public static synchronized void clearItems() {
        ENTRIES.clear();
        itemCount = 0;
        lastItem = "ยังไม่พบข้อมูล";
    }

    @Override public String id() { return "price_extractor"; }
    @Override public void initialize() { }
    @Override public void stop(Minecraft mc) { stopServer(); }

    @Override
    public void tick(Minecraft mc) {
        while (TOGGLE.consumeClick()) {
            EasyLifeSystemConfig cfg = EasyLifeSystemConfig.get().copy();
            cfg.priceExtractorEnabled = !cfg.priceExtractorEnabled;
            EasyLifeSystemConfig.save(cfg);
            notify(mc, "Price Extractor: " + (cfg.priceExtractorEnabled ? "เปิด" : "ปิด"));
        }
        if (!EasyLifeSystemConfig.get().priceExtractorEnabled) {
            if (server != null) stopServer();
            return;
        }
        ensureServer();
        if (++ticks % 12 != 0 || mc == null || mc.player == null || mc.gui == null) return;
        Screen screen = mc.gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?>)) return;
        if (!screen.getTitle().getString().toLowerCase(Locale.ROOT).contains("worth")) return;

        // The last 36 slots are the player's inventory in a vanilla-style chest menu.
        // Never click any slots; use only the item data sent by the server.
        List<Slot> slots = mc.player.containerMenu.slots;
        int topSlots = Math.max(0, slots.size() - 36);
        int added = 0, updated = 0;
        String newest = "";
        synchronized (PriceExtractorModule.class) {
            for (int i = 0; i < topSlots; i++) {
                ItemStack stack = slots.get(i).getItem();
                if (stack.isEmpty()) continue;
                PriceEntry entry = readItem(stack);
                if (entry == null) continue;
                String key = entry.itemId;
                if (ENTRIES.size() >= MAX_ITEMS && !ENTRIES.containsKey(key)) continue;
                PriceEntry old = ENTRIES.put(key, entry);
                if (old == null) { added++; newest = entry.name + " • " + entry.price; }
                else if (!old.price.equals(entry.price)) { updated++; newest = entry.name + " • " + entry.price; }
            }
            itemCount = ENTRIES.size();
            if (!newest.isBlank()) lastItem = newest;
        }
        if ((added > 0 || updated > 0) && EasyLifeSystemConfig.get().extractorNotifications) {
            notify(mc, "พบใหม่ " + added + " • เปลี่ยนราคา " + updated + " • รวม " + itemCount
                + " รายการ | " + newest);
        }
    }

    /** Only include priced server items: decorative buttons and navigation controls are ignored. */
    private static PriceEntry readItem(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return null;
        String perItem = "", perStack = "";
        for (Component line : lore.lines()) {
            String text = line.getString().trim();
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.contains("ราคาต่อชิ้น") || lower.contains("price per item")) {
                perItem = extractPrice(text);
            }
            if (lower.contains("ราคาต่อสแตค") || lower.contains("ราคาต่อสแต็ก")
                || lower.contains("price per stack")) {
                perStack = extractPrice(text);
            }
        }
        if (perItem.isEmpty() && perStack.isEmpty()) return null;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String name = stack.getHoverName().getString();
        return new PriceEntry(id, name, perItem, perStack, Instant.now().toString());
    }

    private static String extractPrice(String line) {
        String value = line.contains(":") ? line.substring(line.indexOf(':') + 1) : line;
        Matcher matcher = PRICE.matcher(value);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static void notify(Minecraft mc, String text) {
        if (mc != null && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("[Easylife] " + text));
        }
    }

    private synchronized void ensureServer() {
        int port = EasyLifeSystemConfig.get().extractorPort;
        if (server != null && serverPort == port) return;
        stopServer();
        try {
            HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            http.createContext("/", ex -> serve(ex, loadWebsite(), "text/html; charset=utf-8"));
            http.createContext("/items", ex -> serve(ex, itemsJson(), "application/json; charset=utf-8"));
            http.createContext("/data", ex -> serve(ex, itemsJson(), "application/json; charset=utf-8"));
            http.setExecutor(Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "easylife-worth-web");
                t.setDaemon(true);
                return t;
            }));
            http.start();
            server = http;
            serverPort = port;
            lastError = "";
        } catch (IOException e) {
            String message = "เปิดเว็บพอร์ต " + port + " ไม่ได้";
            if (!message.equals(lastError)) System.err.println("[Easylife] " + message + ": " + e);
            lastError = message;
        }
    }

    private synchronized void stopServer() {
        if (server != null) server.stop(0);
        server = null;
        serverPort = -1;
    }

    private static String itemsJson() {
        synchronized (PriceExtractorModule.class) {
            return JSON.toJson(new ArrayList<>(ENTRIES.values()));
        }
    }

    private static String loadWebsite() {
        try (InputStream input = PriceExtractorModule.class.getResourceAsStream("/assets/scautoauction/web/index.html")) {
            if (input == null) return "<h1>Missing Price Extractor page</h1>";
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<h1>Unable to open Price Extractor page</h1>";
        }
    }

    private static void serve(HttpExchange ex, String content, String mimeType) throws IOException {
        ex.getResponseHeaders().set("Content-Type", mimeType);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) { out.write(bytes); }
    }

    private static final class PriceEntry {
        final String itemId;
        final String name;
        final String perItem;
        final String perStack;
        final String price;
        final String updatedAt;
        PriceEntry(String itemId, String name, String perItem, String perStack, String updatedAt) {
            this.itemId = itemId;
            this.name = name;
            this.perItem = perItem;
            this.perStack = perStack;
            this.price = !perItem.isEmpty() ? perItem : perStack;
            this.updatedAt = updatedAt;
        }
    }
}
