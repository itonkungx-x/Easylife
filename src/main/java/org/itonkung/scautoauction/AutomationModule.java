package org.itonkung.scautoauction;

import net.minecraft.client.Minecraft;

/** Independent automation module: reusable for any future Siamcraft workflow. */
public interface AutomationModule {
    String id();
    void initialize();
    void tick(Minecraft mc);
    void stop(Minecraft mc);
}
