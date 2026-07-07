package com.nettakrim.planeadvancements;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3f;

import java.util.*;
import net.minecraft.util.Mth;

public class AdvancementCluster {
    public final Vector2f pos;
    public final Vector2i size;
    protected final float offsetY;
    protected final AdvancementWidgetInterface root;

    public AdvancementCluster(AdvancementWidgetInterface root) {
        this(root, root.planeAdvancements$isRoot() ? root : null);
    }

    public AdvancementCluster(AdvancementWidgetInterface root, AdvancementWidgetInterface ignoreChildrenFrom) {
        Vector3f size = getClusterSize(root, ignoreChildrenFrom);

        this.pos = new Vector2f(0,0);
        this.size = new Vector2i(Mth.ceil(size.x), Mth.ceil((size.z-size.y)+1));
        this.offsetY = -size.y;
        this.root = root;

        root.planeAdvancements$setClusterRoot(true);
    }

    public static Vector3f getClusterSize(AdvancementWidgetInterface root, AdvancementWidgetInterface ignoreChildrenFrom) {
        if (root == ignoreChildrenFrom) {
            return new Vector3f(1,0,0);
        }

        float widthMax = 1;
        float heightMax = 0;
        float heightMin = 0;

        float rootX = root.planeAdvancements$getDisplay().getX()-1;
        float rootY = root.planeAdvancements$getDisplay().getY();

        Stack<AdvancementWidgetInterface> stack = new Stack<>();
        stack.addAll(root.planeAdvancements$getChildren());
        while (!stack.isEmpty()) {
            AdvancementWidgetInterface advancement = stack.pop();
            if (advancement != ignoreChildrenFrom) {
                stack.addAll(advancement.planeAdvancements$getChildren());
            }

            float width = advancement.planeAdvancements$getDisplay().getX()-rootX;
            if (width > widthMax) {
                widthMax = width;
            }

            float height = advancement.planeAdvancements$getDisplay().getY()-rootY;
            if (height > heightMax) {
                heightMax = height;
            }
            if (height < heightMin) {
                heightMin = height;
            }
        }

        return new Vector3f(widthMax, heightMin, heightMax);
    }

    public void applyPosition(int xScale, int yScale) {
        pos.y += offsetY;

        pos.mul(xScale, yScale);
        pos.sub(root.planeAdvancements$getDefaultPos());

        root.planeAdvancements$setGridPos(pos);
    }

    public static List<AdvancementCluster> getGridClusters(AdvancementWidgetInterface root) {
        List<AdvancementCluster> clusters = root.planeAdvancements$getChildren().size() > 1 ? getChildClusters(root) : getSplitClusters(root);
        calculateGrid(clusters);
        return clusters;
    }

    private static List<AdvancementCluster> getSplitClusters(AdvancementWidgetInterface root) {
        // split on node with most children 3 or more, breadth first
        Queue<AdvancementWidgetInterface> queue = new ArrayDeque<>(root.planeAdvancements$getChildren());

        AdvancementWidgetInterface mostChildrenWidget = null;
        int mostChildrenCount = 2;

        while (!queue.isEmpty()) {
            AdvancementWidgetInterface widget = queue.remove();

            List<AdvancementWidgetInterface> children = widget.planeAdvancements$getChildren();
            queue.addAll(children);

            if (children.size() > mostChildrenCount) {
                mostChildrenWidget = widget;
                mostChildrenCount = children.size();
            }
        }

        // if nothing is found, just split as normal
        if (mostChildrenWidget == null) {
            return getChildClusters(root);
        }

        List<AdvancementCluster> clusters = new ArrayList<>();
        for (AdvancementWidgetInterface clusterRoot : mostChildrenWidget.planeAdvancements$getChildren()) {
            clusters.add(new AdvancementCluster(clusterRoot));
        }
        clusters.add(new AdvancementCluster(root.planeAdvancements$getChildren().getFirst(), mostChildrenWidget));
        clusters.add(new AdvancementCluster(root));

        return clusters;
    }

