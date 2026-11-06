package org.itonkung.scautoauction;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class EasyLifeSettingsScreen extends Screen {
    private final Screen parent;
    private final EasyLifeConfig draft;
    private final EditBox[] auctionFields = new EditBox[6];
    private EditBox delayField;
    private Button autoUpdateButton;
    private Component warning = Component.empty();
    private int top;
    private int left;
    private int columnWidth;
    private int columnStep;
    private static final String[] LABEL_KEYS = {
        "easylife.setting.starting_price",
        "easylife.setting.increment",
        "easylife.setting.buyout",
        "easylife.setting.duration",
        "easylife.setting.overtime",
        "easylife.setting.start_time"
    };

    public EasyLifeSettingsScreen(Screen parent) {
        super(Component.translatable("easylife.settings.title"));
        this.parent = parent;
        this.draft = EasyLifeConfig.get().copy();
    }

    @Override
    protected void init() {
        super.init();
        top = Math.max(34, (this.height - 327) / 2);
        columnWidth = Math.min(222, Math.max(110, (this.width - 64) / 2 - 9));
        columnStep = columnWidth + 16;
        left = (this.width - (2 * columnWidth + 16)) / 2;
        String[] values = draft.formValues();
        for (int i = 0; i < auctionFields.length; i++) {
            int x = left + (i % 2) * columnStep;
            int y = top + (i / 2) * 46;
            EditBox box = new EditBox(this.font, x, y + 16, columnWidth, 20, Component.translatable(LABEL_KEYS[i]));
            box.setMaxLength(i == 5 ? 32 : 18);
            box.setValue(values[i]);
            auctionFields[i] = this.addRenderableWidget(box);
        }

        autoUpdateButton = this.addRenderableWidget(Button.builder(updateButtonTitle(), button -> {
            draft.autoUpdate = !draft.autoUpdate;
            button.setMessage(updateButtonTitle());
        }).bounds(left, top + 157, columnWidth, 22).build());

        this.addRenderableWidget(Button.builder(Component.translatable("easylife.settings.check_updates"), button -> {
            EasyLifeUpdater.checkForUpdates(true);
        }).bounds(left + columnStep, top + 157, columnWidth, 22).build());

        delayField = new EditBox(this.font, left, top + 204, columnWidth, 20,
            Component.translatable("easylife.setting.delay"));
        delayField.setMaxLength(3);
        delayField.setValue(Integer.toString(draft.betweenListingsTicks));
        this.addRenderableWidget(delayField);

        this.addRenderableWidget(Button.builder(Component.translatable("easylife.settings.save"), button -> saveAndClose())
            .bounds(left, top + 278, columnWidth, 24).build());
        this.addRenderableWidget(Button.builder(Component.translatable("easylife.settings.cancel"), button -> onClose())
            .bounds(left + columnStep, top + 278, columnWidth, 24).build());
    }

    private Component updateButtonTitle() {
        return Component.translatable("easylife.setting.auto_update", Component.translatable(
            draft.autoUpdate ? "easylife.state.on" : "easylife.state.off"));
    }

    private void saveAndClose() {
        try {
            draft.startingPrice = auctionFields[0].getValue().trim();
            draft.minimumIncrement = auctionFields[1].getValue().trim();
            draft.buyoutPrice = auctionFields[2].getValue().trim();
            draft.durationSeconds = auctionFields[3].getValue().trim();
            draft.overtimeSeconds = auctionFields[4].getValue().trim();
            draft.startTime = auctionFields[5].getValue().trim();
            draft.betweenListingsTicks = Integer.parseInt(delayField.getValue().trim());
            EasyLifeConfig.save(draft);
            if (draft.autoUpdate) EasyLifeUpdater.checkForUpdates(false);
            onClose();
        } catch (RuntimeException ex) {
            warning = Component.translatable("easylife.settings.invalid", ex.getMessage());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Siamcraft gold-on-charcoal settings panel.
        int x0 = left - 14;
        int x1 = left + 2 * columnWidth + 30;
        int y0 = top - 36;
        int y1 = top + 313;
        graphics.fill(x0, y0, x1, y1, 0xE51B171C);
        graphics.outline(x0, y0, x1 - x0, y1 - y0, 0xFFFFA63E);
        graphics.fillGradient(x0 + 1, y0 + 1, x1 - 1, y0 + 5, 0xFFFFD67E, 0xFFE67F22);
        graphics.fill(x0 + 1, y1 - 29, x1 - 1, y1 - 28, 0x88F1A34F);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int titleY = Math.max(8, top - 25);
        graphics.centeredText(this.font, this.title, this.width / 2, titleY, 0xFFFFB342);
        graphics.text(this.font, Component.translatable("easylife.settings.module_auction"),
            left, top - 11, 0xFFFFD99A);

        for (int i = 0; i < auctionFields.length; i++) {
            int x = left + (i % 2) * columnStep;
            int y = top + (i / 2) * 46;
            graphics.text(this.font, Component.translatable(LABEL_KEYS[i]), x, y + 2, 0xFFEAEAEA);
        }
        graphics.text(this.font, Component.translatable("easylife.setting.delay"), left, top + 188, 0xFFEAEAEA);
        String version = FabricLoader.getInstance().getModContainer("scautoauction")
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("?");
        graphics.text(this.font, Component.translatable("easylife.settings.version", version),
            left + columnStep, top + 188, 0xFFAAAAAA);
        graphics.text(this.font, Component.translatable("easylife.settings.future"),
            left, top + 219, 0xFFB8C3D2);
        graphics.text(this.font, Component.translatable("easylife.settings.restart_hint"),
            left, top + 232, 0xFFB8C3D2);
        graphics.text(this.font, EasyLifeUpdater.getStatusText(), left, top + 247, 0xFFF5CE74);
        if (!warning.getString().isEmpty()) {
            graphics.centeredText(this.font, warning, this.width / 2, top + 263, 0xFFFF6666);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
