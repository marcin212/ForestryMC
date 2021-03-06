package forestry.core.items;

import forestry.core.IItemTyped;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

/**
 * For blocks whose type depends on metadata.
 * This allows control over which localized name maps to which meta value.
 */
public class ItemTypedBlock extends ItemForestryBlock {

	public ItemTypedBlock(Block block) {
		super(block);
	}

	@Override
	public String getUnlocalizedName(ItemStack itemstack) {
		Block block = getBlock();
		if (block instanceof IItemTyped) {
			IItemTyped blockTyped = (IItemTyped)block;
			Enum type = blockTyped.getTypeFromMeta(itemstack.getItemDamage());
			if (type != null)
				return getBlock().getUnlocalizedName() + "." + type.ordinal();
			else
				return null;
		}
		return super.getUnlocalizedName(itemstack);
	}
}