    private static List<AdvancementCluster> getChildClusters(AdvancementWidgetInterface root) {
        List<AdvancementWidgetInterface> clusterRoots = root.planeAdvancements$getChildren();

        List<AdvancementCluster> clusters = new ArrayList<>(clusterRoots.size());
        for (AdvancementWidgetInterface clusterRoot : clusterRoots) {
            clusters.add(new AdvancementCluster(clusterRoot));
        }
        clusters.add(new AdvancementCluster(root));
        return clusters;
    }

    private static void calculateGrid(List<AdvancementCluster> clusters) {
        AdvancementCluster root = clusters.removeLast();

        //sort bigger clusters to start of the list
        clusters.sort((a, b) -> {
            int i = Float.compare(b.size.y, a.size.y);
            return i == 0 ? Float.compare(b.size.x, a.size.x) : i;
        });

        int maxWidth = PlaneAdvancementsClient.gridWidth-1;
        int usedWidth = 0;

        IntArrayList mask = new IntArrayList();

        // place clusters in first available spot, stepping left, then up when max width is reached
        for (AdvancementCluster cluster : clusters) {
            int y = 0;
            int search = (maxWidth - cluster.size.x) + 1;

            // if a cluster is too big, just let it overflow
            if (search <= 1) {
                cluster.pos.x = 1;
                cluster.pos.y = mask.size();
                for (int i = 0; i < cluster.size.y; i++) {
                    mask.add(-1);
                }
                usedWidth = maxWidth;
                continue;
            }

            // otherwise step through positions
            while (true) {
                int foundPosition = getFreePos(mask, cluster, search, y);

                if (foundPosition >= 0) {
                    int foundMask = getMask(cluster.size.x, foundPosition);
                    for (int u = 0; u < cluster.size.y; u++) {
                        int index = y+u;
                        if (index >= mask.size()) {
                            mask.add(foundMask);
                        } else {
                            mask.set(index, mask.getInt(index) | foundMask);
                        }
                    }

                    cluster.pos.x = foundPosition;
                    cluster.pos.y = y;
                    usedWidth = Math.max(usedWidth, foundPosition + cluster.size.x);
                    break;
                }

                y++;
            }
        }


        Vector2f mirror = new Vector2f(usedWidth+1, mask.size());
        Queue<AdvancementCluster> toSettle = new ArrayDeque<>(clusters.reversed());
        // settle clusters to the left, this is better than just putting them there to begin with as it means bigger clusters will be further to the right
        while (!toSettle.isEmpty()) {
            AdvancementCluster cluster = toSettle.remove();

            //ignore overflowed clusters
            if (cluster.size.x >= maxWidth) {
                cluster.pos.y = mirror.y - cluster.pos.y - cluster.size.y;
                continue;
            }

            int position = (int)cluster.pos.x;
            int y = (int)cluster.pos.y;

            int startingFilter = ~getMask(cluster.size.x, position);

            while (true) {
                // step left for a position that the cluster fits in
                position++;

                int sizeMask = getMask(cluster.size.x, position);

                boolean hit;
                if (cluster.size.x + position > usedWidth) {
                    hit = true;
                } else {
                    hit = false;
                    for (int u = 0; u < cluster.size.y; u++) {
                        int index = y + u;
                        if ((mask.getInt(index) & startingFilter & sizeMask) != 0) {
                            hit = true;
                            break;
                        }
                    }
                }

                // once collided, move to last free position
                if (hit) {
                    position--;
                    // if it didn't move, xy mirror cluster to get it into its final position
                    if (position == cluster.pos.x) {
                        mirror.sub(cluster.pos, cluster.pos).sub(cluster.size.x, cluster.size.y);
                        break;
                    }

                    // otherwise, update masks, then queue it to settle again, to resolve any complicated chains (this is rare to actually matter)
                    sizeMask = getMask(cluster.size.x, position);
                    for (int u = 0; u < cluster.size.y; u++) {
                        int index = y+u;
                        mask.set(index, (mask.getInt(index) & startingFilter) | sizeMask);
                    }
                    cluster.pos.x = position;
                    toSettle.add(cluster);
                    break;
                }
            }
        }

        // set root position, then add it back to list, so it's position gets applied later
        root.pos.x = 0;
        root.pos.y = (mask.size()-1)/2f;
        clusters.add(root);
    }

