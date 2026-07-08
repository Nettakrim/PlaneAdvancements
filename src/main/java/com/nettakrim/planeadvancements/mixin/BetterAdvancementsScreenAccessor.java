package com.nettakrim.planeadvancements.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "betteradvancements.common.gui.BetterAdvancementsScreen")
public interface BetterAdvancementsScreenAccessor {
    @Accessor("zoom")
    static float getZoom() {
        throw new AssertionError("Replaced By Mixin");
    }
}
