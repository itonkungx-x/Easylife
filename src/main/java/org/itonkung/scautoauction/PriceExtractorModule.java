package org.itonkung.scautoauction;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the item tooltip actually shown in /worth; serves JSON+HTML only on localhost.
 * Never clicks on slots, changes inventory, or uploads data to an external service.
 */
public final class PriceExtractorModule implements AutomationModule {
    private static final Pattern MONEY = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?)");
    private final Map<String, String> scanned = new ConcurrentHashMap<>();
    private volatile String latestJson = "{\"name\":\"\",\"itemId\":\"\",\"perItem\":\"\",\"perStack\":\"\",\"updatedAt\":\"\",\"lines\":[]}";
    private volatile String fingerprint = "";
    private volatile HttpServer server;
    private volatile int boundPort = -1;
    private volatile String serverError = "";
    private int scanTicks;

    @Override public String id() { return "price_extractor"; }
    @Override public void initialize() { /* Start only when the Price Extractor is selected. */ }
    @Override public void stop(Minecraft mc) { }

    @Override
    public void tick(Minecraft mc) {
        if (!"price_extractor".equals(EasyLifeSystemConfig.get().activeSystem)) return;
        ensureServer();
        if (++scanTicks % 4 != 0 || mc == null || mc.gui == null) return;
        try {
            Screen screen = mc.gui.screen();
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            if (!screen.getTitle().getString().toLowerCase(Locale.ROOT).contains("worth")) return;

            // Minecraft 26.3 stores the hovered Slot in this protected vanilla field.
            // Read via reflection rather than trying to resize or modify GUI slots.
            Field field = AbstractContainerScreen.class.getDeclaredField("hoveredSlot");
            field.setAccessible(true);
            Object hovered = field.get(screen);
            if (!(hovered instanceof Slot slot)) return;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) return;

            Method tooltipMethod = AbstractContainerScreen.class
                .getDeclaredMethod("getTooltipFromContainerItem", ItemStack.class);
            tooltipMethod.setAccessible(true);
            Object result = tooltipMethod.invoke(screen, stack);
            if (!(result instanceof List<?> elements)) return;
            List<String> lines = new ArrayList<>();
            for (Object element : elements) {
                if (element instanceof Component component) {
                    String line = component.getString().trim();
                    if (!line.isEmpty()) lines.add(line);
                }
            }
            if (lines.isEmpty()) return;
            String key = String.join("\n", lines);
            if (key.equals(fingerprint)) return;
            fingerprint = key;
            String name = lines.getFirst();
            String id = "";
            String perItem = "";
            String perStack = "";
            for (String line : lines) {
                String lower = line.toLowerCase(Locale.ROOT);
                String stripped = line.trim();
                if (id.isEmpty() && stripped.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")) id = stripped;
                if (perItem.isEmpty() && (lower.contains("ราคาต่อชิ้น") || lower.contains("price per item"))) {
                    perItem = extractPrice(line);
                }
                if (perStack.isEmpty() && (lower.contains("ราคาต่อสแตค") || lower.contains("ราคาต่อสแต็ก")
                    || lower.contains("price per stack"))) {
                    perStack = extractPrice(line);
                }
            }
            latestJson = json(name, id, perItem, perStack, lines);
            if (!id.isEmpty() && (!perItem.isEmpty() || !perStack.isEmpty())) {
                scanned.put(id, latestJson);
            }
        } catch (ReflectiveOperationException ex) {
            if (serverError.isEmpty()) {
                serverError = "Tooltip API unavailable: " + ex.getClass().getSimpleName();
                System.err.println("[Easylife] " + serverError);
            }
        } catch (RuntimeException ex) {
            if (serverError.isEmpty()) {
                serverError = "Tooltip scan failed: " + ex.getClass().getSimpleName();
                System.err.println("[Easylife] " + serverError);
            }
        }
    }

    private void ensureServer() {
        int port = EasyLifeSystemConfig.get().extractorPort;
        if (server != null && boundPort == port) return;
        synchronized (this) {
            if (server != null && boundPort == port) return;
            if (server != null) server.stop(0);
            server = null;
            boundPort = -1;
            try {
                HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
                http.createContext("/data", exchange -> serve(exchange, latestJson, "application/json; charset=utf-8"));
                http.createContext("/items", exchange -> serve(exchange,
                    "[" + String.join(",", scanned.values()) + "]", "application/json; charset=utf-8"));
                http.createContext("/", exchange -> serve(exchange, HTML, "text/html; charset=utf-8"));
                http.setExecutor(Executors.newSingleThreadExecutor(task -> {
                    Thread thread = new Thread(task, "easylife-price-http");
                    thread.setDaemon(true);
                    return thread;
                }));
                http.start();
                server = http;
                boundPort = port;
                serverError = "";
            } catch (IOException ex) {
                if (!("Cannot bind extractor port " + port).equals(serverError)) {
                    serverError = "Cannot bind extractor port " + port;
                    System.err.println("[Easylife] " + serverError + ": " + ex);
                }
            }
        }
    }

    private static void serve(HttpExchange exchange, String body, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream stream = exchange.getResponseBody()) { stream.write(data); }
    }

    private static String extractPrice(String line) {
        String afterLabel = line.contains(":") ? line.substring(line.indexOf(':') + 1) : line;
        Matcher matcher = MONEY.matcher(afterLabel);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String json(String name, String id, String item, String stack, List<String> lines) {
        StringBuilder b = new StringBuilder(512);
        b.append('{');
        prop(b, "name", name).append(',');
        prop(b, "itemId", id).append(',');
        prop(b, "perItem", item).append(',');
        prop(b, "perStack", stack).append(',');
        prop(b, "updatedAt", Instant.now().toString()).append(',');
        b.append("\"lines\":[");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) b.append(',');
            escape(b, lines.get(i));
        }
        return b.append("]}").toString();
    }

    private static StringBuilder prop(StringBuilder b, String name, String value) {
        escape(b, name).append(':');
        return escape(b, value);
    }

    private static StringBuilder escape(StringBuilder b, String str) {
        b.append('"');
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '"' -> b.append("\\\"");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> b.append(c);
            }
        }
        return b.append('"');
    }

    private static final String HTML = """
        <!doctype html>
        <html lang="th"><head><meta charset="UTF-8"/>
        <meta name="viewport" content="width=device-width,initial-scale=1"/>
        <title>Easylife Price Extractor</title>
        <style>
        body{font:16px system-ui,sans-serif;color:#faf6ef;background:#111114;margin:0;padding:32px}
        main{max-width:700px;margin:auto;padding:26px;background:#232126;border:1px solid #ffae45;border-radius:18px}
        h1{color:#ffae45} strong{color:#ffd58b}.row{margin:12px 0}
        pre{background:#131217;padding:14px;white-space:pre-wrap;border-radius:10px}
        </style></head><body><main>
        <h1>Price Extractor • Siamcraft</h1>
        <p>เปิด <strong>/worth</strong> และนำเมาส์ชี้ไอเท็ม</p>
        <div class="row">ไอเท็ม: <strong id="name">รอข้อมูล...</strong></div>
        <div class="row">ID: <span id="itemId">-</span></div>
        <div class="row">ราคาต่อชิ้น: <strong id="perItem">-</strong></div>
        <div class="row">ราคาต่อสแตค: <strong id="perStack">-</strong></div>
        <div class="row">ล่าสุด: <span id="updatedAt">-</span></div>
        <pre id="lines"></pre>
        <p>JSON API: <code>/data</code> (ล่าสุด) • <code>/items</code> (ที่สแกนแล้ว)</p>
        </main><script>
        async function refresh(){
          try {
            const r = await fetch('/data',{cache:'no-store'}), d=await r.json();
            for (const id of ['name','itemId','perItem','perStack','updatedAt']) {
              document.getElementById(id).textContent=d[id]||'-';
            }
            document.getElementById('lines').textContent=(d.lines||[]).join('\\n');
          } catch(e){}
        }
        setInterval(refresh,400);refresh();
        </script></body></html>
        """;
}
