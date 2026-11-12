package com.supercolourfulchat.compat;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

public final class ViaVersionBridge {

    private static final int PROTOCOL_1_16 = 735;

    private Object api;
    private Method byUuid;
    private Method byPlayer;
    private volatile boolean available;

    public void init() {
        shutdown();
        Class<?> viaClass = findClass("com.viaversion.viaversion.api.Via");
        if (viaClass == null) {
            viaClass = findClass("us.myles.ViaVersion.api.Via");
        }
        if (viaClass == null) {
            return;
        }
        try {
            Object apiInstance = viaClass.getMethod("getAPI").invoke(null);
            if (apiInstance == null) {
                return;
            }
            Class<?> apiClass = apiInstance.getClass();
            Method uuidMethod = findMethod(apiClass, "getPlayerVersion", UUID.class);
            Method playerMethod = findMethod(apiClass, "getPlayerVersion", Player.class);
            if (uuidMethod == null && playerMethod == null) {
                return;
            }
            this.api = apiInstance;
            this.byUuid = uuidMethod;
            this.byPlayer = playerMethod;
            this.available = true;
        } catch (Throwable ignored) {
            shutdown();
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isBelow116(Player player) {
        if (!available || player == null) {
            return false;
        }
        try {
            Object result;
            if (byUuid != null) {
                result = byUuid.invoke(api, player.getUniqueId());
            } else {
                result = byPlayer.invoke(api, player);
            }
            return result instanceof Integer && (Integer) result < PROTOCOL_1_16;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void shutdown() {
        available = false;
        api = null;
        byUuid = null;
        byPlayer = null;
    }

    private static Class<?> findClass(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?> param) {
        try {
            Method method = clazz.getMethod(name, param);
            try {
                method.setAccessible(true);
            } catch (Throwable ignored) {
            }
            return method;
        } catch (Throwable ignored) {
            return null;
        }
    }
}