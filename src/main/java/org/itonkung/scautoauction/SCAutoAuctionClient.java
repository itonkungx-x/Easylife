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
 * It never retries ambiguous submits. Server queue submissions leave the item in the hand;
 * successful dialog closure is the completion signal, not item disappearance.
 */
public final class SCAutoAuctionClient implements ClientModInitializer {
    private static final int FORM_TIMEOUT = 160;
    private static final int ACCEPT_TIMEOUT = 200;
    private static final int SWITCH_WAIT = 8;
    private static final int SETTLE_TICKS = 5;

    private static final KeyMapping TOGGLE = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.scautoauction.toggle", InputConstants.Type.KEYBOARD, InputConstants.KEY_F8, KeyMapping.Category.MISC));
    private static final KeyMapping STOP = KeyMappingHelper.registerKeyMapping(new KeyMapping(
        "key.scautoauction.stop", InputConstants.Type.KEYBOARD, InputConstants.KEY_F9, KeyMapping.Category.MISC));

    private enum Phase { IDLE, NEXT, SWITCHING, OPENING, FILLING, ACCEPTING, RESTORING, COOLDOWN }
    private record Job(int slot, ItemStack stack) { }

    private final List<Job> jobs = new ArrayList<>();
    private Phase phase = Phase.IDLE;
    private long updateCheckTicks;
    private int index;
    private int completed;
    private int ticks;
    private int stableTicks;
    private boolean staged;
    private ItemStack displacedHotbarItem = ItemStack.EMPTY;
    private int selectedSlot;
    private ItemStack heldExpected = ItemStack.EMPTY;
    private Screen activeDialog;
    private Object originalConnection;

