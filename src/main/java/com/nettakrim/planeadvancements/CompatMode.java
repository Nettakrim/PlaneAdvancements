package com.nettakrim.planeadvancements;

import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum CompatMode {
    VANILLA,
    FULLSCREEN,
    PAGINATED,
    BETTER;

    private static @Nullable CompatMode current = null;

    private static final Logger LOGGER = LoggerFactory.getLogger("planecompatibility");

    public static @NotNull CompatMode getCompatMode() {
        if (current == null) {
            if (FabricLoader.getInstance().isModLoaded("betteradvancements")) {
                LOGGER.info("plane advancements compatibility: betteradvancements");
                current = CompatMode.BETTER;
            } else if (FabricLoader.getInstance().isModLoaded("paginatedadvancements")) {
                // TODO: compat with https://modrinth.com/mod/paginatedadvancements
                LOGGER.info("plane advancements compatibility: paginatedadvancements");
                current = CompatMode.PAGINATED;
            } else if (FabricLoader.getInstance().isModLoaded("advancements_fullscreen")) {
                LOGGER.info("plane advancements compatibility: advancements_fullscreen");
                current = CompatMode.FULLSCREEN;
            } else {
                LOGGER.info("plane advancements compatibility: vanilla");
                current = CompatMode.VANILLA;
            }
        }
        return current;
    }
}
