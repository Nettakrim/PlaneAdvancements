package com.nettakrim.planeadvancements.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nettakrim.planeadvancements.*;
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
import net.minecraft.client.gui.GuiGraphics;
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

    @Shadow public abstract boolean isMouseOver(int originX, int originY, int mouseX, int mouseY);

    @Shadow public abstract void drawConnectivity(GuiGraphics context, int x, int y, boolean border);

    @Inject(at = @At("TAIL"), method = "<init>")
    void initPos(AdvancementTab tab, Minecraft client, AdvancementNode advancement, DisplayInfo display, CallbackInfo ci) {
        defaultPos = new Vector2f(x, y);
        gridPos = new Vector2f(x, y);
        treePos = PlaneAdvancementsClient.positions.computeIfAbsent(advancement.advancement(), k -> new TreePosition());
    }

    @WrapMethod(method = "drawConnectivity")
    void renderLines(GuiGraphics context, int x, int y, boolean border, Operation<Void> original) {
        // remove root lines for grid mode
        if (isClusterRoot && PlaneAdvancementsClient.treeType == TreeType.GRID) {
            for (AdvancementWidgetInterface advancementWidget : children) {
                advancementWidget.planeAdvancements$renderLines(context, x, y, border);
            }
            return;
        }

        if (PlaneAdvancementsClient.getCurrentLineType() == LineType.DEFAULT) {
            original.call(context, x, y, border);
            return;
        }

        if (parent != null) {
            AdvancementWidgetInterface.renderCustomLines(context, x, y, this.x, this.y, parent.getX(), parent.getY(), border, -1);
        }

        for (AdvancementWidgetInterface advancementWidget : children) {
            advancementWidget.planeAdvancements$renderLines(context, x, y, border);
        }
    }

    @ModifyReturnValue(at = @At("RETURN"), method = "isMouseOver")
    private boolean forceTooltipIfDragged(boolean original) {
        if (PlaneAdvancementsClient.draggedWidget != null) {
            return PlaneAdvancementsClient.draggedWidget == this;
        }
        return original;
    }

    @ModifyExpressionValue(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/advancements/AdvancementWidgetType;frameSprite(Lnet/minecraft/advancements/AdvancementType;)Lnet/minecraft/resources/Identifier;"), method = {"draw","drawHover"})
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
    public void planeAdvancements$renderLines(GuiGraphics context, int x, int y, boolean border) {
        drawConnectivity(context, x, y, border);
    }
}
