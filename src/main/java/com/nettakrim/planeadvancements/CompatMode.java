package com.nettakrim.planeadvancements;

import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum CompatMode {
    VANILLA,
    FULLSCREEN,
    PAGINATED,
    BETTER;

    private static @Nullable CompatMode current = null;

    public static @NotNull CompatMode getCompatMode() {
        if (current == null) {
            if (FabricLoader.getInstance().isModLoaded("betteradvancements")) {
                current = CompatMode.BETTER;
            } else if (FabricLoader.getInstance().isModLoaded("paginatedadvancements")) {
                // TODO: compat with https://modrinth.com/mod/paginatedadvancements
                current = CompatMode.PAGINATED;
            } else if (FabricLoader.getInstance().isModLoaded("advancements_fullscreen")) {
                current = CompatMode.FULLSCREEN;
            } else {
                current = CompatMode.VANILLA;
            }
        }
        return current;
    }
}
