package org.itonkung.scautoauction.mixin;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import org.itonkung.scautoauction.PriceExtractorModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Exact container tooltip interception strategy from archived
 * ItemPriceExtractor v1.2.0, adapted to Easylife's PriceExtractorModule.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.AbstractContainerScreen")
public abstract class ContainerTooltipCaptureMixin {
    @Inject(method = "getTooltipFromContainerItem", at = @At("RETURN"), require = 0)
    private void easylife$afterContainerTooltip(ItemStack stack,
          CallbackInfoReturnable<List<Component>> cir) {
        PriceExtractorModule.observeRenderedTooltip(stack, cir.getReturnValue());
    }
}
