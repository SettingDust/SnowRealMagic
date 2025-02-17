package snownee.snow.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import snownee.snow.CoreModule;
import snownee.snow.Hooks;
import snownee.snow.SnowCommonConfig;
import snownee.snow.block.SnowVariant;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

	@Inject(at = @At("HEAD"), method = "useOn", cancellable = true)
	private void srm_useOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> ci) {
		if ((Object) this != Items.SNOW) {
			return;
		}
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Player player = context.getPlayer();
		if (SnowCommonConfig.canPlaceSnowInBlock() && level.getFluidState(pos).isEmpty()) {
			BlockState state = level.getBlockState(pos);
			BlockPlaceContext blockContext = new BlockPlaceContext(context);
			if (Hooks.canContainState(state)) {
				if (Hooks.placeLayersOn(level, pos, 1, false, blockContext, true, true) && !level.isClientSide && (player == null || !player.isCreative())) {
					context.getItemInHand().shrink(1);
				}
				ci.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide));
				return;
			}
			if (!state.canBeReplaced(blockContext)) {
				pos = pos.relative(context.getClickedFace());
				state = level.getBlockState(pos);
				blockContext = BlockPlaceContext.at(blockContext, pos, context.getClickedFace());
				boolean canPlace = Hooks.canContainState(state);
				if (state.getBlock() instanceof SnowVariant && blockContext.replacingClickedOnBlock()) {
					canPlace = true;
				}
				if (canPlace) {
					if (Hooks.placeLayersOn(level, pos, 1, false, blockContext, true, true) && !level.isClientSide && (player == null || !player.isCreative())) {
						context.getItemInHand().shrink(1);
					}
					ci.setReturnValue(InteractionResult.sidedSuccess(level.isClientSide));
					return;
				}
			}
		}
	}

	@Inject(
			at = @At(
				"HEAD"
			), method = "updateCustomBlockEntityTag(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/block/state/BlockState;)Z"
	)
	private void srm_updateCustomBlockEntityTag(BlockPos pos, Level worldIn, Player p_40599_, ItemStack stack, BlockState p_40601_, CallbackInfoReturnable<Boolean> ci) {
		if ((Object) this != Items.SNOW) {
			return;
		}
		BlockEntity tile = worldIn.getBlockEntity(pos);
		if (worldIn.isClientSide && tile != null) {
			CompoundTag data = BlockItem.getBlockEntityData(stack);
			if (data != null) {
				data = data.copy();
				tile.load(data);
				tile.setChanged();
			}
		}
	}

	@Inject(at = @At("HEAD"), method = "registerBlocks")
	private void srm_registerBlocks(Map<Block, Item> blockToItemMap, Item itemIn, CallbackInfo ci) {
		if ((Object) this != Items.SNOW) {
			return;
		}
		blockToItemMap.put(CoreModule.TILE_BLOCK.get(), Items.SNOW);
	}

	//	@Override
	//	public void removeFromBlockToItemMap(Map<Block, Item> blockToItemMap, Item itemIn) {
	//		blockToItemMap.remove(CoreModule.TILE_BLOCK);
	//		super.removeFromBlockToItemMap(blockToItemMap, CoreModule.ITEM);
	//	}

	//	@Override
	//	public String getCreatorModId(ItemStack itemStack) {
	//		return SnowRealMagic.MODID;
	//	}

}
