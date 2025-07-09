package com.nettakrim.planeadvancements.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.nettakrim.planeadvancements.AdvancementTabInterface;
import com.nettakrim.planeadvancements.AdvancementWidgetInterface;
import com.nettakrim.planeadvancements.PlaneAdvancementsClient;
import com.nettakrim.planeadvancements.TreeType;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.Map;

@SuppressWarnings("UnresolvedMixinReference")
@Pseudo
@Mixin(targets = "betteradvancements.common.gui.BetterAdvancementsScreen", remap = false)
public class BetterAdvancementsScreenMixin extends Screen {
    @Shadow private float zoom;
    @Shadow private int internalWidth;
    @Shadow private int internalHeight;

    @Shadow private static int SIDE;
    @Shadow private static int TOP;
    @Shadow private static int PADDING;

    @Shadow @Final
    private Map<AdvancementEntry, AdvancementTabInterface> tabs;

    @Unique private static Field selectedTabField;

    protected BetterAdvancementsScreenMixin(Text title) {
        super(title);
    }

    @Inject(at = @At("HEAD"), method = {"mouseClicked", "method_25402"}, cancellable = true, remap = true)
    void click(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        AdvancementTabInterface selectedTab = getSelectedTab();

        if (selectedTab == null || button != 1) {
            PlaneAdvancementsClient.clearUIHover();
            if (PlaneAdvancementsClient.hoveredUI()) {
                super.mouseClicked(mouseX, mouseY, button);
                cir.setReturnValue(null);
            }
            return;
        }
        PlaneAdvancementsClient.draggedWidget = null;

        int left = SIDE + (width - internalWidth) / 2;
        int top = TOP + (height - internalHeight) / 2;

        double panX = selectedTab.planeAdvancements$getPanX();
        double panY = selectedTab.planeAdvancements$getPanY();
        int x = MathHelper.floor(mouseX - left - PADDING);
        int y = MathHelper.floor(mouseY - top - 2*PADDING);

        for (AdvancementWidgetInterface widget : selectedTab.planeAdvancements$getWidgets().values()) {
            if (widget.planeAdvancements$isHovering(panX, panY, x, y)) {
                PlaneAdvancementsClient.draggedWidget = widget;
                return;
            }
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (PlaneAdvancementsClient.draggedWidget != null) {
            PlaneAdvancementsClient.draggedWidget = null;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Inject(at = @At("HEAD"), method = {"mouseDragged", "method_25403"}, cancellable = true, remap = true)
    void drag(double mouseX, double mouseY, int button, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (PlaneAdvancementsClient.draggedWidget == null || PlaneAdvancementsClient.treeType != TreeType.SPRING) {
            if (PlaneAdvancementsClient.selectedUI()) {
                cir.setReturnValue(super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY));
            }
            return;
        }
        AdvancementTabInterface selectedTab = getSelectedTab();
        PlaneAdvancementsClient.draggedWidget.planeAdvancements$getTreePos().add((float)deltaX/this.zoom, (float)deltaY/this.zoom);
        PlaneAdvancementsClient.draggedWidget.planeAdvancements$updatePos();
        assert selectedTab != null;
        selectedTab.planeAdvancements$heatGraph();
        cir.setReturnValue(true);
    }

    @Inject(at = @At("TAIL"), method = {"render", "method_25394"}, remap = true)
    void render(DrawContext context, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
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
    void merge(DrawContext context, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
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
        addSelectableChild(PlaneAdvancementsClient.treeButton);
        addSelectableChild(PlaneAdvancementsClient.repulsionSlider);
        addSelectableChild(PlaneAdvancementsClient.gridWidthSlider);
        addSelectableChild(PlaneAdvancementsClient.lineButton);
        addSelectableChild(PlaneAdvancementsClient.mergedButton);
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