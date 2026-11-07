package org.itonkung.scautoauction;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.awt.Desktop;
import java.net.URI;

/**
 * Separate Auction and Price Extractor screens, with a Siamcraft-logo-only header.
 * Native Minecraft key bindings are configured via the game's KeyBindsScreen.
 */
public final class EasyLifeSettingsScreen extends Screen {
    private enum Page { HOME, AUCTION, EXTRACTOR }
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath(
        "scautoauction", "textures/gui/siamcraft_logo.png");
    private static final int GOLD = 0xFFFFC266;
    private static final int TEXT = 0xFFF2EEE8;

    private final Screen parent;
    private final Page page;
    private EasyLifeConfig auction;
    private EasyLifeSystemConfig systems;
    private final EditBox[] fields = new EditBox[6];
    private EditBox delay;
    private EditBox port;
    private Component error = Component.empty();
    private int left, top, contentWidth, halfWidth, gap;

    private static final String[] AUCTION_FIELDS = {
        "easylife.setting.starting_price", "easylife.setting.increment",
        "easylife.setting.buyout", "easylife.setting.duration",
        "easylife.setting.overtime", "easylife.setting.start_time"
    };

    public EasyLifeSettingsScreen(Screen parent) { this(parent, Page.HOME); }

    private EasyLifeSettingsScreen(Screen parent, Page page) {
        super(Component.translatable("easylife.settings.title"));
        this.parent = parent;
        this.page = page;
        this.auction = EasyLifeConfig.get().copy();
        this.systems = EasyLifeSystemConfig.get().copy();
    }

    @Override
    protected void init() {
        super.init();
        contentWidth = Math.min(444, Math.max(244, width - 32));
        left = (width - contentWidth) / 2;
        top = Math.max(6, (height - 372) / 2);
        gap = 14;
        halfWidth = (contentWidth - gap) / 2;
        error = Component.empty();

        switch (page) {
            case HOME -> homeControls();
            case AUCTION -> auctionControls();
            case EXTRACTOR -> extractorControls();
        }
    }

