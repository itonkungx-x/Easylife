package org.itonkung.scautoauction.mixin;

import net.minecraft.client.input.KeyEvent;
import org.itonkung.scautoauction.PriceExtractorModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional SDL keyboard hook adapted from ItemPriceExtractor v1.2.0.
 * Only handles Easylife's configured hotkey within /worth; other keys pass through.
 */
@Mixin(targets = "net.minecraft.client.KeyboardHandler")
public abstract class WorthKeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), require = 0)
    private void easylife$onKeyPress(long window, int action, KeyEvent event,
                                    CallbackInfo ci) {
        PriceExtractorModule.onRawKeyPress(action, event);
    }
}
