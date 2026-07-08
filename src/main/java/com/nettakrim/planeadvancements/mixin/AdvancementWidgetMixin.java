package com.nettakrim.planeadvancements.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nettakrim.planeadvancements.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

@Mixin(AdvancementWidget.class)
public abstract class AdvancementWidgetMixin implements AdvancementWidgetInterface {
    @Shadow @Final @Mutable
    private int x;
    @Shadow @Final @Mutable
    private int y;

    @Shadow @Nullable private AdvancementWidget parent;
    @Shadow @Final private List<AdvancementWidgetInterface> children;

    @Shadow @Final private DisplayInfo display;
    @Shadow @Final private AdvancementNode advancementNode;

    @Unique Vector2f defaultPos;
    @Unique Vector2f gridPos;
    @Unique TreePosition treePos;

    @Unique boolean isClusterRoot;

    @Shadow public abstract boolean isMouseOver(int xo, int yo, int mouseX, int mouseY);

    @Shadow public abstract void extractConnectivity(GuiGraphicsExtractor graphics, int xo, int yo, boolean background);

    @Inject(at = @At("TAIL"), method = "<init>")
    void initPos(AdvancementTab tab, Minecraft minecraft, AdvancementNode advancementNode, DisplayInfo display, CallbackInfo ci) {
        defaultPos = new Vector2f(x, y);
        gridPos = new Vector2f(x, y);
        treePos = PlaneAdvancementsClient.positions.computeIfAbsent(advancementNode.advancement(), _ -> new TreePosition());
    }

    @WrapMethod(method = "extractConnectivity")
    void renderLines(GuiGraphicsExtractor graphics, int xo, int yo, boolean background, Operation<Void> original) {
        // remove root lines for grid mode
        if (isClusterRoot && PlaneAdvancementsClient.treeType == TreeType.GRID) {
            for (AdvancementWidgetInterface advancementWidget : children) {
                advancementWidget.planeAdvancements$renderLines(graphics, xo, yo, background);
            }
            return;
        }

        if (PlaneAdvancementsClient.getCurrentLineType() == LineType.DEFAULT) {
            original.call(graphics, xo, yo, background);
            return;
        }

        if (parent != null) {
            AdvancementWidgetInterface.renderCustomLines(graphics, xo, yo, this.x, this.y, parent.getX(), parent.getY(), background, -1);
        }

        for (AdvancementWidgetInterface advancementWidget : children) {
            advancementWidget.planeAdvancements$renderLines(graphics, xo, yo, background);
        }
    }

    @ModifyReturnValue(at = @At("RETURN"), method = "isMouseOver")
    private boolean forceTooltipIfDragged(boolean original) {
        if (PlaneAdvancementsClient.draggedWidget != null) {
            return PlaneAdvancementsClient.draggedWidget == this;
        }
        return original;
    }

    @ModifyExpressionValue(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/advancements/AdvancementWidgetType;frameSprite(Lnet/minecraft/advancements/AdvancementType;)Lnet/minecraft/resources/Identifier;"), method = {"extractRenderState","extractHover"})
    private Identifier replaceMergeRoot(Identifier original) {
        if (PlaneAdvancementsClient.isMergedAndSpring() && parent == null) {
            return Identifier.fromNamespaceAndPath(PlaneAdvancementsClient.MOD_ID,"merged");
        }
        return original;
    }

    @Override
    public List<AdvancementWidgetInterface> planeAdvancements$getChildren() {
        return children;
    }

    @Override
    public AdvancementWidgetInterface planeAdvancements$getParent() {
        return (AdvancementWidgetInterface)parent;
    }

    @Override
    public void planeAdvancements$setParent(AdvancementWidgetInterface widget) {
        parent = (AdvancementWidget)widget;
    }

    @Override
    public void planeAdvancements$updatePos() {
        Vector2f pos = planeAdvancements$getCurrentPos();

        if (this.x != Mth.floor(pos.x) && this.x != Mth.ceil(pos.x)) {
            this.x = Math.round(pos.x);
        }
        if (this.y != Mth.floor(pos.y) && this.y != Mth.ceil(pos.y)) {
            this.y = Math.round(pos.y);
        }
    }

    @Override
    public Vector2f planeAdvancements$getDefaultPos() {
        return defaultPos;
    }

    @Override
    public Vector2f planeAdvancements$getTreePos() {
        return treePos.getCurrentPosition();
    }

    @Override
    public Vector2f planeAdvancements$getGridPos() {
        return gridPos;
    }

    @Override
    public boolean planeAdvancements$isHovering(double originX, double originY, int mouseX, int mouseY) {
        return isMouseOver((int)originX, (int)originY, mouseX, mouseY);
    }

    @Override
    public DisplayInfo planeAdvancements$getDisplay() {
        return display;
    }

    @Override
    public AdvancementNode planeAdvancements$getPlaced() {
        return advancementNode;
    }

    @Override
    public void planeAdvancements$setGridPos(Vector2f pos) {
        defaultPos.add(pos, gridPos);
        planeAdvancements$updatePos();

        for (AdvancementWidgetInterface child : children) {
            if (child.planeAdvancements$renderClusterLines()) {
                child.planeAdvancements$setGridPos(pos);
            }
        }
    }

    @Override
    public boolean planeAdvancements$isRoot() {
        return display.getX() == 0;
    }

    @Override
    public void planeAdvancements$setClusterRoot(boolean isClusterRoot) {
        this.isClusterRoot = isClusterRoot;
    }

    @Override
    public boolean planeAdvancements$renderClusterLines() {
        return !isClusterRoot;
    }

    @Override
    public int planeAdvancements$getX() {
        return x;
    }

    @Override
    public int planeAdvancements$getY() {
        return y;
    }

    @Override
    public void planeAdvancements$renderLines(GuiGraphicsExtractor graphics, int x, int y, boolean background) {
        extractConnectivity(graphics, x, y, background);
    }
}
