package com.nettakrim.planeadvancements.mixin;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.nettakrim.planeadvancements.*;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.PlacedAdvancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("UnresolvedMixinReference")
@Pseudo
@Mixin(targets = "betteradvancements.common.gui.BetterAdvancementTab", remap = false)
public abstract class BetterAdvancementTabMixin implements AdvancementTabInterface {
    @Shadow @Final @Mutable private Map<AdvancementEntry, AdvancementWidgetInterface> widgets;

    @Shadow private int minX;
    @Shadow private int maxX;
    @Shadow private int minY;
    @Shadow private int maxY;

    @Shadow private int scrollX;
    @Shadow private int scrollY;
    
    @Unique private AdvancementWidgetInterface root;
    @Shadow @Final @Mutable private PlacedAdvancement rootNode;
    @Shadow @Final @Mutable private AdvancementDisplay display;

    @Unique
    private int temperature = -1;

    @Unique private TreeType currentType = TreeType.DEFAULT;
    @Unique private float currentRepulsion = PlaneAdvancementsClient.repulsion;
    @Unique private int currentGridWidth;
    @Unique private boolean treeNeedsUpdate;

    @Unique private AdvancementWidgetInterface rootBackup = null;
    @Unique private Map<AdvancementEntry, AdvancementWidgetInterface> widgetsBackup = null;

    @Inject(at = @At("TAIL"), method = "<init>", remap = true)
    private void init(MinecraftClient client, @Coerce Object screen, @Coerce Object type, int index, PlacedAdvancement root, AdvancementDisplay display, CallbackInfo ci) {
        try {
            this.root = (AdvancementWidgetInterface)this.getClass().getDeclaredField("root").get(this);
        } catch (Exception ignored) {}
    }

    @Inject(at = @At("HEAD"), method = "drawContents", remap = true)
    private void render(DrawContext context, int left, int top, int width, int height, float zoom, CallbackInfo ci) {
        // shadowing centered is inconsistent, for some reason
        if (temperature == -1) {
            planeAdvancements$heatGraph();
            planeAdvancements$centerPan(width, height);
        }

        if (PlaneAdvancementsClient.isMergedAndSpring()) {
            if (PlaneAdvancementsClient.mergedTreeNeedsUpdate) {
                AdvancementCluster.initialiseTree(planeAdvancements$getRoot());
                PlaneAdvancementsClient.mergedTreeNeedsUpdate = false;
            }
        } else if (treeNeedsUpdate) {
            AdvancementCluster.initialiseTree(planeAdvancements$getRoot());
            treeNeedsUpdate = false;
        }

        if (currentGridWidth != PlaneAdvancementsClient.gridWidth && PlaneAdvancementsClient.treeType == TreeType.GRID) {
            planeAdvancements$applyClusters(AdvancementCluster.getGridClusters(planeAdvancements$getRoot()));
            planeAdvancements$updateRange(width, height);
            planeAdvancements$centerPan(width, height);
            currentGridWidth = PlaneAdvancementsClient.gridWidth;
        }

        if (currentType != PlaneAdvancementsClient.treeType) {
            planeAdvancements$updateRange(width, height);
            planeAdvancements$centerPan(width, height);
        }

        if (currentRepulsion != PlaneAdvancementsClient.repulsion) {
            currentRepulsion = PlaneAdvancementsClient.repulsion;
            planeAdvancements$heatGraph();
        }

        if (temperature <= 0) {
            return;
        }
        temperature--;

        // always update spring graph forces, so that it can settle while not visible
        int steps = MathHelper.ceil(MathHelper.sqrt(temperature/10f));
        for (int i = 0; i < steps; i++) {
            for (AdvancementWidgetInterface widgetA : widgets.values()) {
                for (AdvancementWidgetInterface widgetB : widgets.values()) {
                    widgetA.planeAdvancements$applySpringForce(widgetB, 0.1f, PlaneAdvancementsClient.repulsion);
                }
            }
        }

        if (PlaneAdvancementsClient.treeType == TreeType.SPRING) {
            // == 1 so it updates on the last update tick
            if (temperature%60 == 1) {
                planeAdvancements$updateRange(width, height);
            } else {
                for (AdvancementWidgetInterface widget : widgets.values()) {
                    widget.planeAdvancements$updatePos();
                }
            }
        }
    }

    // root cannot be shadowed, so cannot be @Mutable, however its only used here, so we can swap the object thats being used in the functions
    @ModifyReceiver(at = @At(value = "INVOKE", target = "Lbetteradvancements/common/gui/BetterAdvancementWidget;drawConnectivity(Lnet/minecraft/client/gui/DrawContext;IIZ)V"), method = "drawContents", remap = true)
    private @Coerce AdvancementWidgetInterface replaceLineDrawer(@Coerce AdvancementWidgetInterface receiver, DrawContext context, int x, int y, boolean border) {
        return root;
    }

    @ModifyReceiver(at = @At(value = "INVOKE", target = "Lbetteradvancements/common/gui/BetterAdvancementWidget;draw(Lnet/minecraft/client/gui/DrawContext;II)V"), method = "drawContents", remap = true)
    private @Coerce AdvancementWidgetInterface replaceWidgetDrawer(@Coerce AdvancementWidgetInterface receiver, DrawContext context, int x, int y) {
        return root;
    }

