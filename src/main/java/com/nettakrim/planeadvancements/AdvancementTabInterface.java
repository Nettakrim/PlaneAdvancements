package com.nettakrim.planeadvancements;

import java.util.*;
import net.minecraft.advancements.AdvancementHolder;

public interface AdvancementTabInterface {
    Map<AdvancementHolder, AdvancementWidgetInterface> planeAdvancements$getWidgets();
    AdvancementWidgetInterface planeAdvancements$getRoot();

    double planeAdvancements$getPanX();
    double planeAdvancements$getPanY();
    void planeAdvancements$centerPan(int width, int height);
    void planeAdvancements$updateRange(int width, int height);

    void planeAdvancements$heatGraph();
    void planeAdvancements$applyClusters(List<AdvancementCluster> clusters);

    void planeAdvancements$setMerged(Collection<AdvancementTabInterface> tabs);
    void planeAdvancements$clearMerged(Collection<AdvancementTabInterface> tabs);
}
