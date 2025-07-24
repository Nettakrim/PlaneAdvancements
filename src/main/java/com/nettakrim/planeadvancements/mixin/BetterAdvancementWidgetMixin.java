package com.nettakrim.planeadvancements.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nettakrim.planeadvancements.*;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@SuppressWarnings("UnresolvedMixinReference")
@Pseudo
@Mixin(targets = "betteradvancements.common.gui.BetterAdvancementWidget", remap = false)
public abstract class BetterAdvancementWidgetMixin implements AdvancementWidgetInterface {
    @Shadow
    private int x;
    @Shadow
    private int y;

    @Unique @Nullable
    private AdvancementWidgetInterface parent;
    @Shadow @Final private List<AdvancementWidgetInterface> children;

    @Shadow @Final private AdvancementDisplay displayInfo;
    @Shadow @Final private PlacedAdvancement advancementNode;

    @Unique Vector2f defaultPos;
    @Unique Vector2f gridPos;
    @Unique TreePosition treePos;

    @Unique boolean isClusterRoot;

    @Shadow private AdvancementProgress advancementProgress;
    @SuppressWarnings("ReferenceToMixin") // perhaps because it is Pseudo, it gives a warning despite being an Accessor
    @Unique protected BetterDisplayInfoAccessor betterDisplayInfoAccessor;

    @Shadow public abstract boolean isMouseOver(double scrollX, double scrollY, double mouseX, double mouseY);

    @Shadow public abstract void drawConnectivity(DrawContext context, int x, int y, boolean border);

    @Inject(at = @At("TAIL"), method = "<init>", remap = true)
    void initPos(@Coerce AdvancementTabInterface tab, MinecraftClient client, PlacedAdvancement advancement, AdvancementDisplay display, CallbackInfo ci) {
        defaultPos = new Vector2f(x, y);
        gridPos = new Vector2f(x, y);
        treePos = PlaneAdvancementsClient.positions.computeIfAbsent(advancement.getAdvancement(), k -> new TreePosition());

        try {
            //noinspection ReferenceToMixin
            betterDisplayInfoAccessor = (BetterDisplayInfoAccessor)this.getClass().getDeclaredField("betterDisplayInfo").get(this);
        } catch (Exception ignored) {}
    }

    @WrapMethod(method = "drawConnectivity", remap = true)
    private void removeGridRoots(DrawContext context, int x, int y, boolean border, Operation<Void> original) {
        // remove root lines for grid mode
        if (isClusterRoot && PlaneAdvancementsClient.treeType == TreeType.GRID) {
            for (AdvancementWidgetInterface advancementWidget : children) {
                advancementWidget.planeAdvancements$renderLines(context, x, y, border);
            }
            return;
        }

        original.call(context, x, y, border);
    }

    @WrapMethod(method = "drawConnection", remap = true)
    private void drawLines(DrawContext context, @Coerce AdvancementWidgetInterface parent, int x, int y, boolean border, Operation<Void> original) {
        if (PlaneAdvancementsClient.getCurrentLineType() == LineType.DEFAULT) {
            original.call(context, parent, x, y, border);
            return;
        }

        if (parent != null) {
            int innerColor = advancementProgress != null && advancementProgress.isDone() ? betterDisplayInfoAccessor.callGetCompletedLineColor() : betterDisplayInfoAccessor.callGetUnCompletedLineColor();
            AdvancementWidgetInterface.renderCustomLines(context, x, y, this.x, this.y, parent.planeAdvancements$getX(), parent.planeAdvancements$getY(), border, innerColor);
        }
    }

    @ModifyReturnValue(at = @At("RETURN"), method = "isMouseOver", remap = true)
    private boolean forceTooltipIfDragged(boolean original) {
        if (PlaneAdvancementsClient.draggedWidget != null) {
            return PlaneAdvancementsClient.draggedWidget == this;
        }
        return original;
    }

    @ModifyExpressionValue(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/advancement/AdvancementObtainedStatus;getFrameTexture(Lnet/minecraft/advancement/AdvancementFrame;)Lnet/minecraft/util/Identifier;"), method = {"draw","drawHover"}, remap = true)
    private Identifier replaceMergeRoot(Identifier original) {
        if (PlaneAdvancementsClient.isMergedAndSpring() && parent == null) {
            return Identifier.of(PlaneAdvancementsClient.MOD_ID,"merged");
        }
        return original;
    }

    @Inject(at = @At("TAIL"), method = "attachToParent", remap = true)
    private void setParent(CallbackInfo ci) {
        try {
            parent = (AdvancementWidgetInterface)this.getClass().getDeclaredField("parent").get(this);
        } catch (Exception ignored) {}
    }

    @Override
    public List<AdvancementWidgetInterface> planeAdvancements$getChildren() {
        return children;
    }

    @Override
    public AdvancementWidgetInterface planeAdvancements$getParent() {
        return parent;
    }

    @Override
    public void planeAdvancements$setParent(AdvancementWidgetInterface widget) {
        try {
            this.getClass().getDeclaredField("parent").set(this, widget);
            parent = widget;
        } catch (Exception ignored) {}
    }

    @Override
    public void planeAdvancements$updatePos() {
        Vector2f pos = planeAdvancements$getCurrentPos();

        if (this.x != MathHelper.floor(pos.x) && this.x != MathHelper.ceil(pos.x)) {
            this.x = Math.round(pos.x);
        }
        if (this.y != MathHelper.floor(pos.y) && this.y != MathHelper.ceil(pos.y)) {
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
        return isMouseOver(originX, originY, mouseX, mouseY);
    }

    @Override
    public AdvancementDisplay planeAdvancements$getDisplay() {
        return displayInfo;
    }

    @Override
    public PlacedAdvancement planeAdvancements$getPlaced() {
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
        return displayInfo.getX() == 0;
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
    public void planeAdvancements$renderLines(DrawContext context, int x, int y, boolean border) {
        drawConnectivity(context, x, y, border);
    }
}