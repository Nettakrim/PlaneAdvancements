package com.nettakrim.planeadvancements;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.ClientModInitializer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.advancements.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PlaneAdvancementsClient implements ClientModInitializer {
	public static final String MOD_ID = "planeadvancements";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static Path configDir;

	public static TreeType treeType = TreeType.SPRING;
	public static float repulsion = 0.1f;
    public static boolean angledLines = true;
	public static int gridWidth = 10;
	public static boolean merged = false;

	public static AdvancementWidgetInterface draggedWidget;

	public static Button treeButton;
	public static Button lineButton;
	public static AbstractSliderButton repulsionSlider;
	public static AbstractSliderButton gridWidthSlider;
	public static Button mergedButton;

	public static final Map<Advancement, TreePosition> positions = new HashMap<>();

	public static final DisplayInfo mergedDisplay = new DisplayInfo(
			// item cant be air, but it can be stone with the model of air
			new ItemStackTemplate(Items.STONE, DataComponentPatch.builder().set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace("air")).build()),
			Component.translatable(PlaneAdvancementsClient.MOD_ID+".merged_title"),
			Component.translatable(PlaneAdvancementsClient.MOD_ID+".merged_description"),
			Optional.of(new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath(MOD_ID,"merged_background"))),
			AdvancementType.CHALLENGE, false, false, false
	);
	public static final Advancement mergedAdvancement = new Advancement(Optional.empty(), Optional.of(mergedDisplay), AdvancementRewards.EMPTY, Map.of(), AdvancementRequirements.EMPTY, false);
	public static final AdvancementHolder mergedEntry = new AdvancementHolder(Identifier.fromNamespaceAndPath(PlaneAdvancementsClient.MOD_ID, "merged"), mergedAdvancement);

	public static boolean mergedTreeNeedsUpdate;

	@Override
	public void onInitializeClient() {
		loadConfig();
		ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> saveConfig());

		treeButton = Button.builder(getTreeText(), _ -> cycleTreeType()).bounds(0,0,16,16).build();
		lineButton = Button.builder(getLineText(), _ -> cycleLineType()).bounds(80,0,16,16).build();
		repulsionSlider = new CallableSlider(16, 0, 64, 16, PlaneAdvancementsClient::getRepulsionText, Mth.sqrt(repulsion), (v) -> repulsion = Math.max((float)(v * v), 0.01f));
		gridWidthSlider = new CallableSlider(16, 0, 64, 16, PlaneAdvancementsClient::getGridWidthText, (gridWidth - 2) / 14d, (v) -> gridWidth = (int)Math.round(v*14 + 2));
		mergedButton = Button.builder(getMergedText(), _ -> cycleMerged()).bounds(96,0,16,16).build();

		setUIActive();
	}

	private static Component getTreeText() {
		return Component.translatable(MOD_ID+".tree."+treeType.name().toLowerCase(Locale.ROOT));
	}

	private static Component getLineText() {
		return Component.translatable(MOD_ID+".line."+(angledLines ? "rotated" : "smart"));
	}

	private static Component getRepulsionText() {
		return Component.translatable(MOD_ID+".repulsion", repulsion <= 0.01f ? "0.0" : String.valueOf(Mth.sqrt(repulsion)+0.01f).substring(0,3));
	}

	private static Component getGridWidthText() {
		return Component.translatable(MOD_ID+".grid_width", gridWidth);
	}

	private static Component getMergedText() {
		return Component.translatable(MOD_ID+(merged ? ".merged" : ".unmerged"));
	}

	public static LineType getCurrentLineType() {
		return treeType == TreeType.SPRING ? (angledLines ? LineType.ROTATED : LineType.SMART) : LineType.DEFAULT;
	}

	public static void cycleTreeType() {
		treeType = TreeType.values()[(treeType.ordinal() + 1) % TreeType.values().length];
		treeButton.setMessage(getTreeText());
		setUIActive();
	}

	public static void cycleLineType() {
		angledLines = !angledLines;
		lineButton.setMessage(getLineText());
	}

	public static void cycleMerged() {
		merged = !merged;
		mergedButton.setMessage(getMergedText());
	}

	public static boolean hoveredUI() {
		return treeButton.isHovered() || lineButton.isHovered() || repulsionSlider.isHovered() || gridWidthSlider.isHovered() || mergedButton.isHovered();
	}

	public static void clearUIHover() {
		treeButton.setFocused(false);
		lineButton.setFocused(false);
		repulsionSlider.setFocused(false);
		gridWidthSlider.setFocused(false);
		mergedButton.setFocused(false);
	}

	public static void renderUI(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
		treeButton.extractRenderState(graphics, mouseX, mouseY, tickDelta);
		if (treeType == TreeType.SPRING) {
			lineButton.extractRenderState(graphics, mouseX, mouseY, tickDelta);
			repulsionSlider.extractRenderState(graphics, mouseX, mouseY, tickDelta);
			mergedButton.extractRenderState(graphics, mouseX, mouseY, tickDelta);
		} if (treeType == TreeType.GRID) {
			gridWidthSlider.extractRenderState(graphics, mouseX, mouseY, tickDelta);
		}
	}

	private static void setUIActive() {
		boolean isTree = treeType == TreeType.SPRING;
		lineButton.active = isTree;
		repulsionSlider.active = isTree;
		mergedButton.active = isTree;
		gridWidthSlider.active = treeType == TreeType.GRID;
	}

	public static boolean isMergedAndSpring() {
		return merged && treeType == TreeType.SPRING;
	}

	public static int getTemperature() {
		return merged && treeType == TreeType.SPRING ? 1500 : 1000;
	}

	private static final Codec<Data> dataCodec = RecordCodecBuilder.create((instance) -> instance.group(
			Codec.INT.optionalFieldOf("treeType", 1).forGetter(Data::treeType),
			Codec.FLOAT.optionalFieldOf("repulsion", 0.1f).forGetter(Data::repulsion),
			Codec.BOOL.optionalFieldOf("angledLines", true).forGetter(Data::angledLines),
			Codec.INT.optionalFieldOf("gridWidth", 10).forGetter(Data::gridWidth),
			Codec.BOOL.optionalFieldOf("merged", false).forGetter(Data::merged)
	).apply(instance, Data::new));

	private record Data(int treeType, float repulsion, boolean angledLines, int gridWidth, boolean merged) {}

	private static void loadConfig() {
		configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID+".json");
		if (!configDir.toFile().exists()) {
			saveConfig();
			return;
		}

		try {
			Data data = dataCodec.parse(JsonOps.INSTANCE, new GsonBuilder().create().fromJson(Files.newBufferedReader(configDir), JsonElement.class)).result().orElse(new Data(treeType.ordinal(), 0.1f, true, 10, false));
			treeType = TreeType.values()[data.treeType];
			repulsion = data.repulsion;
			angledLines = data.angledLines;
			gridWidth = data.gridWidth;
			merged = data.merged;
		} catch (IOException e) {
			LOGGER.info("Failed to load file from {} {}", configDir, e);
		}
	}

	//see DataProvider.writeCodecToPath - it uses various @Beta and @Deprecated methods/classes
	@SuppressWarnings({"UnstableApiUsage", "deprecation"})
	private static void saveConfig() {
		CompletableFuture.runAsync(() -> {
			try {
				ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
				HashingOutputStream hashingOutputStream = new HashingOutputStream(Hashing.sha1(), byteArrayOutputStream);
				JsonWriter jsonWriter = new JsonWriter(new OutputStreamWriter(hashingOutputStream, StandardCharsets.UTF_8));

				try {
					jsonWriter.setSerializeNulls(false);
					jsonWriter.setIndent("");
					GsonHelper.writeValue(jsonWriter, dataCodec.encodeStart(JsonOps.INSTANCE, new Data(treeType.ordinal(), repulsion, angledLines, gridWidth, merged)).getOrThrow(), DataProvider.KEY_COMPARATOR);
				} catch (Throwable var9) {
					try {
						jsonWriter.close();
					} catch (Throwable var8) {
						var9.addSuppressed(var8);
					}

					throw var9;
				}

				jsonWriter.close();
				CachedOutput.NO_CACHE.writeIfNeeded(configDir, byteArrayOutputStream.toByteArray(), hashingOutputStream.hash());
			} catch (IOException e) {
				LOGGER.info("Failed to save file to {} {}", configDir, e);
			}
		}, Util.backgroundExecutor().forName("saveStable"));
	}
}