    private static int getFreePos(IntArrayList mask, AdvancementCluster cluster, int search, int y) {
        if (y >= mask.size()) {
            return 0;
        }
        int currentMask = mask.getInt(y);

        for (int i = 0; i < search; i++) {
            int sizeMask = getMask(cluster.size.x, i);
            if ((currentMask & sizeMask) == 0) {
                boolean found = true;

                for (int u = 0; u < cluster.size.y; u++) {
                    int index = y+u;
                    if (index >= mask.size()) {
                        return i;
                    }
                    if ((mask.getInt(index) & sizeMask) != 0) {
                        found = false;
                        break;
                    }
                }

                if (found) {
                    return i;
                }
            }
        }

        return -1;
    }

    private static int getMask(int size, int position) {
        return ((1 << size)-1) * (1 << position);
    }

    public static void initialiseTree(AdvancementWidgetInterface root) {
        Vector2f rootPos = root.planeAdvancements$getTreePos();
        if (!Float.isNaN(rootPos.x) && !Float.isNaN(rootPos.y)) {
            // recursively search for uninitialised children
            for (AdvancementWidgetInterface child : root.planeAdvancements$getChildren()) {
                initialiseTree(child);
            }
            return;
        }

        Stack<TreeNode> solve = new Stack<>();
        float rootAngle = 0;
        AdvancementWidgetInterface rootParent = root.planeAdvancements$getParent();
        if (rootParent != null) {
            AdvancementWidgetInterface validSibling = null;
            int validSiblings = 0;
            for (AdvancementWidgetInterface sibling : rootParent.planeAdvancements$getChildren()) {
                Vector2f siblingPos = sibling.planeAdvancements$getTreePos();
                if (!Float.isNaN(siblingPos.x) && !Float.isNaN(siblingPos.y)) {
                    if (validSiblings == 0) {
                        validSibling = sibling;
                    }
                    validSiblings++;
                }
            }

            if (validSiblings == 0) {
                // if it's the only child, try place it in line
                AdvancementWidgetInterface parentParent = rootParent.planeAdvancements$getParent();
                if (parentParent != null) {
                    Vector2f v = rootParent.planeAdvancements$getTreePos().sub(parentParent.planeAdvancements$getTreePos(), new Vector2f());
                    rootAngle = (float) Mth.atan2(v.y, v.x);
                } else {
                    rootAngle = 0;
                }
            } else {
                // otherwise, place it in what is probably a gap in its siblings
                validSiblings++;
                if (validSiblings%2 == 0) {
                    validSiblings = -validSiblings;
                }
                Vector2f v = rootParent.planeAdvancements$getTreePos().sub(validSibling.planeAdvancements$getTreePos(), new Vector2f());
                PlaneAdvancementsClient.LOGGER.info(validSiblings+" "+v);
                rootAngle = (float) Mth.atan2(v.y, v.x) + Mth.PI/validSiblings;
            }

            root.planeAdvancements$getTreePos().set(Mth.cos(rootAngle)*64, Mth.sin(rootAngle)*64).add(rootParent.planeAdvancements$getTreePos());
        } else {
            root.planeAdvancements$getTreePos().set(0);
        }
        solve.push(new TreeNode(root, rootAngle));

        while (!solve.isEmpty()) {
            solve.pop().arcChildren(solve);
        }
    }

    private record TreeNode(AdvancementWidgetInterface widget, float angle) {
        public void arcChildren(Stack<TreeNode> solve) {
            List<AdvancementWidgetInterface> children = widget.planeAdvancements$getChildren();
            for (int i = 0; i < children.size(); i++) {
                AdvancementWidgetInterface child = children.get(i);
                float t;
                if (widget.planeAdvancements$getParent() == null) {
                    t = (Mth.TWO_PI * i) / children.size();
                } else if (children.size() > 1) {
                    t = angle + Mth.HALF_PI*(i/(children.size()-1f) - 0.5f);
                } else {
                    t = angle;
                }
                child.planeAdvancements$getTreePos().set(Mth.cos(t)*64, Mth.sin(t)*64).add(widget.planeAdvancements$getTreePos());
                solve.push(new TreeNode(child, t));
            }
        }
    }
}
