package brightspark.asynclocator.logic;

import brightspark.asynclocator.AsyncLocator;
import brightspark.asynclocator.AsyncLocatorConfig;
import brightspark.asynclocator.AsyncLocatorMod;
import brightspark.asynclocator.mixins.MapItemAccess;
import brightspark.asynclocator.mixins.MerchantOfferAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class MerchantLogic {
	private MerchantLogic() {}

	private static ItemStack createEmptyMap() {
		ItemStack stack = new ItemStack(Items.FILLED_MAP);
		stack.setHoverName(new TranslatableComponent("asynclocator.map.locating"));
		return stack;
	}

	private static void invalidateMap(AbstractVillager merchant, ItemStack mapStack) {
		mapStack.setHoverName(new TranslatableComponent("asynclocator.map.none"));
		merchant.getOffers().stream().filter(offer -> offer.getResult() == mapStack).findFirst().ifPresentOrElse(
			offer -> removeOffer(merchant, offer),
			() -> AsyncLocatorMod.logWarn("Failed to find merchant offer for map")
		);
	}

	private static void removeOffer(AbstractVillager merchant, MerchantOffer offer) {
		if (AsyncLocatorConfig.REMOVE_OFFER.get()) {
			if (merchant.getOffers().remove(offer))
				AsyncLocatorMod.logInfo("Removed merchant map offer");
			else
				AsyncLocatorMod.logWarn("Failed to remove merchant map offer");
		} else {
			((MerchantOfferAccess) offer).setMaxUses(0);
			offer.setToOutOfStock();
			AsyncLocatorMod.logInfo("Marked merchant map offer as out of stock");
		}
	}

	private static void updateMap(ItemStack mapStack, ServerLevel level, BlockPos pos, String displayName, MapDecoration.Type destinationType) {
		MapItemAccess.callCreateAndStoreSavedData(
			mapStack, level, pos.getX(), pos.getZ(), 2, true, true, level.dimension()
		);
		MapItem.renderBiomePreviewMap(level, mapStack);
		MapItemSavedData.addTargetDecoration(mapStack, pos, "+", destinationType);
		mapStack.setHoverName(new TranslatableComponent(displayName));
	}

	private static void handleLocationFound(ServerLevel level, AbstractVillager merchant, ItemStack mapStack, String displayName, MapDecoration.Type destinationType, BlockPos pos) {
		if (pos == null) {
			AsyncLocatorMod.logInfo("No location found - invalidating merchant offer");

			invalidateMap(merchant, mapStack);
		} else {
			AsyncLocatorMod.logInfo("Location found - updating treasure map in merchant offer");

			updateMap(mapStack, level, pos, displayName, destinationType);
		}

		if (merchant.getTradingPlayer() instanceof ServerPlayer tradingPlayer) {
			AsyncLocatorMod.logInfo(
				"Player {} currently trading - updating merchant offers",
				tradingPlayer
			);

			tradingPlayer.sendMerchantOffers(
				tradingPlayer.containerMenu.containerId,
				merchant.getOffers(),
				merchant instanceof Villager villager ? villager.getVillagerData().getLevel() : 1,
				merchant.getVillagerXp(),
				merchant.showProgressBar(),
				merchant.canRestock()
			);
		}
	}

	public static MerchantOffer updateMapAsync(Entity pTrader, int emeraldCost, String displayName, MapDecoration.Type destinationType, int maxUses, int villagerXp, TagKey<ConfiguredStructureFeature<?, ?>> destination) {
		if (pTrader instanceof AbstractVillager merchant) {
			ItemStack mapStack = createEmptyMap();
			ServerLevel level = (ServerLevel) pTrader.level;

			AsyncLocator.locate(level, destination, pTrader.blockPosition(), 100, true)
				.thenOnServerThread(pos ->
					handleLocationFound(level, merchant, mapStack, displayName, destinationType, pos)
				);

			return new MerchantOffer(
				new ItemStack(Items.EMERALD, emeraldCost),
				new ItemStack(Items.COMPASS),
				mapStack,
				maxUses,
				villagerXp,
				0.2F
			);
		} else {
			AsyncLocatorMod.logInfo(
				"Merchant is not of type {} - passing back to normal vanilla behaviour",
				AbstractVillager.class.getSimpleName()
			);
			return null;
		}
	}
}