    private void homeControls() {
        modeCard("easylife.module.auction", systems.auctionEnabled, top + 106, true);
        modeCard("easylife.module.price_extractor", systems.priceExtractorEnabled, top + 201, false);

        button("easylife.settings.keybinds", left + 10, top + 306, halfWidth - 10, 23,
            () -> minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options)));
        button("easylife.settings.updates", left + halfWidth + gap, top + 306, halfWidth - 10, 23,
            () -> {
                auction.autoUpdate = !auction.autoUpdate;
                EasyLifeConfig.save(auction);
                minecraft.gui.setScreen(new EasyLifeSettingsScreen(parent));
            });
        button("easylife.settings.close", left + (contentWidth - 164) / 2, top + 338, 164, 22, this::onClose);
    }

    private void modeCard(String label, boolean enabled, int y, boolean isAuction) {
        int rowY = y + 34;
        addRenderableWidget(Button.builder(Component.translatable(enabled
                ? "easylife.mode.disable" : "easylife.mode.enable"), btn -> {
            EasyLifeSystemConfig next = EasyLifeSystemConfig.get().copy();
            if (isAuction) next.auctionEnabled = !next.auctionEnabled;
            else next.priceExtractorEnabled = !next.priceExtractorEnabled;
            EasyLifeSystemConfig.save(next);
            minecraft.gui.setScreen(new EasyLifeSettingsScreen(parent));
        }).bounds(left + 14, rowY, halfWidth - 12, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("easylife.mode.settings"), btn ->
            minecraft.gui.setScreen(new EasyLifeSettingsScreen(parent,
                isAuction ? Page.AUCTION : Page.EXTRACTOR))
        ).bounds(left + halfWidth + gap, rowY, halfWidth - 12, 22).build());
    }

    private void auctionControls() {
        String[] values = auction.formValues();
        for (int i = 0; i < fields.length; i++) {
            int x = left + 8 + (i % 2) * (halfWidth + gap);
            int y = top + 117 + (i / 2) * 42;
            EditBox edit = new EditBox(font, x, y + 14, halfWidth - 16, 19,
                Component.translatable(AUCTION_FIELDS[i]));
            edit.setMaxLength(i == 5 ? 32 : 18);
            edit.setValue(values[i]);
            fields[i] = addRenderableWidget(edit);
        }
        delay = new EditBox(font, left + 8, top + 259, halfWidth - 16, 20,
            Component.translatable("easylife.setting.delay"));
        delay.setMaxLength(3);
        delay.setValue(Integer.toString(auction.betweenListingsTicks));
        addRenderableWidget(delay);

        button("easylife.settings.keybinds", left + halfWidth + gap, top + 259,
            halfWidth - 16, 20,
            () -> minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options)));
        saveButtons(this::saveAuction);
    }

    private void extractorControls() {
        port = new EditBox(font, left + 8, top + 168, halfWidth - 16, 21,
            Component.translatable("easylife.extractor.port"));
        port.setMaxLength(5);
        port.setValue(Integer.toString(systems.extractorPort));
        addRenderableWidget(port);

        button(systems.extractorNotifications
                ? "easylife.notify.on" : "easylife.notify.off",
            left + halfWidth + gap, top + 168, halfWidth - 16, 21,
            () -> {
                systems.extractorNotifications = !systems.extractorNotifications;
                minecraft.gui.setScreen(new EasyLifeSettingsScreen(parent, Page.EXTRACTOR));
                EasyLifeSystemConfig cfg = EasyLifeSystemConfig.get().copy();
                cfg.extractorNotifications = systems.extractorNotifications;
                EasyLifeSystemConfig.save(cfg);
            });

        button("easylife.extractor.open_web", left + 8, top + 244, halfWidth - 16, 23,
            this::openLocalWebsite);
        button("easylife.extractor.clear", left + halfWidth + gap, top + 244, halfWidth - 16, 23,
            PriceExtractorModule::clearItems);
        button("easylife.settings.keybinds", left + 8, top + 281, halfWidth - 16, 21,
            () -> minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options)));
        saveButtons(this::saveExtractor);
    }

    private void saveButtons(Runnable saveAction) {
        button("easylife.settings.save", left + 8, top + 329, halfWidth - 16, 24, saveAction);
        button("easylife.settings.cancel", left + halfWidth + gap, top + 329, halfWidth - 16,
            24, this::onClose);
    }

    private void saveAuction() {
        try {
            auction.startingPrice = fields[0].getValue().trim();
            auction.minimumIncrement = fields[1].getValue().trim();
            auction.buyoutPrice = fields[2].getValue().trim();
            auction.durationSeconds = fields[3].getValue().trim();
            auction.overtimeSeconds = fields[4].getValue().trim();
            auction.startTime = fields[5].getValue().trim();
            auction.betweenListingsTicks = Integer.parseInt(delay.getValue().trim());
            EasyLifeConfig.save(auction);
            onClose();
        } catch (RuntimeException e) {
            error = Component.literal("ข้อมูลไม่ถูกต้อง: " + e.getMessage());
        }
    }

    private void saveExtractor() {
        try {
            systems.extractorPort = Integer.parseInt(port.getValue().trim());
            EasyLifeSystemConfig.save(systems);
            onClose();
        } catch (RuntimeException e) {
            error = Component.literal("ข้อมูลไม่ถูกต้อง: " + e.getMessage());
        }
    }

    private void openLocalWebsite() {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create("http://127.0.0.1:" + systems.extractorPort));
            }
        } catch (Exception e) {
            error = Component.literal("ไม่สามารถเปิดเบราว์เซอร์ได้");
        }
    }

    private void button(String key, int x, int y, int w, int h, Runnable action) {
        addRenderableWidget(Button.builder(Component.translatable(key), b -> action.run())
            .bounds(x, y, Math.max(80, w), h).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // No header text; only the user-supplied Siamcraft logo.
        g.fill(left - 12, top - 8, left + contentWidth + 12, top + 369, 0xEB17171E);
        g.outline(left - 12, top - 8, contentWidth + 24, 377, 0xFFFFA33D);
        g.fillGradient(left - 11, top - 7, left + contentWidth + 11, top - 3,
            0xFFFFD772, 0xFFE17C27);
        g.blit(LOGO, width / 2 - 62, top + 4, width / 2 + 62, top + 78,
            0.0F, 1.0F, 0.0F, 1.0F);

        // Main content is deliberately below the logo-only header.
        if (page == Page.HOME) {
            card(g, top + 106, systems.auctionEnabled);
            card(g, top + 201, systems.priceExtractorEnabled);
        } else {
            card(g, top + 101, page == Page.AUCTION
                ? systems.auctionEnabled : systems.priceExtractorEnabled);
        }
        super.extractRenderState(g, mouseX, mouseY, delta);

        if (page == Page.HOME) {
            label(g, "easylife.module.auction", left + 16, top + 116, GOLD);
            label(g, systems.auctionEnabled ? "easylife.status.running" : "easylife.status.stopped",
                left + contentWidth - 78, top + 116, systems.auctionEnabled ? 0xFF6AE7A2 : 0xFFAAAAAA);
            label(g, "easylife.auction.short", left + 16, top + 136, 0xFFCCCFD5);

            label(g, "easylife.module.price_extractor", left + 16, top + 211, GOLD);
            label(g, systems.priceExtractorEnabled ? "easylife.status.running" : "easylife.status.stopped",
                left + contentWidth - 78, top + 211, systems.priceExtractorEnabled ? 0xFF6AE7A2 : 0xFFAAAAAA);
            label(g, "easylife.extractor.short", left + 16, top + 231, 0xFFCCCFD5);
            label(g, "easylife.extractor.found",
                left + 16, top + 263, 0xFFFFD48C, PriceExtractorModule.getFoundCount());
        } else if (page == Page.AUCTION) {
            label(g, "easylife.auction.settings", left + 15, top + 105, GOLD);
            for (int i = 0; i < fields.length; i++) {
                int x = left + 8 + (i % 2) * (halfWidth + gap);
                int y = top + 117 + (i / 2) * 42;
                label(g, AUCTION_FIELDS[i], x, y, TEXT);
            }
            label(g, "easylife.setting.delay", left + 8, top + 247, TEXT);
            label(g, "easylife.auction.keyhelp", left + 8, top + 292, 0xFFB7BBC2);
        } else {
            label(g, "easylife.extractor.settings", left + 15, top + 109, GOLD);
            label(g, "easylife.extractor.found", left + 15, top + 132, 0xFF6AE7A2,
                PriceExtractorModule.getFoundCount());
            String last = PriceExtractorModule.getLastFoundName();
            if (last.length() > 46) last = last.substring(0, 43) + "...";
            g.text(font, Component.literal(last), left + 15, top + 145, TEXT);
            label(g, "easylife.extractor.port", left + 8, top + 156, TEXT);
            label(g, "easylife.extractor.hint", left + 8, top + 218, 0xFFB7BBC2,
                "http://127.0.0.1:" + systems.extractorPort);
            String status = PriceExtractorModule.getStatus();
            if (!status.isBlank()) g.text(font, Component.literal(status), left + 8, top + 312, 0xFFFF8B8B);
        }
        if (!error.getString().isEmpty()) {
            g.centeredText(font, error, width / 2, top + 357, 0xFFFF7171);
        }
    }

    private void card(GuiGraphicsExtractor g, int y, boolean enabled) {
        g.fill(left + 5, y, left + contentWidth - 5, y + (page == Page.HOME ? 85 : 213),
            enabled ? 0xF0242927 : 0xF0222229);
        g.outline(left + 5, y, contentWidth - 10, page == Page.HOME ? 85 : 213,
            enabled ? 0xFF587C58 : 0xFF55515B);
    }

    private void label(GuiGraphicsExtractor g, String key, int x, int y, int color, Object... args) {
        g.text(font, Component.translatable(key, args), x, y, color);
    }

    @Override
    public void onClose() {
        if (page == Page.HOME) minecraft.gui.setScreen(parent);
        else minecraft.gui.setScreen(new EasyLifeSettingsScreen(parent));
    }
}
