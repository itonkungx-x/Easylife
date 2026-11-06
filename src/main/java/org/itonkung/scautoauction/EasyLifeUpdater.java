package org.itonkung.scautoauction;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public final class EasyLifeUpdater {
    private static final String REPO = "itonkungx-x/SC-AutoAuction";
    private static final String RELEASE_API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String ASSET_PREFIX = "Easylife-in-Siamcraft-26.3-";
    private static final AtomicBoolean CHECKING = new AtomicBoolean(false);
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private static volatile String statusKey = "easylife.update.idle";
    private static volatile String statusArg = "";
    private static volatile boolean pending;

    private EasyLifeUpdater() {}

    public static Component getStatusText() {
        String key = statusKey;
        return statusArg.isEmpty() ? Component.translatable(key) : Component.translatable(key, statusArg);
    }

    public static void checkForUpdates(boolean manual) {
        if (!manual && !EasyLifeConfig.get().autoUpdate) return;
        if (!CHECKING.compareAndSet(false, true)) return;
        setStatus("easylife.update.checking", "");
        Thread.startVirtualThread(() -> {
            try {
                check();
            } catch (Exception ex) {
                setStatus("easylife.update.error", safeError(ex));
                System.err.println("[Easylife in Siamcraft] Update error: " + ex);
            } finally {
                CHECKING.set(false);
            }
        });
    }

    private static void check() throws Exception {
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        HttpRequest query = HttpRequest.newBuilder(URI.create(RELEASE_API))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Easylife-in-Siamcraft-Updater")
            .GET().build();
        HttpResponse<String> response = client.send(query, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            setStatus("easylife.update.no_release", "");
            return;
        }
        if (response.statusCode() != 200) throw new IOException("GitHub HTTP " + response.statusCode());
        JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
        if (release.has("draft") && release.get("draft").getAsBoolean()) {
            setStatus("easylife.update.no_release", "");
            return;
        }
        String tag = release.get("tag_name").getAsString();
        String latest = tag.startsWith("v") ? tag.substring(1) : tag;
        String installed = FabricLoader.getInstance().getModContainer("scautoauction")
            .orElseThrow().getMetadata().getVersion().getFriendlyString();

        if (compareVersions(latest, installed) <= 0) {
            setStatus("easylife.update.current", "");
            return;
        }

        JsonArray assets = release.getAsJsonArray("assets");
        if (assets == null) throw new IOException("Release has no files");
        JsonObject asset = null;
        for (var e : assets) {
            JsonObject a = e.getAsJsonObject();
            String name = a.get("name").getAsString();
            if (name.equals(ASSET_PREFIX + latest + ".jar")) {
                asset = a;
                break;
            }
        }
        if (asset == null) throw new IOException("Missing Minecraft 26.3 JAR");

        // GitHub calculates the SHA-256 digest of each release asset.
        // Never silently install a file that GitHub cannot integrity-check.
        if (!asset.has("digest") || asset.get("digest").isJsonNull()) {
            throw new IOException("Release asset has no SHA-256 digest");
        }
        String digest = asset.get("digest").getAsString();
        if (!digest.matches("sha256:[a-fA-F0-9]{64}")) {
            throw new IOException("Release SHA-256 digest is invalid");
        }
        long expectedSize = asset.get("size").getAsLong();
        if (expectedSize < 1024 || expectedSize > 8_000_000L) {
            throw new IOException("Unexpected JAR size");
        }

        String downloadUrl = asset.get("browser_download_url").getAsString();
        String approvedPrefix = "https://github.com/" + REPO + "/releases/download/";
        if (!downloadUrl.startsWith(approvedPrefix)) {
            throw new IOException("Untrusted release URL");
        }
        if (!EasyLifeConfig.get().autoUpdate) {
            setStatus("easylife.update.manual_available", latest);
            return;
        }
        setStatus("easylife.update.available", latest);
        HttpRequest download = HttpRequest.newBuilder(URI.create(downloadUrl))
            .timeout(Duration.ofSeconds(60))
            .header("User-Agent", "Easylife-in-Siamcraft-Updater")
            .GET().build();
        HttpResponse<byte[]> file = client.send(download, HttpResponse.BodyHandlers.ofByteArray());
        if (file.statusCode() != 200 || file.body().length != expectedSize) {
            throw new IOException("Download incomplete (HTTP " + file.statusCode() + ")");
        }
        String actualDigest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.body()));
        if (!MessageDigest.isEqual(actualDigest.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                digest.substring(7).toLowerCase(Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new IOException("SHA-256 mismatch");
        }

        Path directory = FabricLoader.getInstance().getConfigDir().resolve("easylife-updater");
        Files.createDirectories(directory);
        Path tmp = Files.createTempFile(directory, "download-", ".jar");
        try {
            Files.write(tmp, file.body());
            verifyJar(tmp, latest);
            Path staged = directory.resolve("pending.jar");
            Files.move(tmp, staged, StandardCopyOption.REPLACE_EXISTING);
            registerShutdownInstall(directory, staged);
            pending = true;
            setStatus("easylife.update.ready", latest);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void verifyJar(Path path, String version) throws IOException {
        try (JarFile jar = new JarFile(path.toFile())) {
            JarEntry meta = jar.getJarEntry("fabric.mod.json");
            if (meta == null) throw new IOException("JAR does not contain Fabric metadata");
            try (InputStream stream = jar.getInputStream(meta)) {
                JsonObject json = JsonParser.parseString(
                    new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                if (!"scautoauction".equals(json.get("id").getAsString())
                    || !version.equals(json.get("version").getAsString())) {
                    throw new IOException("JAR mod ID or version does not match release");
                }
                JsonObject dependencies = json.getAsJsonObject("depends");
                if (dependencies == null || !dependencies.has("minecraft")
                    || !"26.3".equals(dependencies.get("minecraft").getAsString())) {
                    throw new IOException("Release is for another Minecraft version");
                }
            }
        }
    }

    private static void registerShutdownInstall(Path directory, Path staged) throws IOException {
        if (!REGISTERED.compareAndSet(false, true)) return;
        // The source location must be a real JAR inside this game's mods directory.
        var origin = FabricLoader.getInstance().getModContainer("scautoauction").orElseThrow().getOrigin();
        if (origin.getKind() != net.fabricmc.loader.api.metadata.ModOrigin.Kind.PATH
            || origin.getPaths().size() != 1) {
            REGISTERED.set(false);
            throw new IOException("Cannot locate installed mod JAR");
        }
        Path current = origin.getPaths().get(0).toRealPath();
        Path mods = FabricLoader.getInstance().getGameDir().resolve("mods").toRealPath();
        if (!Files.isRegularFile(current) || !current.getParent().equals(mods)
            || !current.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
            REGISTERED.set(false);
            throw new IOException("Mod is not installed as a standalone JAR in mods/");
        }
        Path helper = directory.resolve("easylife-install-helper.jar");
        createHelperJar(helper);
        Path log = directory.resolve("install.log");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                String javaExe = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                    ? "javaw.exe" : "java";
                Path java = Path.of(System.getProperty("java.home"), "bin", javaExe);
                new ProcessBuilder(java.toString(), "-jar", helper.toString(),
                    Long.toString(ProcessHandle.current().pid()),
                    staged.toString(), current.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
                    .start();
            } catch (IOException ex) {
                System.err.println("[Easylife in Siamcraft] Installer launch failed: " + ex);
            }
        }, "easylife-update-install"));
    }

    private static void createHelperJar(Path output) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, EasyLifeInstaller.class.getName());
        String classPath = EasyLifeInstaller.class.getName().replace('.', '/') + ".class";
        try (InputStream data = EasyLifeInstaller.class.getClassLoader().getResourceAsStream(classPath)) {
            if (data == null) throw new IOException("Missing embedded installer");
            try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(output), manifest)) {
                jar.putNextEntry(new JarEntry(classPath));
                data.transferTo(jar);
                jar.closeEntry();
            }
        }
    }

    private static int compareVersions(String latest, String installed) {
        String[] a = latest.split("[.\\-]");
        String[] b = installed.split("[.\\-]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int left = i < a.length ? number(a[i]) : 0;
            int right = i < b.length ? number(b[i]) : 0;
            int compare = Integer.compare(left, right);
            if (compare != 0) return compare;
        }
        return 0;
    }

    private static int number(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String safeError(Exception exception) {
        String text = exception.getMessage();
        if (text == null) return exception.getClass().getSimpleName();
        return text.length() > 70 ? text.substring(0, 70) + "…" : text;
    }

    private static void setStatus(String key, String arg) {
        statusArg = arg;
        statusKey = key;
    }

    public static boolean hasPendingUpdate() {
        return pending;
    }
}
