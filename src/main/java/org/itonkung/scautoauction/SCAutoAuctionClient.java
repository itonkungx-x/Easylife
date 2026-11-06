package org.itonkung.scautoauction;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Easylife bootstrap: shared lifecycle, updater, and independently registered modules.
 * New automation systems only need to implement AutomationModule and register here.
 */
public final class SCAutoAuctionClient implements ClientModInitializer {
    private long updateCheckTicks;

    @Override
    public void onInitializeClient() {
        EasyLifeConfig.load();
        ModuleRegistry.register(new AuctionModule());
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
