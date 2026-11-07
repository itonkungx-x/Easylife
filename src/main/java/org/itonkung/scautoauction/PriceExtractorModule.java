package org.itonkung.scautoauction;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
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
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * /worth price scanner.
 *
 * Important: data sent by servers can become visible only after Minecraft
 * assembles the display tooltip. Do not rely only on DataComponents.LORE.
 * Read actual vanilla/container tooltip with an item-lore fallback.
 */
public final class PriceExtractorModule implements AutomationModule {
    private static final KeyMapping TOGGLE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.scautoauction.extractor", InputConstants.Type.KEYBOARD, InputConstants.KEY_F7, KeyMapping.Category.MISC));
    private static final Gson JSON = new Gson();
    private static final int MAX_ITEMS = 2048;
    private static final Map<String, PriceEntry> ENTRIES = new LinkedHashMap<>();

    private static volatile String lastItem = "ยังไม่พบข้อมูล";
    private static volatile String lastJson = "{}";
    private static volatile String lastError = "";
    private static volatile String statusTh = "ยังไม่เริ่ม";
    private static volatile String statusEn = "Not started";
    private static volatile int itemCount, lastScanned, lastPriceLines;

    private HttpServer server;
    private int serverPort = -1;
    private int ticks, emptyRounds, lastMenuId = Integer.MIN_VALUE;
    private String screenTitle = "";
    private boolean warnedNoPrice;
    private Method containerTooltipMethod;
    private boolean tooltipLookupDone;

    public static int getFoundCount() { return itemCount; }
    public static String getLastFoundName() { return lastItem; }
    public static String getStatus() { return lastError.isBlank() ? statusTh : lastError; }
    public static String getStatusForLanguage(String language) {
        String status = "en_us".equals(language) ? statusEn : statusTh;
        return lastError.isBlank() ? status : lastError;
    }
    public static String getDiagnostics() {
        return "Slots: " + lastScanned + " • Price lines: " + lastPriceLines
            + " • Total: " + itemCount;
    }

    public static synchronized void clearItems() {
        ENTRIES.clear();
        itemCount = 0;
        lastItem = "ยังไม่พบข้อมูล";
        lastJson = "{}";
        lastPriceLines = 0;
        lastScanned = 0;
        statusTh = "ล้างข้อมูลแล้ว";
        statusEn = "Scan results cleared";
    }

    @Override public String id() { return "price_extractor"; }
    @Override public void initialize() {
        // KeyMapping.consumeClick() is normally not populated while a screen is open.
        // Listen for the registered F7 (or remapped) key inside the /worth container.
        ScreenEvents.AFTER_INIT.register((client, current, w, h) -> {
            if (!(current instanceof AbstractContainerScreen<?>)) return;
            if (!current.getTitle().getString().toLowerCase(Locale.ROOT).contains("worth")) return;
            ScreenKeyboardEvents.allowKeyPress(current).register((scr, event) -> {
                if (!TOGGLE.matches(event)) return true;
                toggleEnabled(client);
                return false; // handled: prevent container shortcut conflicts
            });
        });
    }
    @Override public void stop(Minecraft mc) { stopServer(); }

