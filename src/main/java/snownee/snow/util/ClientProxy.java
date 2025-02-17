package snownee.snow.util;

import static snownee.snow.CoreModule.FENCE;
import static snownee.snow.CoreModule.FENCE2;
import static snownee.snow.CoreModule.FENCE_GATE;
import static snownee.snow.CoreModule.SLAB;
import static snownee.snow.CoreModule.STAIRS;
import static snownee.snow.CoreModule.TILE_BLOCK;
import static snownee.snow.CoreModule.WALL;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.Variant;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import snownee.kiwi.KiwiGO;
import snownee.kiwi.loader.Platform;
import snownee.snow.CoreModule;
import snownee.snow.client.FallingSnowRenderer;
import snownee.snow.client.SnowClient;
import snownee.snow.client.SnowVariantMetadataSectionSerializer;
import snownee.snow.client.model.ModelDefinition;
import snownee.snow.client.model.SnowVariantModel;
import snownee.snow.client.model.SnowCoveredModel;
import snownee.snow.client.model.WrapperUnbakedModel;

public class ClientProxy implements ClientModInitializer {

	public static BakedModel getBlockModel(BlockState state) {
		return Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
	}

	public static BakedModel getBlockModel(ResourceLocation location) {
		return Minecraft.getInstance().getModelManager().getModel(location);
	}

	public static void onPlayerJoin() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && Platform.isModLoaded("sodium") && !Platform.isModLoaded("indium")) {
			player.sendSystemMessage(Component.literal("Please install §lIndium§r mod to make Snow! Real Magic! work with Sodium."));
		}
	}

	public static void renderFallingBlock(Entity entity, BlockState state, BlockPos pos, PoseStack poseStack, MultiBufferSource bufferSource) {
		BlockPos blockpos = BlockPos.containing(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
		poseStack.translate(-0.5D, 0.0D, -0.5D);
		BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
		RenderType type = ItemBlockRenderTypes.getMovingBlockRenderType(state);
		dispatcher.getModelRenderer().tesselateBlock(entity.level(), getBlockModel(state), state, blockpos, poseStack, bufferSource.getBuffer(type), false, RandomSource.create(42), state.getSeed(pos), OverlayTexture.NO_OVERLAY);
	}

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(CoreModule.ENTITY.getOrCreate(), FallingSnowRenderer::new);

		ModelLoadingPlugin.register(ctx -> {
			List<ResourceLocation> extraModels = Lists.newArrayList(SnowClient.OVERLAY_MODEL);

			SnowClient.snowVariantMapping.clear();
			ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
			ModelBakery.MODEL_LISTER.listMatchingResources(resourceManager).forEach((key, resource) -> {
				ModelDefinition def;
				try {
					def = resource.metadata().getSection(SnowVariantMetadataSectionSerializer.SERIALIZER).orElse(null);
				} catch (IOException e) {
					return;
				}
				if (def == null || def.model == null) {
					return;
				}
				SnowClient.snowVariantMapping.put(ModelBakery.MODEL_LISTER.fileToId(key), def);
				extraModels.add(def.model);
				if (def.overrideBlocks != null) {
					for (ResourceLocation id : def.overrideBlocks) {
						Block block = BuiltInRegistries.BLOCK.get(id);
						if (block != Blocks.AIR) {
							SnowClient.overrideBlocks.add(block);
						}
					}
				}
			});
			ctx.addModels(extraModels);

			List<KiwiGO<? extends Block>> allBlocks = List.of(TILE_BLOCK, FENCE, FENCE2, STAIRS, SLAB, FENCE_GATE, WALL);
			Set<ResourceLocation> snowCoveredModelIds = Sets.newHashSet();
			Map<UnbakedModel, UnbakedModel> transform = Maps.newHashMap();
			for (KiwiGO<? extends Block> block : allBlocks) {
				for (BlockState state : block.get().getStateDefinition().getPossibleStates()) {
					ResourceLocation modelId = BlockModelShaper.stateToModelLocation(BuiltInRegistries.BLOCK.getKey(block.get()), state);
					snowCoveredModelIds.add(modelId);
				}
			}

			ctx.modifyModelOnLoad().register(ModelModifier.WRAP_LAST_PHASE, (model, context) -> {
				if (snowCoveredModelIds.contains(context.id())) {
					return transform.computeIfAbsent(model, $ -> new WrapperUnbakedModel($, SnowCoveredModel::new));
				}
				return model;
			});

			ctx.modifyModelAfterBake().register(ModelModifier.WRAP_LAST_PHASE, (model, context) -> {
				ModelState modelState = context.settings();
				if (model == null || modelState.getClass() != Variant.class) {
					return model;
				}
				ModelDefinition def = SnowClient.snowVariantMapping.get(context.id());
				if (def == null) {
					return model;
				}
				Variant variantState = (Variant) modelState;
				variantState = new Variant(def.model, variantState.getRotation(), variantState.isUvLocked(), variantState.getWeight());
				BakedModel variantModel = context.baker().bake(def.model, variantState);
				if (variantModel == null) {
					return model;
				}
				return new SnowVariantModel(model, variantModel);
			});

			SnowClient.cachedOverlayModel = null;
			SnowClient.cachedSnowModel = null;
		});
	}
}