    @Override
    public void onInitializeClient() {
        EasyLifeConfig.load();
        EasyLifeUpdater.checkForUpdates(false);
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
        // Also check for updates every six hours when Minecraft stays open.
        if (++updateCheckTicks >= 20L * 60 * 60 * 6) {
            updateCheckTicks = 0;
            EasyLifeUpdater.checkForUpdates(false);
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
        // Register one listing per occupied hotbar/inventory slot; the server leaves items in place.
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
        stableTicks = 0;
        staged = false;
        displacedHotbarItem = ItemStack.EMPTY;
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
                staged = job.slot() >= 9;
                displacedHotbarItem = ItemStack.EMPTY;

                if (!staged) {
                    inventory.setSelectedSlot(job.slot());
                    selectedSlot = job.slot();
                } else {
                    selectedSlot = inventory.getSelectedSlot();
                    displacedHotbarItem = inventory.getItem(selectedSlot).copy();
                    // Slot IDs 9..35 are non-hotbar inventory slots.
                    // SWAP temporarily exchanges the selected hotbar item with this inventory slot.
                    // We restore the exact original positions after the queue dialog closes.
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
                if (staged && !ItemStack.matches(inventory.getItem(job.slot()), displacedHotbarItem)) {
                    halt(mc, "สลับ Inventory มา Mainhand ไม่ถูกต้อง");
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
                String[] values = EasyLifeConfig.get().formValues();
                for (int i = 0; i < values.length; i++) {
                    inputs.get(i).setValue(values[i]);
                    if (!values[i].equals(inputs.get(i).getValue())) {
                        halt(mc, "เซิร์ฟเวอร์ไม่ยอมรับค่าช่อง " + (i + 1));
                        return;
                    }
                }
                // Vanilla Button#onPress ignores its InputWithModifiers argument.
                submit.onPress(null);
                ticks = 0;
                stableTicks = 0;
                phase = Phase.ACCEPTING;
            }
            case ACCEPTING -> {
                if (++ticks > ACCEPT_TIMEOUT) {
                    halt(mc, "หมดเวลารอเซิร์ฟเวอร์ปิดหน้าประมูลหลังส่ง ตรวจคิวก่อนเริ่มใหม่");
                    return;
                }
                if (screen != null && screen != activeDialog
                    && !(screen instanceof DialogScreen<?>)
                    && !(screen instanceof WaitingForResponseScreen)) {
                    halt(mc, "มีหน้าต่างแทรกหลังส่งประมูล");
                    return;
                }
                // The auction server queues a snapshot, keeping the original held item.
                // Require the dialog to close and the original stack to remain stable.
                if (screen != null) {
                    stableTicks = 0;
                    return;
                }
                if (!ItemStack.matches(inventory.getItem(selectedSlot), heldExpected)) {
                    halt(mc, "ไอเท็มใน Mainhand เปลี่ยนหลังส่ง ตรวจคิวด้วยตนเอง");
                    return;
                }
                if (++stableTicks < SETTLE_TICKS) return;
                if (staged) {
                    if (!ItemStack.matches(inventory.getItem(job.slot()), displacedHotbarItem)) {
                        halt(mc, "ช่อง Inventory ถูกเปลี่ยนระหว่างส่งประมูล");
                        return;
                    }
                    mc.gameMode.handleContainerInput(
                        mc.player.inventoryMenu.containerId,
                        job.slot(), selectedSlot, ContainerInput.SWAP, mc.player
                    );
                    ticks = 0;
                    phase = Phase.RESTORING;
                } else {
                    finishJob(mc);
                }
            }
            case RESTORING -> {
                if (screen != null) {
                    halt(mc, "มีหน้าต่างแทรกระหว่างคืนไอเท็ม");
                    return;
                }
                if (++ticks < SWITCH_WAIT) return;
                if (!ItemStack.matches(inventory.getItem(job.slot()), job.stack())
                    || !ItemStack.matches(inventory.getItem(selectedSlot), displacedHotbarItem)) {
                    halt(mc, "คืนไอเท็มเข้าช่องเดิมไม่สำเร็จ โปรดตรวจ Inventory");
                    return;
                }
                finishJob(mc);
            }
            case COOLDOWN -> {
                if (screen != null) { halt(mc, "หน้าต่างแทรกก่อนรายการถัดไป"); return; }
                if (++ticks >= EasyLifeConfig.get().betweenListingsTicks) {
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

    private void finishJob(Minecraft mc) {
        completed++;
        index++;
        message(mc, "ส่งคำขอเข้าคิว " + completed + "/" + jobs.size()
            + " (ไอเท็มยังอยู่ในช่องเดิม)");
        activeDialog = null;
        displacedHotbarItem = ItemStack.EMPTY;
        heldExpected = ItemStack.EMPTY;
        staged = false;
        stableTicks = 0;
        ticks = 0;
        phase = Phase.COOLDOWN;
    }

    private void halt(Minecraft mc, String reason) {
        int count = completed;
        int total = jobs.size();
        // Restore a temporary inventory swap only when the game screen is closed
        // and both stacks still exactly match our expected positions.
        boolean restoredAttempted = false;
        if (staged && phase != Phase.RESTORING && mc.player != null
            && mc.gameMode != null && mc.gui.screen() == null
            && index < jobs.size()) {
            Inventory inventory = mc.player.getInventory();
            Job job = jobs.get(index);
            if (inventory.getSelectedSlot() == selectedSlot
                && ItemStack.matches(inventory.getItem(selectedSlot), heldExpected)
                && ItemStack.matches(inventory.getItem(job.slot()), displacedHotbarItem)) {
                mc.gameMode.handleContainerInput(
                    mc.player.inventoryMenu.containerId,
                    job.slot(), selectedSlot, ContainerInput.SWAP, mc.player
                );
                restoredAttempted = true;
            }
        }
        boolean tempSwap = staged;
        clear();
        message(mc, "หยุด: " + reason + " | ส่งคำขอแล้ว " + count + "/" + total
            + (tempSwap ? (restoredAttempted
                ? " | ส่งคำสั่งคืนช่องเดิมแล้ว กรุณาตรวจ Inventory"
                : " | ตรวจ Inventory เพราะอาจมีไอเท็มที่สลับช่องชั่วคราว") : ""));
    }

    private void clear() {
        phase = Phase.IDLE;
        jobs.clear();
        activeDialog = null;
        originalConnection = null;
        heldExpected = ItemStack.EMPTY;
        index = 0;
        ticks = 0;
        stableTicks = 0;
        staged = false;
        displacedHotbarItem = ItemStack.EMPTY;
    }

    private static void message(Minecraft mc, String text) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("[Easylife] " + text));
        }
    }
}
