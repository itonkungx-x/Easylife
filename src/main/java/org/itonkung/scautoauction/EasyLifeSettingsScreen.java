package org.itonkung.scautoauction;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
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
        left = this.width / 2 - 230;
        String[] values = draft.formValues();
        for (int i = 0; i < auctionFields.length; i++) {
            int x = left + (i % 2) * 240;
            int y = top + (i / 2) * 46;
            EditBox box = new EditBox(this.font, x, y + 16, 222, 20, Component.translatable(LABEL_KEYS[i]));
            box.setMaxLength(i == 5 ? 32 : 18);
            box.setValue(values[i]);
            auctionFields[i] = this.addRenderableWidget(box);
        }

        autoUpdateButton = this.addRenderableWidget(Button.builder(updateButtonTitle(), button -> {
            draft.autoUpdate = !draft.autoUpdate;
            button.setMessage(updateButtonTitle());
        }).bounds(left, top + 157, 222, 22).build());

        this.addRenderableWidget(Button.builder(Component.translatable("easylife.settings.check_updates"), button -> {
            EasyLifeUpdater.checkForUpdates(true);
        }).bounds(left + 240, top + 157, 222, 22).build());

        delayField = new EditBox(this.font, left, top + 204, 222, 20,
            Component.translatable("easylife.setting.delay"));
        delayField.setMaxLength(3);
        delayField.setValue(Integer.toString(draft.betweenListingsTicks));
        this.addRenderableWidget(delayField);

        this.addRenderableWidget(Button.builder(Component.translatable("easylife.settings.save"), button -> saveAndClose())
            .bounds(left, top + 278, 222, 24).build());
        this.addRenderableWidget(Button.builder(Component.translatable("easylife.settings.cancel"), button -> onClose())
            .bounds(left + 240, top + 278, 222, 24).build());
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);
        super.render(graphics, mouseX, mouseY, delta);
        int titleY = Math.max(8, top - 25);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, titleY, 0xFFFFB342);

        for (int i = 0; i < auctionFields.length; i++) {
            int x = left + (i % 2) * 240;
            int y = top + (i / 2) * 46;
            graphics.drawString(this.font, Component.translatable(LABEL_KEYS[i]), x, y + 2, 0xFFEAEAEA);
        }
        graphics.drawString(this.font, Component.translatable("easylife.setting.delay"), left, top + 188, 0xFFEAEAEA);
        String version = FabricLoader.getInstance().getModContainer("scautoauction")
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("?");
        graphics.drawString(this.font, Component.translatable("easylife.settings.version", version),
            left + 240, top + 188, 0xFFAAAAAA);
        graphics.drawString(this.font, Component.translatable("easylife.settings.restart_hint"),
            left, top + 232, 0xFFB8C3D2);
        graphics.drawString(this.font, EasyLifeUpdater.getStatusText(), left, top + 247, 0xFFF5CE74);
        if (!warning.getString().isEmpty()) {
            graphics.drawCenteredString(this.font, warning, this.width / 2, top + 263, 0xFFFF6666);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
