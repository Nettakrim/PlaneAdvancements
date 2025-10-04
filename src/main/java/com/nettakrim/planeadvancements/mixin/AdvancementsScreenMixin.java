package com.nettakrim.planeadvancements.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nettakrim.planeadvancements.*;
import net.minecraft.advancement.*;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.advancement.AdvancementTab;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(AdvancementsScreen.class)
public abstract class AdvancementsScreenMixin extends Screen implements FullscreenInterface {
    @Shadow @Nullable private AdvancementTab selectedTab;

    @Shadow @Final private Map<AdvancementEntry, AdvancementTabInterface> tabs;

    protected AdvancementsScreenMixin(Text title) {
        super(title);
    }

    @Inject(at = @At("HEAD"), method = "mouseClicked", cancellable = true)
    void click(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (selectedTab == null || click.button() != 1) {
            PlaneAdvancementsClient.clearUIHover();
            if (PlaneAdvancementsClient.hoveredUI()) {
                super.mouseClicked(click, doubled);
                cir.setReturnValue(null);
            }
            return;
        }
        PlaneAdvancementsClient.draggedWidget = null;
        AdvancementTabInterface tab = (AdvancementTabInterface)selectedTab;

        double panX = tab.planeAdvancements$getPanX();
        double panY = tab.planeAdvancements$getPanY();
        int x;
        int y;
        if (CompatMode.getCompatMode() == CompatMode.FULLSCREEN) {
            x = MathHelper.floor(click.x()-((this.width - advancementsfullscreen$getWindowWidth(false)) >> 1));
            y = MathHelper.floor(click.y()-((this.height - advancementsfullscreen$getWindowHeight(false)) >> 1));
        } else {
            x = MathHelper.floor(click.x()-((this.width - 252) >> 1)-9);
            y = MathHelper.floor(click.y()-((this.height - 140) >> 1)-18);
        }

        for (AdvancementWidgetInterface widget : tab.planeAdvancements$getWidgets().values()) {
            if (widget.planeAdvancements$isHovering(panX, panY, x, y)) {
                PlaneAdvancementsClient.draggedWidget = widget;
                return;
            }
        }
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (PlaneAdvancementsClient.draggedWidget != null) {
            PlaneAdvancementsClient.draggedWidget = null;
        }
        return super.mouseReleased(click);
    }

    @Inject(at = @At("HEAD"), method = "mouseDragged", cancellable = true)
    void drag(Click click, double offsetX, double offsetY, CallbackInfoReturnable<Boolean> cir) {
        if (PlaneAdvancementsClient.draggedWidget == null || PlaneAdvancementsClient.treeType != TreeType.SPRING) {
            if (PlaneAdvancementsClient.selectedUI()) {
                cir.setReturnValue(super.mouseDragged(click, offsetX, offsetY));
            }
            return;
        }
        PlaneAdvancementsClient.draggedWidget.planeAdvancements$getTreePos().add((float)offsetX, (float)offsetY);
        PlaneAdvancementsClient.draggedWidget.planeAdvancements$updatePos();
        assert selectedTab != null;
        ((AdvancementTabInterface)selectedTab).planeAdvancements$heatGraph();
        cir.setReturnValue(true);
    }

    @ModifyExpressionValue(at = @At(value = "INVOKE", target = "Ljava/util/Map;size()I"), method = "drawWindow")
    int hideTabs(int original) {
        if (PlaneAdvancementsClient.isMergedAndSpring()) {
            return 0;
        }
        return original;
    }

    @Inject(at = @At("HEAD"), method = "render")
    void merge(DrawContext context, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        if(selectedTab != null) {
            if (PlaneAdvancementsClient.isMergedAndSpring()) {
                ((AdvancementTabInterface)selectedTab).planeAdvancements$setMerged(tabs.values());
            } else {
                ((AdvancementTabInterface)selectedTab).planeAdvancements$clearMerged(tabs.values());
            }
        }
    }

    @Inject(at = @At("TAIL"), method = "render")
    void render(DrawContext context, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        PlaneAdvancementsClient.renderUI(context, mouseX, mouseY, tickDelta);
    }

    @Inject(at = @At("TAIL"), method = "init")
    void init(CallbackInfo ci) {
        addSelectableChild(PlaneAdvancementsClient.treeButton);
        addSelectableChild(PlaneAdvancementsClient.repulsionSlider);
        addSelectableChild(PlaneAdvancementsClient.gridWidthSlider);
        addSelectableChild(PlaneAdvancementsClient.lineButton);
        addSelectableChild(PlaneAdvancementsClient.mergedButton);
    }
}
