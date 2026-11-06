package org.itonkung.scautoauction;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.client.gui.screens.dialog.WaitingForResponseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Fail-closed auction queue assistant.
 * One occupied inventory slot (one whole stack) = one listing.
 * It never retries ambiguous submits or changes the inventory outside server clicks.
 */
public final class SCAutoAuctionClient implements ClientModInitializer {
    private static final String[] FORM_VALUES = {"100", "10%", "0", "60", "11", "after1m"};
    private static final int FORM_TIMEOUT = 160;
    private static final int ACCEPT_TIMEOUT = 200;
    private static final int SWITCH_WAIT = 8;
    private static final int INTER_ITEM_WAIT = 20;

    private static final KeyMapping TOGGLE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.scautoauction.toggle", InputConstants.Type.KEYBOARD, InputConstants.KEY_F8, KeyMapping.Category.MISC));
    private static final KeyMapping STOP = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.scautoauction.stop", InputConstants.Type.KEYBOARD, InputConstants.KEY_F9, KeyMapping.Category.MISC));

    private enum Phase { IDLE, NEXT, SWITCHING, OPENING, FILLING, ACCEPTING, COOLDOWN }
    private record Job(int slot, ItemStack stack) { }

    private final List<Job> jobs = new ArrayList<>();
    private Phase phase = Phase.IDLE;
    private int index;
    private int completed;
    private int ticks;
    private int emptyTicks;
    private int selectedSlot;
    private ItemStack heldExpected = ItemStack.EMPTY;
    private Screen activeDialog;
    private Object originalConnection;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(Minecraft mc) {
        while (STOP.consumeClick()) {
            if (phase != Phase.IDLE) halt(mc, "หยุดด้วย F9");
        }
        while (TOGGLE.consumeClick()) {
            if (phase == Phase.IDLE) start(mc);
            else halt(mc, "หยุดด้วย F8");
        }
        if (phase == Phase.IDLE) return;
        if (mc.player == null || mc.gameMode == null || mc.player.connection != originalConnection) {
            halt(mc, "การเชื่อมต่อเซิร์ฟเวอร์เปลี่ยน");
            return;
        }
        try {
            advance(mc);
        } catch (RuntimeException error) {
            halt(mc, "เกิดข้อผิดพลาด: " + error.getClass().getSimpleName());
        }
    }

    private void start(Minecraft mc) {
        if (mc.player == null || mc.gameMode == null || mc.gui.screen() != null) {
            message(mc, "ต้องอยู่ในเกมและปิดหน้าต่างอื่นก่อนกด F8");
            return;
        }
        if (mc.player.containerMenu != mc.player.inventoryMenu
            || !mc.player.containerMenu.getCarried().isEmpty()) {
            message(mc, "ปิด Container และวางไอเท็มที่ถือด้วยเมาส์กลับช่องก่อน");
            return;
        }

        Inventory inventory = mc.player.getInventory();
        int selected = inventory.getSelectedSlot();
        jobs.clear();
        // Exhaust the hotbar first, so later swaps always use an empty hotbar slot.
        if (!inventory.getItem(selected).isEmpty()) {
            jobs.add(new Job(selected, inventory.getItem(selected).copy()));
        }
        for (int i = 0; i < 9; i++) {
            if (i != selected && !inventory.getItem(i).isEmpty()) {
                jobs.add(new Job(i, inventory.getItem(i).copy()));
            }
        }
        for (int i = 9; i < 36; i++) {
            if (!inventory.getItem(i).isEmpty()) {
                jobs.add(new Job(i, inventory.getItem(i).copy()));
            }
        }
        if (jobs.isEmpty()) {
            message(mc, "ไม่พบไอเท็มใน Inventory หรือ Hotbar");
            return;
        }

        completed = 0;
        index = 0;
        ticks = 0;
        emptyTicks = 0;
        originalConnection = mc.player.connection;
        activeDialog = null;
        phase = Phase.NEXT;
        message(mc, "เริ่มลงประมูล " + jobs.size() + " ช่อง | F8/F9 หยุด");
    }

