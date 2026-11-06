package org.itonkung.scautoauction;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Shared Easylife launcher. Each workflow owns its state and UI handlers.
 */
public final class SCAutoAuctionClient implements ClientModInitializer {
    private long updateCheckTicks;

    @Override
    public void onInitializeClient() {
        EasyLifeConfig.load();
        EasyLifeSystemConfig.load();

        ModuleRegistry.register(new AuctionModule());
        ModuleRegistry.register(new PriceExtractorModule());
        ModuleRegistry.initialize();

        EasyLifeUpdater.checkForUpdates(false);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (++updateCheckTicks >= 20L * 60 * 60 * 6) {
                updateCheckTicks = 0;
                EasyLifeUpdater.checkForUpdates(false);
            }
            ModuleRegistry.tick(mc);
        });
    }
}
