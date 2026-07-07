package com.nettakrim.planeadvancements.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nettakrim.planeadvancements.AdvancementTabInterface;
import com.nettakrim.planeadvancements.AdvancementWidgetInterface;
import com.nettakrim.planeadvancements.PlaneAdvancementsClient;
import com.nettakrim.planeadvancements.TreeType;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

@SuppressWarnings("UnresolvedMixinReference")
@Pseudo
@Mixin(targets = "betteradvancements.common.gui.BetterAdvancementsScreen", remap = false)
public class BetterAdvancementsScreenMixin extends Screen {
    @Shadow private int internalWidth;
    @Shadow private int internalHeight;

    @Shadow private static int SIDE;
    @Shadow private static int TOP;
    @Shadow private static int PADDING;

    @Shadow @Final
    private Map<AdvancementHolder, AdvancementTabInterface> tabs;

    @Unique private static Field selectedTabField;

    protected BetterAdvancementsScreenMixin(Component title) {
        super(title);
    }

    @Inject(at = @At("HEAD"), method = {"mouseClicked", "method_25402"}, cancellable = true, remap = true)
    void click(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        PlaneAdvancementsClient.draggedWidget = null;

        AdvancementTabInterface selectedTab = getSelectedTab();

        if (selectedTab == null || click.button() != 1) {
            PlaneAdvancementsClient.clearUIHover();
            if (PlaneAdvancementsClient.hoveredUI()) {
                super.mouseClicked(click, doubled);
                cir.setReturnValue(null);
            }
            return;
        }

        int left = SIDE + (width - internalWidth) / 2;
        int top = TOP + (height - internalHeight) / 2;

        double panX = selectedTab.planeAdvancements$getPanX();
        double panY = selectedTab.planeAdvancements$getPanY();
        int x = Mth.floor(click.x() - left - PADDING);
        int y = Mth.floor(click.y() - top - 2*PADDING);

        for (AdvancementWidgetInterface widget : selectedTab.planeAdvancements$getWidgets().values()) {
            if (widget.planeAdvancements$isHovering(panX, panY, x, y)) {
                PlaneAdvancementsClient.draggedWidget = widget;
                return;
            }
        }
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent click) {
        PlaneAdvancementsClient.draggedWidget = null;
        return super.mouseReleased(click);
    }

    @Inject(at = @At("HEAD"), method = {"mouseDragged", "method_25403"}, cancellable = true, remap = true)
    void drag(MouseButtonEvent click, double offsetX, double offsetY, CallbackInfoReturnable<Boolean> cir) {
        if (PlaneAdvancementsClient.draggedWidget == null || PlaneAdvancementsClient.treeType != TreeType.SPRING) {
            if (PlaneAdvancementsClient.hoveredUI()) {
                cir.setReturnValue(super.mouseDragged(click, offsetX, offsetY));
            }
            return;
        }
        AdvancementTabInterface selectedTab = getSelectedTab();
        PlaneAdvancementsClient.draggedWidget.planeAdvancements$getTreePos().add((float)offsetX/BetterAdvancementsScreenAccessor.getZoom(), (float)offsetY/BetterAdvancementsScreenAccessor.getZoom());
        PlaneAdvancementsClient.draggedWidget.planeAdvancements$updatePos();
        assert selectedTab != null;
        selectedTab.planeAdvancements$heatGraph();
        cir.setReturnValue(true);
    }

    @Inject(at = @At("TAIL"), method = {"render", "method_25394"}, remap = true)
    void render(GuiGraphics context, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        PlaneAdvancementsClient.renderUI(context, mouseX, mouseY, tickDelta);
    }

    @ModifyExpressionValue(at = @At(value = "INVOKE", target = "Ljava/util/Map;size()I"), method = "renderWindow", remap = true)
    int hideTabs(int original) {
        if (PlaneAdvancementsClient.isMergedAndSpring()) {
            return 0;
        }
        return original;
    }

    @Inject(at = @At("HEAD"), method = {"render", "method_25394"}, remap = true)
    void merge(GuiGraphics context, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        AdvancementTabInterface selectedTab = getSelectedTab();
        if(selectedTab != null) {
            if (PlaneAdvancementsClient.isMergedAndSpring()) {
                selectedTab.planeAdvancements$setMerged(tabs.values());
            } else {
                selectedTab.planeAdvancements$clearMerged(tabs.values());
            }
        }
    }

    @Inject(at = @At("TAIL"), method = {"init", "method_25426"}, remap = true)
    void init(CallbackInfo ci) {
        addWidget(PlaneAdvancementsClient.treeButton);
        addWidget(PlaneAdvancementsClient.repulsionSlider);
        addWidget(PlaneAdvancementsClient.gridWidthSlider);
        addWidget(PlaneAdvancementsClient.lineButton);
        addWidget(PlaneAdvancementsClient.mergedButton);
    }

    @Unique
    private AdvancementTabInterface getSelectedTab() {
        try {
            if (selectedTabField == null) {
                selectedTabField = this.getClass().getDeclaredField("selectedTab");
            }
            return (AdvancementTabInterface)selectedTabField.get(this);
        } catch (Exception ignored) {
            return null;
        }
    }
}