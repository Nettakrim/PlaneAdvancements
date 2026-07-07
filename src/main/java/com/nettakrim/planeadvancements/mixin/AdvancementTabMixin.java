package com.nettakrim.planeadvancements.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.nettakrim.planeadvancements.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.client.gui.screens.advancements.AdvancementWidget;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

@Mixin(AdvancementTab.class)
public abstract class AdvancementTabMixin implements AdvancementTabInterface {
    @Shadow @Final @Mutable
    private Map<AdvancementHolder, AdvancementWidgetInterface> widgets;

    @Shadow @Final @Mutable private AdvancementWidget root;
    @Shadow @Final @Mutable private AdvancementNode rootNode;
    @Shadow @Final @Mutable private DisplayInfo display;

    @Shadow private int minX;
    @Shadow private int maxX;
    @Shadow private int minY;
    @Shadow private int maxY;

    @Shadow private double scrollX;
    @Shadow private double scrollY;

    @Shadow private boolean centered;

    @Shadow @Final private AdvancementsScreen screen;
    @Unique private int temperature;

    @Unique private TreeType currentType = TreeType.DEFAULT;
    @Unique private float currentRepulsion = PlaneAdvancementsClient.repulsion;
    @Unique private int currentGridWidth;
    @Unique private boolean treeNeedsUpdate;

    @Unique private AdvancementWidget rootBackup = null;
    @Unique private Map<AdvancementHolder, AdvancementWidgetInterface> widgetsBackup = null;