    @Override
    public void tick(Minecraft mc) {
        while (TOGGLE.consumeClick()) toggleEnabled(mc);
        if (!EasyLifeSystemConfig.get().priceExtractorEnabled) {
            if (server != null) stopServer();
            statusTh = "ระบบปิดอยู่";
            statusEn = "Scanner disabled";
            return;
        }
        ensureServer();
        if (++ticks % 12 != 0 || mc == null || mc.player == null || mc.gui == null) return;
        Screen current = mc.gui.screen();
        if (!(current instanceof AbstractContainerScreen<?> screen)) {
            statusTh = "รอเปิดหน้า /worth";
            statusEn = "Open /worth to scan prices";
            emptyRounds = 0;
            return;
        }
        String title = screen.getTitle().getString();
        if (!title.toLowerCase(Locale.ROOT).contains("worth")) {
            statusTh = "รอเปิดหน้า /worth (" + title + ")";
            statusEn = "Waiting for /worth (" + title + ")";
            emptyRounds = 0;
            return;
        }

        List<Slot> slots = screen.getMenu().slots;
        int menuId = screen.getMenu().containerId;
        if (menuId != lastMenuId || !title.equals(screenTitle)) {
            emptyRounds = 0;
            warnedNoPrice = false;
            lastMenuId = menuId;
            screenTitle = title;
            say(mc, "พบหน้า " + title + " • กำลังอ่านราคาอัตโนมัติ");
        }

        // The last 36 slots represent player inventory in chest-like menus.
        int topSlots = Math.max(0, slots.size() - 36);
        if (topSlots == 0) {
            statusTh = "พบ /worth แต่ไม่พบช่องรายการ";
            statusEn = "/worth detected, no listing slots";
            return;
        }

        int added = 0, updated = 0, seen = 0, examined = 0, linesFound = 0;
        String newest = "";
        synchronized (PriceExtractorModule.class) {
            for (int i = 0; i < topSlots; i++) {
                ItemStack stack = slots.get(i).getItem();
                if (stack.isEmpty()) continue;
                seen++;
                List<String> tooltip = itemTooltip(mc, screen, stack);
                examined += tooltip.size();
                PriceTextParser.Prices prices = PriceTextParser.parse(tooltip);
                if (!prices.found()) continue;
                linesFound++;

                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                String name = stack.getHoverName().getString();
                String key = itemId + "|" + name;
                if (ENTRIES.size() >= MAX_ITEMS && !ENTRIES.containsKey(key)) continue;
                PriceEntry entry = new PriceEntry(itemId, name, prices.perItem(),
                    prices.perStack(), Instant.now().toString());
                PriceEntry previous = ENTRIES.get(key);
                if (previous == null || !previous.price.equals(entry.price)
                    || !previous.perStack.equals(entry.perStack)) {
                    ENTRIES.put(key, entry);
                    if (previous == null) added++;
                    else updated++;
                    newest = name + " = " + entry.price;
                    lastJson = JSON.toJson(entry);
                }
            }
            itemCount = ENTRIES.size();
            if (!newest.isBlank()) lastItem = newest;
        }
        lastScanned = seen;
        lastPriceLines = linesFound;
        statusTh = "กำลังสแกน /worth • พบช่อง " + seen + " • มีราคา " + linesFound
            + " • สะสม " + itemCount;
        statusEn = "Scanning /worth • Items " + seen + " • Priced " + linesFound
            + " • Saved " + itemCount;
        if ((added > 0 || updated > 0) && EasyLifeSystemConfig.get().extractorNotifications) {
            say(mc, "พบใหม่ " + added + " • ราคาเปลี่ยน " + updated + " • สะสม " + itemCount
                + " | " + newest);
        }
        if (seen > 0 && linesFound == 0 && ++emptyRounds >= 5 && !warnedNoPrice) {
            warnedNoPrice = true;
            say(mc, "พบ /worth " + seen + " ช่อง แต่ยังไม่พบราคาจาก Tooltip"
                + " • ดูสถานะใน Easylife → สแกนราคา");
            System.err.println("[Easylife] /worth found " + seen
                + " slots and " + examined + " tooltip lines, but no recognized price labels.");
        } else if (linesFound > 0) {
            emptyRounds = 0;
            lastError = "";
        }
    }

    private List<String> itemTooltip(Minecraft mc, AbstractContainerScreen<?> screen, ItemStack stack) {
        List<String> all = new ArrayList<>();
        // Primary source: actual on-screen tooltip including client-added lines.
        try {
            if (!tooltipLookupDone) {
                tooltipLookupDone = true;
                containerTooltipMethod = AbstractContainerScreen.class.getDeclaredMethod(
                    "getTooltipFromContainerItem", ItemStack.class);
                containerTooltipMethod.setAccessible(true);
            }
            if (containerTooltipMethod != null) {
                Object result = containerTooltipMethod.invoke(screen, stack);
                if (result instanceof List<?> values) {
                    for (Object value : values) {
                        if (value instanceof Component text) all.add(text.getString());
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (lastError.isBlank()) {
                lastError = "Tooltip reflection: " + error.getClass().getSimpleName();
                System.err.println("[Easylife] " + lastError + ": " + error.getMessage());
            }
        }
        // The base vanilla tooltip is a safe fallback when a mod changes the container tooltip.
        try {
            for (Component line : Screen.getTooltipFromItem(mc, stack)) {
                String value = line.getString();
                if (!all.contains(value)) all.add(value);
            }
        } catch (RuntimeException ignored) {
        }
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) {
                String value = line.getString();
                if (!all.contains(value)) all.add(value);
            }
        }
        return all;
    }

    private static void toggleEnabled(Minecraft mc) {
        EasyLifeSystemConfig cfg = EasyLifeSystemConfig.get().copy();
        cfg.priceExtractorEnabled = !cfg.priceExtractorEnabled;
        EasyLifeSystemConfig.save(cfg);
        say(mc, cfg.priceExtractorEnabled
            ? "Price Extractor เปิดแล้ว • ระบบจะอ่านราคาใน /worth"
            : "Price Extractor ปิดแล้ว • กด F7 อีกครั้งเพื่อเปิด");
    }

    private static void say(Minecraft mc, String line) {
        if (mc != null && mc.player != null)
            mc.player.sendSystemMessage(Component.literal("[Easylife] " + line));
    }

    private synchronized void ensureServer() {
        int port = EasyLifeSystemConfig.get().extractorPort;
        if (server != null && serverPort == port) return;
        stopServer();
        try {
            HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            http.createContext("/", ex -> serve(ex, loadWebsite(), "text/html; charset=utf-8"));
            http.createContext("/items", ex -> serve(ex, itemsJson(), "application/json; charset=utf-8"));
            http.createContext("/data", ex -> serve(ex, lastJson, "application/json; charset=utf-8"));
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
            statusTh = message;
            statusEn = "Cannot open local port " + port;
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
        try (InputStream input = PriceExtractorModule.class.getResourceAsStream(
            "/assets/scautoauction/web/index.html")) {
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