    private void advance(Minecraft mc) {
        Screen screen = mc.gui.screen();
        Inventory inventory = mc.player.getInventory();
        Job job = index < jobs.size() ? jobs.get(index) : null;
        switch (phase) {
            case NEXT -> {
                if (screen != null) { halt(mc, "มีหน้าต่างอื่นเปิดอยู่"); return; }
                if (job == null) {
                    int count = completed;
                    clear();
                    message(mc, "ครบ " + count + " รายการ ตรวจสอบคิวประมูลบนเซิร์ฟเวอร์อีกครั้ง");
                    return;
                }
                if (!ItemStack.matches(inventory.getItem(job.slot()), job.stack())) {
                    halt(mc, "ไอเท็มช่อง " + (job.slot() + 1) + " ไม่ตรงกับตอนเริ่ม");
                    return;
                }
                heldExpected = job.stack().copy();

                if (job.slot() < 9) {
                    inventory.setSelectedSlot(job.slot());
                    selectedSlot = job.slot();
                } else {
                    selectedSlot = inventory.getSelectedSlot();
                    if (!inventory.getItem(selectedSlot).isEmpty()) {
                        halt(mc, "Mainhand ไม่ว่างก่อนย้ายของ");
                        return;
                    }
                    // Minecraft 26.3 uses ContainerInput.SWAP instead of ClickType.
                    // Slot IDs 9..35 map directly to the non-hotbar inventory.
                    mc.gameMode.handleContainerInput(
                        mc.player.inventoryMenu.containerId,
                        job.slot(), selectedSlot, ContainerInput.SWAP, mc.player
                    );
                }
                ticks = 0;
                phase = Phase.SWITCHING;
            }
            case SWITCHING -> {
                if (screen != null) { halt(mc, "หน้าต่างแทรกขณะเลือกไอเท็ม"); return; }
                if (++ticks < SWITCH_WAIT) return;
                if (inventory.getSelectedSlot() != selectedSlot ||
                    !ItemStack.matches(inventory.getSelectedItem(), heldExpected)) {
                    halt(mc, "Mainhand ไม่ใช่ไอเท็มที่จะส่ง");
                    return;
                }
                if (job.slot() >= 9 && !inventory.getItem(job.slot()).isEmpty()) {
                    halt(mc, "ย้ายจาก Inventory ไม่สำเร็จ");
                    return;
                }
                mc.player.connection.sendCommand("auction queue add");
                ticks = 0;
                phase = Phase.OPENING;
            }
            case OPENING -> {
                if (screen == null) {
                    if (++ticks > FORM_TIMEOUT) halt(mc, "หมดเวลารอ Dialog ประมูล");
                    return;
                }
                if (!(screen instanceof DialogScreen<?>) || !auctionTitle(screen)) {
                    halt(mc, "หน้าที่เปิดไม่ใช่หน้าประมูลที่รองรับ");
                    return;
                }
                activeDialog = screen;
                phase = Phase.FILLING;
            }
            case FILLING -> {
                if (screen != activeDialog) {
                    halt(mc, "Dialog เปลี่ยนไปก่อนส่ง");
                    return;
                }
                List<EditBox> inputs = textBoxes(screen);
                if (inputs.size() != 6) {
                    halt(mc, "จำนวนช่องกรอกไม่ตรงภาพตัวอย่าง (พบ " + inputs.size() + ")");
                    return;
                }
                AbstractWidget vault = widgetWithText(screen, "vault");
                AbstractWidget override = widgetWithText(screen, "override");
                Button submit = buttonWithText(screen, "ส่งเข้าคิว");
                if (submit == null) submit = buttonWithText(screen, "submit");
                if (vault == null || override == null || submit == null || !submit.active) {
                    halt(mc, "ปุ่ม Vault / Override / ส่งเข้าคิว ไม่ครบ");
                    return;
                }
                String overrideLabel = override.getMessage().getString().toLowerCase(Locale.ROOT);
                if (!overrideLabel.contains("ปิด") && !overrideLabel.contains("off")) {
                    halt(mc, "Override ไม่ได้ปิดอยู่");
                    return;
                }
                for (int i = 0; i < FORM_VALUES.length; i++) {
                    inputs.get(i).setValue(FORM_VALUES[i]);
                    if (!FORM_VALUES[i].equals(inputs.get(i).getValue())) {
                        halt(mc, "เซิร์ฟเวอร์ไม่ยอมรับค่าช่อง " + (i + 1));
                        return;
                    }
                }
                // Vanilla Button#onPress ignores its InputWithModifiers argument.
                submit.onPress(null);
                ticks = 0;
                emptyTicks = 0;
                phase = Phase.ACCEPTING;
            }
            case ACCEPTING -> {
                if (++ticks > ACCEPT_TIMEOUT) {
                    halt(mc, "ไม่พบการนำไอเท็มออกหลังส่ง โปรดตรวจคิวก่อนเริ่มใหม่");
                    return;
                }
                if (screen != null && screen != activeDialog && !(screen instanceof DialogScreen<?>) && !(screen instanceof WaitingForResponseScreen)) {
                    halt(mc, "มีหน้าต่างแทรกหลังส่งประมูล");
                    return;
                }
                if (screen != null) { emptyTicks = 0; return; }
                if (inventory.getItem(selectedSlot).isEmpty()) {
                    if (++emptyTicks < 3) return;
                    completed++;
                    index++;
                    message(mc, "ไอเท็มถูกนำออกจากมือ " + completed + "/" + jobs.size());
                    activeDialog = null;
                    ticks = 0;
                    phase = Phase.COOLDOWN;
                } else {
                    emptyTicks = 0;
                }
            }
            case COOLDOWN -> {
                if (screen != null) { halt(mc, "หน้าต่างแทรกก่อนรายการถัดไป"); return; }
                if (++ticks >= INTER_ITEM_WAIT) {
                    ticks = 0;
                    phase = Phase.NEXT;
                }
            }
            case IDLE -> { }
        }
    }

