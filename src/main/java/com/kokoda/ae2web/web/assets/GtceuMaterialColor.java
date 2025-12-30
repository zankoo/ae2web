package com.kokoda.ae2web.web.assets;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Extracts material color from GTCEu items via reflection.
 * Falls back gracefully when GTCEu is not installed.
 */
public final class GtceuMaterialColor {
    private static final String CHEMICAL_HELPER_CLASS = "com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper";

    private static volatile boolean resolved;
    private static volatile @Nullable Method getMaterialMethod;
    private static volatile @Nullable Method getMaterialRgbMethod;

    private GtceuMaterialColor() {}

    /**
     * Try to extract material RGB color from a GTCEu item.
     * Returns hex color string (e.g., "FFE650") or null if not applicable.
     */
    public static @Nullable String tryExtract(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        Method method = resolveGetMaterialMethod();
        if (method == null) {
            return null;
        }

        try {
            Object material = method.invoke(null, stack);
            if (material == null) {
                return null;
            }

            Method rgbMethod = resolveGetMaterialRgbMethod(material);
            if (rgbMethod == null) {
                return null;
            }

            Object rgbValue = rgbMethod.invoke(material);
            if (!(rgbValue instanceof Integer rgb)) {
                return null;
            }

            return String.format("%06X", rgb & 0xFFFFFF);
        } catch (Throwable t) {
            return null;
        }
    }

    private static @Nullable Method resolveGetMaterialRgbMethod(Object material) {
        Method cached = getMaterialRgbMethod;
        if (cached != null && cached.getDeclaringClass().isInstance(material)) {
            return cached;
        }

        synchronized (GtceuMaterialColor.class) {
            cached = getMaterialRgbMethod;
            if (cached != null && cached.getDeclaringClass().isInstance(material)) {
                return cached;
            }

            try {
                getMaterialRgbMethod = material.getClass().getMethod("getMaterialRGB");
            } catch (Throwable t) {
                getMaterialRgbMethod = null;
            }

            return getMaterialRgbMethod;
        }
    }

    private static @Nullable Method resolveGetMaterialMethod() {
        if (resolved) {
            return getMaterialMethod;
        }

        synchronized (GtceuMaterialColor.class) {
            if (resolved) {
                return getMaterialMethod;
            }

            try {
                Class<?> helperClass = Class.forName(CHEMICAL_HELPER_CLASS);
                getMaterialMethod = helperClass.getMethod("getMaterial", ItemStack.class);
                try {
                    getMaterialRgbMethod = getMaterialMethod.getReturnType().getMethod("getMaterialRGB");
                } catch (Throwable t) {
                    getMaterialRgbMethod = null;
                }
            } catch (ClassNotFoundException e) {
                getMaterialMethod = null;
            } catch (Throwable t) {
                getMaterialMethod = null;
            } finally {
                resolved = true;
            }

            return getMaterialMethod;
        }
    }
}