    @Inject(at = @At("HEAD"), method = "drawContents")
    private void render(GuiGraphics context, int x, int y, CallbackInfo ci) {
        if (!centered) {
            planeAdvancements$heatGraph();
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
            planeAdvancements$updateRange(planeAdvancements$getWidth(), planeAdvancements$getHeight());
            planeAdvancements$centerPan(planeAdvancements$getWidth(), planeAdvancements$getHeight());
            currentGridWidth = PlaneAdvancementsClient.gridWidth;
        }

        if (currentType != PlaneAdvancementsClient.treeType) {
            planeAdvancements$updateRange(planeAdvancements$getWidth(), planeAdvancements$getHeight());
            planeAdvancements$centerPan(planeAdvancements$getWidth(), planeAdvancements$getHeight());
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
        int steps = Mth.ceil(Mth.sqrt(temperature/10f));
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
                planeAdvancements$updateRange(planeAdvancements$getWidth(), planeAdvancements$getHeight());
            } else {
                for (AdvancementWidgetInterface widget : widgets.values()) {
                    widget.planeAdvancements$updatePos();
                }
            }
        }
    }

    @ModifyReturnValue(at = @At("RETURN"), method = "getTitle")
    private Component setTitle(Component original) {
        if (PlaneAdvancementsClient.isMergedAndSpring()) {
            return Component.translatable(PlaneAdvancementsClient.MOD_ID+".merged_tab");
        }
        return original;
    }

    @ModifyReturnValue(at = @At("RETURN"), method = "isMouseOver")
    private boolean hideTab(boolean original) {
        return original && !PlaneAdvancementsClient.isMergedAndSpring();
    }

    @Inject(at = @At("TAIL"), method = "addWidget")
    private void widgetAdded(CallbackInfo callbackInfo) {
        treeNeedsUpdate = true;
        PlaneAdvancementsClient.mergedTreeNeedsUpdate = true;
    }

    @Override
    public void planeAdvancements$heatGraph() {
        temperature = PlaneAdvancementsClient.getTemperature();
    }

    @Override
    public Map<AdvancementHolder, AdvancementWidgetInterface> planeAdvancements$getWidgets() {
        return widgets;
    }

    @Override
    public AdvancementWidgetInterface planeAdvancements$getRoot() {
        return (AdvancementWidgetInterface)root;
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

        // min pan only works as 0, so if it does extend too far, everything needs to be offset to compensate
        int offsetX = Mth.ceil(-minX/16f)*16;
        int offsetY = Mth.ceil(-minY/16f)*16;

        if (offsetX == 0 && offsetY == 0) {
            return;
        }

        minX = 0;
        minY = 0;
        maxX += offsetX;
        maxY += offsetY;
        scrollX -= offsetX;
        scrollY -= offsetY;
        for (AdvancementWidgetInterface widget : widgets.values()) {
            widget.planeAdvancements$getCurrentPos().add(offsetX, offsetY);
            widget.planeAdvancements$updatePos();
        }
    }

    @Override
    public void planeAdvancements$centerPan(int width, int height) {
        if (CompatMode.getCompatMode() == CompatMode.FULLSCREEN) {
            this.scrollX = (width - (maxX + minX)) >> 1;
            this.scrollY = (height - (maxY + minY)) >> 1;
        } else {
            this.scrollX = width - ((this.maxX + this.minX) >> 1);
            this.scrollY = height - ((this.maxY + this.minY) >> 1);
        }
    }

    @Override
    public void planeAdvancements$applyClusters(List<AdvancementCluster> clusters) {
        for (AdvancementCluster cluster : clusters) {
            cluster.applyPosition(28, 27);
        }
    }

    @Override
    public void planeAdvancements$setMerged(Collection<AdvancementTabInterface> tabs) {
        if (widgetsBackup != null) {
            return;
        }

        widgetsBackup = widgets;
        widgets = new HashMap<>(widgetsBackup);
        AdvancementNode placedAdvancement = new AdvancementNode(PlaneAdvancementsClient.mergedEntry, null);
        AdvancementWidget newRoot = new AdvancementWidget((AdvancementTab)(Object)this, Minecraft.getInstance(), placedAdvancement, PlaneAdvancementsClient.mergedDisplay);
        //noinspection DataFlowIssue
        AdvancementWidgetInterface newRootInterface = (AdvancementWidgetInterface)newRoot;

        tabs.forEach(tab -> {
            AdvancementWidgetInterface tabRoot = tab.planeAdvancements$getRoot();
            tabRoot.planeAdvancements$setParent(newRootInterface);
            newRootInterface.planeAdvancements$getChildren().add(tabRoot);
            widgets.putAll(tab.planeAdvancements$getWidgets());
        });
        widgets.put(PlaneAdvancementsClient.mergedEntry, newRootInterface);

        rootBackup = root;
        root = newRoot;
        display = PlaneAdvancementsClient.mergedDisplay;
        rootNode = placedAdvancement;

        planeAdvancements$updateRange(planeAdvancements$getWidth(), planeAdvancements$getHeight());
        planeAdvancements$centerPan(planeAdvancements$getWidth(), planeAdvancements$getHeight());

        planeAdvancements$heatGraph();
    }

    @Override
    public void planeAdvancements$clearMerged(Collection<AdvancementTabInterface> tabs) {
        if (widgetsBackup == null) {
            return;
        }

        widgets = widgetsBackup;
        widgetsBackup = null;

        root = rootBackup;
        display = ((AdvancementWidgetInterface)root).planeAdvancements$getDisplay();
        rootNode = ((AdvancementWidgetInterface)root).planeAdvancements$getPlaced();

        tabs.forEach(tab -> {
            AdvancementWidgetInterface tabRoot = tab.planeAdvancements$getRoot();
            tabRoot.planeAdvancements$setParent(null);
            ((AdvancementTabMixin)tab).currentType = TreeType.SPRING;
        });

        planeAdvancements$updateRange(planeAdvancements$getWidth(), planeAdvancements$getHeight());
        planeAdvancements$centerPan(planeAdvancements$getWidth(), planeAdvancements$getHeight());

        planeAdvancements$heatGraph();
    }

    @Unique
    private int planeAdvancements$getWidth() {
        if (CompatMode.getCompatMode() == CompatMode.FULLSCREEN) {
            FullscreenInterface i = (FullscreenInterface)screen;
            return (i._advancements_fullscreen_getFullscreenWindowWidth() >> 1);
        } else {
            return 117; //((252) >> 1)-9;
        }
    }

    @Unique
    private int planeAdvancements$getHeight() {
        if (CompatMode.getCompatMode() == CompatMode.FULLSCREEN) {
            FullscreenInterface i = (FullscreenInterface)screen;
            return (i._advancements_fullscreen_getFullscreenWindowHeight() >> 1);
        } else {
            return 57; //((140) >> 1)-13; (not sure why this isnt also -18 ???)
        }
    }
}