    @Inject(at = @At("TAIL"), method = "scroll", remap = true)
    private void fixPan(double x, double y, int width, int height, CallbackInfo ci) {
        if (PlaneAdvancementsClient.treeType != TreeType.SPRING) {
            return;
        }

        if (maxX - minX <= width) {
            scrollX = (width - (maxX + minX))/2;
        }
        if (maxY - minY <= height) {
            scrollY = (height - (maxY + minY))/2;
        }
    }

    @ModifyReturnValue(at = @At("RETURN"), method = "getTitle", remap = true)
    private Text setTitle(Text original) {
        if (PlaneAdvancementsClient.isMergedAndSpring()) {
            return Text.translatable(PlaneAdvancementsClient.MOD_ID+".merged_tab_better");
        }
        return original;
    }

    @ModifyReturnValue(at = @At("RETURN"), method = "isMouseOver", remap = true)
    private boolean hideTab(boolean original) {
        return original && !PlaneAdvancementsClient.isMergedAndSpring();
    }

    @Inject(at = @At("TAIL"), method = "addWidget", remap = true)
    private void widgetAdded(CallbackInfo callbackInfo) {
        treeNeedsUpdate = true;
        PlaneAdvancementsClient.mergedTreeNeedsUpdate = true;
    }

    @Override
    public void planeAdvancements$heatGraph() {
        temperature = PlaneAdvancementsClient.getTemperature();
    }

    @Override
    public Map<AdvancementEntry, AdvancementWidgetInterface> planeAdvancements$getWidgets() {
        return widgets;
    }

    @Override
    public AdvancementWidgetInterface planeAdvancements$getRoot() {
        return root;
    }

    @Override
    public double planeAdvancements$getPanX() {
        return scrollX;
    }

    @Override
    public double planeAdvancements$getPanY() {
        return scrollY;
    }

    @Override
    public void planeAdvancements$updateRange(int width, int height) {
        currentType = PlaneAdvancementsClient.treeType;

        minX = Integer.MAX_VALUE;
        maxX = Integer.MIN_VALUE;
        minY = Integer.MAX_VALUE;
        maxY = Integer.MIN_VALUE;

        for (AdvancementWidgetInterface widget : widgets.values()) {
            widget.planeAdvancements$updatePos();

            int i = widget.planeAdvancements$getX();
            int j = i + 28;
            int k = widget.planeAdvancements$getY();
            int l = k + 27;
            minX = Math.min(minX, i);
            maxX = Math.max(maxX, j);
            minY = Math.min(minY, k);
            maxY = Math.max(maxY, l);
        }

        if (PlaneAdvancementsClient.treeType == TreeType.SPRING) {
            minX -= width/2;
            maxX += width/2;
            minY -= height/2;
            maxY += height/2;
        } else if (maxX - minX > width) {
            maxX += 4;
        }
    }

    @Override
    public void planeAdvancements$centerPan(int width, int height) {
        scrollX = (width - (maxX + minX))/2;
        scrollY = (height - (maxY + minY))/2;
    }

    @Override
    public void planeAdvancements$applyClusters(List<AdvancementCluster> clusters) {
        for (AdvancementCluster cluster : clusters) {
            cluster.applyPosition(32, 27);
        }
    }

    @Override
    public void planeAdvancements$setMerged(Collection<AdvancementTabInterface> tabs) {
        if (widgetsBackup != null) {
            return;
        }

        widgetsBackup = widgets;
        widgets = new HashMap<>(widgetsBackup);
        PlacedAdvancement placedAdvancement = new PlacedAdvancement(PlaneAdvancementsClient.mergedEntry, null);

        AdvancementWidgetInterface newRootInterface;
        try {
            newRootInterface = (AdvancementWidgetInterface)root.getClass().getConstructors()[0].newInstance(this, MinecraftClient.getInstance(), placedAdvancement, PlaneAdvancementsClient.mergedDisplay);
        } catch (Exception ignored) {
            return;
        }

        tabs.forEach(tab -> {
            AdvancementWidgetInterface tabRoot = tab.planeAdvancements$getRoot();
            tabRoot.planeAdvancements$setParent(newRootInterface);
            newRootInterface.planeAdvancements$getChildren().add(tabRoot);
            widgets.putAll(tab.planeAdvancements$getWidgets());
        });
        widgets.put(PlaneAdvancementsClient.mergedEntry, newRootInterface);

        rootBackup = root;
        root = newRootInterface;
        display = PlaneAdvancementsClient.mergedDisplay;
        rootNode = placedAdvancement;

        planeAdvancements$heatGraph();

        // no width/height in this context, setting currentType will later cause pan centering
        currentType = TreeType.DEFAULT;
    }

    @Override
    public void planeAdvancements$clearMerged(Collection<AdvancementTabInterface> tabs) {
        if (widgetsBackup == null) {
            return;
        }

        widgets = widgetsBackup;
        widgetsBackup = null;

        root = rootBackup;
        display = root.planeAdvancements$getDisplay();
        rootNode = root.planeAdvancements$getPlaced();

        tabs.forEach(tab -> {
            AdvancementWidgetInterface tabRoot = tab.planeAdvancements$getRoot();
            tabRoot.planeAdvancements$setParent(null);
            ((BetterAdvancementTabMixin)tab).currentType = TreeType.SPRING;
        });

        planeAdvancements$heatGraph();

        // no width/height in this context, setting currentType will later cause pan centering
        currentType = TreeType.DEFAULT;
    }
}