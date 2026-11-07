package org.itonkung.scautoauction.mixin;

import java.util.List;

import org.itonkung.scautoauction.PriceExtractorModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Observe the tooltip returned to Minecraft at hover time.
 * This is read-only: it never changes the tooltip, inventory or server GUI.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerTooltipCaptureMixin {
    @Inject(method = "getTooltipFromContainerItem", at = @At("RETURN"), require = 0)
    private void easylife$captureVisiblePriceTooltip(ItemStack stack,
            CallbackInfoReturnable<List<Component>> result) {
        PriceExtractorModule.observeRenderedTooltip(stack, result.getReturnValue());
    }
}
