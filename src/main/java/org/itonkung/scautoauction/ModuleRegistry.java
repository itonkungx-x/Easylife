package org.itonkung.scautoauction;

import net.minecraft.client.Minecraft;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared module lifecycle. New workflows can be added without modifying
 * existing auction logic. Registration order is deterministic.
 */
public final class ModuleRegistry {
    private static final Map<String, AutomationModule> MODULES = new LinkedHashMap<>();
    private ModuleRegistry() {}

    public static void register(AutomationModule module) {
        if (MODULES.putIfAbsent(module.id(), module) != null)
            throw new IllegalArgumentException("Duplicate Easylife module: " + module.id());
    }

    public static Collection<AutomationModule> all() {
        return Collections.unmodifiableCollection(MODULES.values());
    }

    public static void initialize() {
        MODULES.values().forEach(AutomationModule::initialize);
    }

    public static void tick(Minecraft mc) {
        MODULES.values().forEach(module -> module.tick(mc));
    }

    public static void stopAll(Minecraft mc) {
        MODULES.values().forEach(module -> module.stop(mc));
    }
}