    private static boolean auctionTitle(Screen screen) {
        String title = screen.getTitle().getString().toLowerCase(Locale.ROOT);
        return title.contains("ประมูล") || title.contains("auction");
    }

    /**
     * Minecraft 26.3 DialogScreen embeds its text fields inside a
     * ScrollableLayout.Container, which is itself a single top-level child.
     * Recurse through ContainerEventHandler.children() rather than reading
     * only Screen.children().
     */
    private static List<GuiEventListener> allControls(Screen screen) {
        List<GuiEventListener> results = new ArrayList<>();
        Set<GuiEventListener> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (GuiEventListener child : screen.children()) {
            collectControls(child, results, seen, 0);
        }
        return results;
    }

    private static void collectControls(GuiEventListener widget, List<GuiEventListener> results,
                                        Set<GuiEventListener> seen, int depth) {
        if (depth > 20 || !seen.add(widget)) return;
        results.add(widget);
        if (widget instanceof ContainerEventHandler container) {
            for (GuiEventListener nested : container.children()) {
                collectControls(nested, results, seen, depth + 1);
            }
        }
    }

    private static List<EditBox> textBoxes(Screen screen) {
        List<EditBox> fields = new ArrayList<>();
        for (GuiEventListener item : allControls(screen)) {
            if (item instanceof EditBox box) fields.add(box);
        }
        fields.sort(Comparator.comparingInt(EditBox::getY).thenComparingInt(EditBox::getX));
        return fields;
    }

    // Currency and Override may be widgets other than a vanilla Button.
    private static AbstractWidget widgetWithText(Screen screen, String text) {
        for (GuiEventListener item : allControls(screen)) {
            if (item instanceof AbstractWidget widget && widget.getMessage().getString()
                .toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT))) {
                return widget;
            }
        }
        return null;
    }

    private static Button buttonWithText(Screen screen, String text) {
        for (GuiEventListener item : allControls(screen)) {
            if (item instanceof Button button && button.getMessage().getString()
                .toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT))) {
                return button;
            }
        }
        return null;
    }

    private void halt(Minecraft mc, String reason) {
        int count = completed;
        int total = jobs.size();
        clear();
        message(mc, "หยุด: " + reason + " | ไอเท็มถูกนำออกแล้ว " + count + "/" + total);
    }

    private void clear() {
        phase = Phase.IDLE;
        jobs.clear();
        activeDialog = null;
        originalConnection = null;
        heldExpected = ItemStack.EMPTY;
        index = 0;
        ticks = 0;
        emptyTicks = 0;
    }

    private static void message(Minecraft mc, String text) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("[SC-AutoAuction] " + text));
        }
    }
}
