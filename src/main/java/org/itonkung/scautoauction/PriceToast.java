package org.itonkung.scautoauction;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Client-only toast adapted from the archived ItemPriceExtractor v1.2.0
 * PriceMessages implementation. Visible even while /worth covers the chat.
 */
final class PriceToast {
    private static Object toastId;
    private static boolean warned;
    private PriceToast() {}

    static void show(String message) {
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Object client = mcClass.getMethod("getInstance").invoke(null);
            if (client == null) return;
            Object gui;
            try { gui = mcClass.getField("gui").get(client); }
            catch (NoSuchFieldException ignored) {
                Field f = mcClass.getDeclaredField("gui");
                f.setAccessible(true);
                gui = f.get(client);
            }
            if (gui == null) return;
            Object toastManager = gui.getClass().getMethod("toastManager").invoke(gui);
            if (toastManager == null) return;
            Class<?> cmp = Class.forName("net.minecraft.network.chat.Component");
            Class<?> idClass = Class.forName(
                "net.minecraft.client.gui.components.toasts.SystemToast$SystemToastId");
            Class<?> manager = Class.forName(
                "net.minecraft.client.gui.components.toasts.ToastManager");
            Class<?> toast = Class.forName(
                "net.minecraft.client.gui.components.toasts.SystemToast");
            Object title = cmp.getMethod("literal", String.class).invoke(null, "Siamcraft Price Scan");
            Object text = cmp.getMethod("literal", String.class).invoke(null, message);
            toast.getMethod("addOrUpdate", manager, idClass, cmp, cmp)
                .invoke(null, toastManager, getToastId(idClass), title, text);
        } catch (ReflectiveOperationException | SecurityException error) {
            if (!warned) {
                warned = true;
                System.err.println("[Easylife] Overlay toast unavailable: " + error);
            }
        }
    }

    private static Object getToastId(Class<?> type) throws ReflectiveOperationException {
        if (toastId != null) return toastId;
        for (Constructor<?> ctor : type.getDeclaredConstructors()) {
            try {
                ctor.setAccessible(true);
                if (ctor.getParameterCount() == 0) return toastId = ctor.newInstance();
                if (ctor.getParameterCount() == 1 && ctor.getParameterTypes()[0] == long.class)
                    return toastId = ctor.newInstance(2300L);
            } catch (ReflectiveOperationException | SecurityException ignored) {}
        }
        return toastId = type.getField("NARRATOR_TOGGLE").get(null);
    }
}
