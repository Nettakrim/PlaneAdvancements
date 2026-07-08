package com.nettakrim.planeadvancements.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nettakrim.planeadvancements.AdvancementTabInterface;
import com.nettakrim.planeadvancements.AdvancementWidgetInterface;
import com.nettakrim.planeadvancements.PlaneAdvancementsClient;
import com.nettakrim.planeadvancements.TreeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

@SuppressWarnings("UnresolvedMixinReference")
@Pseudo
@Mixin(targets = "betteradvancements.common.gui.BetterAdvancementsScreen")
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

    @Inject(at = @At("HEAD"), method = "mouseClicked", cancellable = true)
    void click(MouseButtonEvent event, boolean isDoubleClick, CallbackInfoReturnable<Boolean> cir) {
        PlaneAdvancementsClient.draggedWidget = null;

        AdvancementTabInterface selectedTab = getSelectedTab();

        if (selectedTab == null || event.button() != 1) {
            PlaneAdvancementsClient.clearUIHover();
            if (PlaneAdvancementsClient.hoveredUI()) {
                super.mouseClicked(event, isDoubleClick);
                cir.setReturnValue(null);
            }
            return;
        }

        int left = SIDE + (width - internalWidth) / 2;
        int top = TOP + (height - internalHeight) / 2;

        double panX = selectedTab.planeAdvancements$getPanX();
        double panY = selectedTab.planeAdvancements$getPanY();
        int x = Mth.floor(event.x() - left - PADDING);
        int y = Mth.floor(event.y() - top - 2*PADDING);

        for (AdvancementWidgetInterface widget : selectedTab.planeAdvancements$getWidgets().values()) {
            if (widget.planeAdvancements$isHovering(panX, panY, x, y)) {
                PlaneAdvancementsClient.draggedWidget = widget;
                return;
            }
        }
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        PlaneAdvancementsClient.draggedWidget = null;
        return super.mouseReleased(event);
    }

    @Inject(at = @At("HEAD"), method = "mouseDragged", cancellable = true)
    void drag(MouseButtonEvent event, double mouseDeltaX, double mouseDeltaY, CallbackInfoReturnable<Boolean> cir) {
        if (PlaneAdvancementsClient.draggedWidget == null || PlaneAdvancementsClient.treeType != TreeType.SPRING) {
            if (PlaneAdvancementsClient.hoveredUI()) {
                cir.setReturnValue(super.mouseDragged(event, mouseDeltaX, mouseDeltaY));
            }
            return;
        }
        AdvancementTabInterface selectedTab = getSelectedTab();
        PlaneAdvancementsClient.draggedWidget.planeAdvancements$getTreePos().add((float)mouseDeltaX/BetterAdvancementsScreenAccessor.getZoom(), (float)mouseDeltaY/BetterAdvancementsScreenAccessor.getZoom());
        PlaneAdvancementsClient.draggedWidget.planeAdvancements$updatePos();
        assert selectedTab != null;
        selectedTab.planeAdvancements$heatGraph();
        cir.setReturnValue(true);
    }

    @Inject(at = @At("TAIL"), method = "extractRenderState")
    void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        PlaneAdvancementsClient.renderUI(graphics, mouseX, mouseY, a);
    }

    @ModifyExpressionValue(at = @At(value = "INVOKE", target = "Ljava/util/Map;size()I"), method = "renderWindow")
    int hideTabs(int original) {
        if (PlaneAdvancementsClient.isMergedAndSpring()) {
            return 0;
        }
        return original;
    }

    @Inject(at = @At("HEAD"), method = "extractRenderState")
    void merge(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        AdvancementTabInterface selectedTab = getSelectedTab();
        if(selectedTab != null) {
            if (PlaneAdvancementsClient.isMergedAndSpring()) {
                selectedTab.planeAdvancements$setMerged(tabs.values());
            } else {
                selectedTab.planeAdvancements$clearMerged(tabs.values());
            }
        }
    }

    @Inject(at = @At("TAIL"), method = "init")
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