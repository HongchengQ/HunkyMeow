package emu.grasscutter.game.gacha;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.gacha.GachaBanner.BannerType;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.net.proto.GachaItemOuterClass.GachaItem;
import emu.grasscutter.net.proto.GachaTransferItemOuterClass.GachaTransferItem;
import emu.grasscutter.net.proto.GetGachaInfoRspOuterClass.GetGachaInfoRsp;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.event.player.PlayerWishEvent;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.send.PacketDoGachaRsp;
import emu.grasscutter.utils.*;
import it.unimi.dsi.fastutil.ints.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;
import org.greenrobot.eventbus.Subscribe;

public class GachaSystem extends BaseGameSystem {
    private static final int starglitterId = 221;
    private static final int stardustId = 222;
    @Getter private final Int2ObjectMap<GachaBanner> gachaBanners;
    private WatchService watchService;

    public GachaSystem(GameServer server) {
        super(server);
        this.gachaBanners = new Int2ObjectOpenHashMap<>();
        this.load();
        this.startWatcher(server);
    }

    public int randomRange(int min, int max) { // Both are inclusive
        return ThreadLocalRandom.current().nextInt(max - min + 1) + min;
    }

    public int getRandom(int[] array) {
        return array[randomRange(0, array.length - 1)];
    }

    public synchronized void load() {
        getGachaBanners().clear();
        int autoScheduleId = 1000;
        int autoSortId = 9000;
        try {
            var banners = DataLoader.loadTableToList("Banners", GachaBanner.class);
            if (banners != null && !banners.isEmpty()) {
                for (var banner : banners) {
                    banner.onLoad();
                    if (banner.isDeprecated()) {
                        Grasscutter.getLogger()
                            .error(
                                "A Banner has not been loaded because it contains one or more deprecated fields. Remove the fields mentioned above and reload.");
                    } else if (banner.isDisabled()) {
                        Grasscutter.getLogger().trace("A Banner has not been loaded because it is disabled.");
                    } else {
                        if (banner.scheduleId < 0) banner.scheduleId = autoScheduleId++;
                        if (banner.sortId < 0) banner.sortId = autoSortId--;
                        getGachaBanners().put(banner.scheduleId, banner);
                    }
                }
                Grasscutter.getLogger().debug("Banners successfully loaded.");
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private synchronized int[] removeC6FromPool(int[] itemPool, Player player) {
        IntList temp = new IntArrayList();
        for (int itemId : itemPool) {
            if (InventorySystem.checkPlayerAvatarConstellationLevel(player, itemId) < 6) {
                temp.add(itemId);
            }
        }
        return temp.toIntArray();
    }

    private synchronized int drawRoulette(int[] weights, int cutoff) {
        // This follows the logic laid out in issue #183
        // Simple weighted selection with an upper bound for the roll that cuts off trailing entries
        // All weights must be >= 0
        int total = 0;
        for (int weight : weights) {
            if (weight < 0) {
                throw new IllegalArgumentException("Weights must be non-negative!");
            }
            total += weight;
        }
        int roll = ThreadLocalRandom.current().nextInt(Math.min(total, cutoff));
        int subTotal = 0;
        for (int i = 0; i < weights.length; i++) {
            subTotal += weights[i];
            if (roll < subTotal) {
                return i;
            }
        }
        // throw new IllegalStateException();
        return 0; // This should only be reachable if total==0
    }

    private synchronized int doFallbackRarePull(
            int[] fallback1,
            int[] fallback2,
            int rarity,
            GachaBanner banner,
            PlayerGachaBannerInfo gachaInfo) {
        if (fallback1.length < 1) {
            if (fallback2.length < 1) {
                return getRandom(
                        (rarity == 5)
                                ? GachaBanner.DEFAULT_FALLBACK_ITEMS_5_POOL_2
                                : GachaBanner.DEFAULT_FALLBACK_ITEMS_4_POOL_2);
            } else {
                return getRandom(fallback2);
            }
        } else if (fallback2.length < 1) {
            return getRandom(fallback1);
        } else { // Both pools are possible, use the pool balancer
            int pityPool1 = banner.getPoolBalanceWeight(rarity, gachaInfo.getPityPool(rarity, 1));
            int pityPool2 = banner.getPoolBalanceWeight(rarity, gachaInfo.getPityPool(rarity, 2));
            int chosenPool =
                    switch ((pityPool1 >= pityPool2)
                            ? 1
                            : 0) { // Larger weight must come first for the hard cutoff to function correctly
                        case 1 -> 1 + drawRoulette(new int[] {pityPool1, pityPool2}, 10000);
                        default -> 2 - drawRoulette(new int[] {pityPool2, pityPool1}, 10000);
                    };
            return switch (chosenPool) {
                case 1:
                    gachaInfo.setPityPool(rarity, 1, 0);
                    yield getRandom(fallback1);
                default:
                    gachaInfo.setPityPool(rarity, 2, 0);
                    yield getRandom(fallback2);
            };
        }
    }

    private synchronized int doRarePull(
            int[] featured,
            int[] fallback1,
            int[] fallback2,
            int rarity,
            GachaBanner banner,
            PlayerGachaBannerInfo gachaInfo) {
        int itemId = 0;
        boolean epitomized =
                (banner.hasEpitomized()) && (rarity == 5) && (gachaInfo.getWishItemId() != 0);
        boolean pityEpitomized =
                (gachaInfo.getFailedChosenItemPulls()
                        >= banner.getWishMaxProgress()); // Maximum fate points reached
        boolean pityFeatured =
                (gachaInfo.getFailedFeaturedItemPulls(rarity) >= 1); // Lost previous coinflip
        boolean rollFeatured =
                (this.randomRange(1, 100) <= banner.getEventChance(rarity)); // Won this coinflip
        boolean pullFeatured = pityFeatured || rollFeatured;

        if (epitomized && pityEpitomized) { // Auto pick item when epitomized points reached
            gachaInfo.setFailedFeaturedItemPulls(
                    rarity, 0); // Epitomized item will always be a featured one
            itemId = gachaInfo.getWishItemId();
        } else {
            if (pullFeatured && (featured.length > 0)) {
                gachaInfo.setFailedFeaturedItemPulls(rarity, 0);
                itemId = getRandom(featured);
            } else {
                gachaInfo.addFailedFeaturedItemPulls(
                        rarity,
                        1); // This could be moved into doFallbackRarePull but having it here makes it clearer
                itemId = doFallbackRarePull(fallback1, fallback2, rarity, banner, gachaInfo);
            }
        }

        if (epitomized) {
            if (itemId == gachaInfo.getWishItemId()) { // Reset epitomized points when got wished item
                gachaInfo.setFailedChosenItemPulls(0);
            } else { // Add epitomized points if not get wished item
                gachaInfo.addFailedChosenItemPulls(1);
            }
        }
        return itemId;
    }

    private synchronized int doPull(
            GachaBanner banner, PlayerGachaBannerInfo gachaInfo, BannerPools pools) {
        // Pre-increment all pity pools (yes this makes all calculations assume 1-indexed pity)
        gachaInfo.incPityAll();

        int[] weights = {
            banner.getWeight(5, gachaInfo.getPity5()), banner.getWeight(4, gachaInfo.getPity4()), 10000
        };
        int levelWon = 5 - drawRoulette(weights, 10000);

        return switch (levelWon) {
            case 5:
                gachaInfo.setPity5(0);
                yield doRarePull(
                        pools.rateUpItems5,
                        pools.fallbackItems5Pool1,
                        pools.fallbackItems5Pool2,
                        5,
                        banner,
                        gachaInfo);
            case 4:
                gachaInfo.setPity4(0);
                yield doRarePull(
                        pools.rateUpItems4,
                        pools.fallbackItems4Pool1,
                        pools.fallbackItems4Pool2,
                        4,
                        banner,
                        gachaInfo);
            default:
                yield getRandom(banner.getFallbackItems3());
        };
    }

    public synchronized void doPulls(Player player, int scheduleId, int times) {
        // Sanity check
        if (times != 10 && times != 1) {
            player.sendPacket(new PacketDoGachaRsp(Retcode.RET_GACHA_INVALID_TIMES));
            return;
        }
        Inventory inventory = player.getInventory();
        if (inventory.getInventoryTab(ItemType.ITEM_WEAPON).getSize() + times
                > inventory.getInventoryTab(ItemType.ITEM_WEAPON).getMaxCapacity()) {
            player.sendPacket(new PacketDoGachaRsp(Retcode.RET_ITEM_EXCEED_LIMIT));
            return;
        }

        // Get banner
        GachaBanner banner = this.getGachaBanners().get(scheduleId);
        if (banner == null) {
            player.sendPacket(new PacketDoGachaRsp());
            return;
        }

        // Check against total limit
        PlayerGachaBannerInfo gachaInfo = player.getGachaInfo().getBannerInfo(banner);
        // Call pre-PlayerWishEvent.
        var event =
                new PlayerWishEvent(
                        player,
                        banner,
                        times,
                        new PlayerWishEvent.Pity(
                                gachaInfo.getPity5(),
                                gachaInfo.getPity4(),
                                gachaInfo.getFailedFeaturedItemPulls(4) > 0,
                                banner.hasEpitomized()
                                        ? gachaInfo.getFailedChosenItemPulls() >= 2
                                        : gachaInfo.getFailedFeaturedItemPulls(5) > 0));
        if (!event.call()) {
            player.sendPacket(new PacketDoGachaRsp(Retcode.RET_SVR_ERROR));
            return;
        }

        // Set properties.
        banner = event.getBanner();
        times = event.getWishCount();

        int gachaTimesLimit = banner.getGachaTimesLimit();
        if (gachaTimesLimit != Integer.MAX_VALUE
                && (gachaInfo.getTotalPulls() + times) > gachaTimesLimit) {
            player.sendPacket(new PacketDoGachaRsp(Retcode.RET_GACHA_TIMES_LIMIT));
            return;
        }

        // Spend currency
        ItemParamData cost = banner.getCost(times);
        if (cost.getCount() > 0 && !inventory.payItem(cost)) {
            player.sendPacket(new PacketDoGachaRsp(Retcode.RET_GACHA_COST_ITEM_NOT_ENOUGH));
            return;
        }

        // Add to character
        gachaInfo.addTotalPulls(times);
        BannerPools pools = new BannerPools(banner);
        List<GachaItem> list = new ArrayList<>();
        int stardust = 0, starglitter = 0;

        if (banner.isRemoveC6FromPool()) { // The ultimate form of pity (non-vanilla)
            pools.rateUpItems4 = removeC6FromPool(pools.rateUpItems4, player);
            pools.rateUpItems5 = removeC6FromPool(pools.rateUpItems5, player);
            pools.fallbackItems4Pool1 = removeC6FromPool(pools.fallbackItems4Pool1, player);
            pools.fallbackItems4Pool2 = removeC6FromPool(pools.fallbackItems4Pool2, player);
            pools.fallbackItems5Pool1 = removeC6FromPool(pools.fallbackItems5Pool1, player);
            pools.fallbackItems5Pool2 = removeC6FromPool(pools.fallbackItems5Pool2, player);
        }

        var items = new ArrayList<PlayerWishEvent.WishCompute>();
        for (int i = 0; i < times; i++) {
            // Roll
            int itemId = doPull(banner, gachaInfo, pools);
            ItemData itemData = GameData.getItemDataMap().get(itemId);
            if (itemData == null) {
                continue; // Maybe we should bail out if an item fails instead of rolling the rest?
            }

            // Write gacha record
            GachaRecord gachaRecord = new GachaRecord(itemId, player.getUid(), banner.getGachaType());
            DatabaseHelper.saveGachaRecord(gachaRecord);

            // Create gacha item
            GachaItem.Builder gachaItem = GachaItem.newBuilder();
            int addStardust = 0, addStarglitter = 0;
            boolean isTransferItem = false;

            // Const check
            int constellation = InventorySystem.checkPlayerAvatarConstellationLevel(player, itemId);
            switch (constellation) {
                case -2: // Is weapon
                    switch (itemData.getRankLevel()) {
                        case 5 -> addStarglitter = 10;
                        case 4 -> addStarglitter = 2;
                        default -> addStardust = 15;
                    }
                    break;
                case -1: // New character
                    gachaItem.setIsGachaItemNew(true);
                    break;
                default:
                    if (constellation >= 6) { // C6, give consolation starglitter
                        addStarglitter = (itemData.getRankLevel() == 5) ? 25 : 5;
                    } else { // C0-C5, give constellation item
                        if (banner.isRemoveC6FromPool()
                                && constellation
                                        == 5) { // New C6, remove it from the pools so we don't get C7 in a 10pull
                            pools.removeFromAllPools(new int[] {itemId});
                        }
                        addStarglitter = (itemData.getRankLevel() == 5) ? 10 : 2;
                        int constItemId =
                                itemId + 100; // This may not hold true for future characters. Examples of strictly
                        // correct constellation item lookup are elsewhere for now.
                        boolean haveConstItem =
                            inventory.getInventoryTab(ItemType.ITEM_MATERIAL).getItemById(constItemId) == null;
                        gachaItem.addTransferItems(
                            GachaTransferItem.newBuilder()
                                .setItem(ItemParam.newBuilder().setItemId(constItemId).setCount(1))
                                .setIsTransferItemNew(haveConstItem));
                        // inventory.addItem(constItemId, 1);  // This is now managed by the avatar card item
                        // itself
                    }
                    isTransferItem = true;
                    break;
            }

            // Create item
            GameItem item = new GameItem(itemData);
            items.add(
                    new PlayerWishEvent.WishCompute(
                            item, gachaItem, addStardust, addStarglitter, isTransferItem));
        }

        // Call post-PlayerWishEvent.
        event.finish(items.stream().map(PlayerWishEvent.WishCompute::getItem).toList());

        var eventItems = event.getReceivedItems();
        for (var i = 0; i < items.size(); i++) {
            var compute = items.get(i);
            var gameItem = eventItems.get(i);
            var gachaItem = compute.getGacha();

            gachaItem.setGachaItem(gameItem.toItemParam());
            inventory.addItem(gameItem);

            stardust += compute.getAddStardust();
            starglitter += compute.getAddStarglitter();

            if (compute.getAddStardust() > 0) {
                gachaItem.addTokenItemList(
                        ItemParam.newBuilder().setItemId(stardustId).setCount(compute.getAddStardust()));
            }
            if (compute.getAddStarglitter() > 0) {
                ItemParam starglitterParam =
                        ItemParam.newBuilder()
                                .setItemId(starglitterId)
                                .setCount(compute.getAddStarglitter())
                                .build();
                if (compute.isTransferItem()) {
                    gachaItem.addTransferItems(GachaTransferItem.newBuilder().setItem(starglitterParam));
                }
                gachaItem.addTokenItemList(starglitterParam);
            }

            list.add(gachaItem.build());
        }

        // Add stardust/starglitter
        if (stardust > 0) {
            inventory.addItem(stardustId, stardust);
        }
        if (starglitter > 0) {
            inventory.addItem(starglitterId, starglitter);
        }

        // Packets
        player.sendPacket(new PacketDoGachaRsp(banner, list, gachaInfo));

        // Battle Pass trigger
        player.getBattlePassManager().triggerMission(WatcherTriggerType.TRIGGER_GACHA_NUM, 0, times);
    }

    private synchronized void startWatcher(GameServer server) {
        if (this.watchService == null) {
            try {
                this.watchService = FileSystems.getDefault().newWatchService();
                FileUtils.getDataUserPath("").register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
            } catch (Exception e) {
                Grasscutter.getLogger()
                        .error(
                                "Unable to load the Gacha Manager Watch Service. If ServerOptions.watchGacha is true it will not auto-reload");
            }
        } else {
            Grasscutter.getLogger().error("Cannot reinitialise watcher");
        }
    }

    @Subscribe
    public synchronized void watchBannerJson(GameServerTickEvent tickEvent) {
        if (GAME_OPTIONS.watchGachaConfig) {
            try {
                WatchKey watchKey = watchService.take();

                for (WatchEvent<?> event : watchKey.pollEvents()) {
                    final Path changed = (Path) event.context();
                    if (changed.endsWith("Banners.json")) {
                        Grasscutter.getLogger()
                                .info("Change detected with banners.json. Reloading gacha config");
                        this.load();
                    }
                }

                boolean valid = watchKey.reset();
                if (!valid) {
                    Grasscutter.getLogger()
                            .error(
                                    "Unable to reset Gacha Manager Watch Key. Auto-reload of banners.json will no longer work.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private synchronized GetGachaInfoRsp createProto(Player player) {
        GetGachaInfoRsp.Builder proto = GetGachaInfoRsp.newBuilder().setGachaRandom(12345);

        long currentTime = System.currentTimeMillis() / 1000L;

        for (GachaBanner banner : getGachaBanners().values()) {
            if ((banner.getEndTime() >= currentTime && banner.getBeginTime() <= currentTime)
                    || (banner.getBannerType() == BannerType.STANDARD)) {
                proto.addGachaInfoList(banner.toProto(player));
            }
        }

        return proto.build();
    }

    public GetGachaInfoRsp toProto(Player player) {
        return createProto(player);
    }

    private class BannerPools {
        public int[] rateUpItems4;
        public int[] rateUpItems5;
        public int[] fallbackItems4Pool1;
        public int[] fallbackItems4Pool2;
        public int[] fallbackItems5Pool1;
        public int[] fallbackItems5Pool2;

        public BannerPools(GachaBanner banner) {
            rateUpItems4 = banner.getRateUpItems4();
            rateUpItems5 = banner.getRateUpItems5();
            fallbackItems4Pool1 = banner.getFallbackItems4Pool1();
            fallbackItems4Pool2 = banner.getFallbackItems4Pool2();
            fallbackItems5Pool1 = banner.getFallbackItems5Pool1();
            fallbackItems5Pool2 = banner.getFallbackItems5Pool2();

            if (banner.isAutoStripRateUpFromFallback()) {
                fallbackItems4Pool1 = Utils.setSubtract(fallbackItems4Pool1, rateUpItems4);
                fallbackItems4Pool2 = Utils.setSubtract(fallbackItems4Pool2, rateUpItems4);
                fallbackItems5Pool1 = Utils.setSubtract(fallbackItems5Pool1, rateUpItems5);
                fallbackItems5Pool2 = Utils.setSubtract(fallbackItems5Pool2, rateUpItems5);
            }
        }

        public void removeFromAllPools(int[] itemIds) {
            rateUpItems4 = Utils.setSubtract(rateUpItems4, itemIds);
            rateUpItems5 = Utils.setSubtract(rateUpItems5, itemIds);
            fallbackItems4Pool1 = Utils.setSubtract(fallbackItems4Pool1, itemIds);
            fallbackItems4Pool2 = Utils.setSubtract(fallbackItems4Pool2, itemIds);
            fallbackItems5Pool1 = Utils.setSubtract(fallbackItems5Pool1, itemIds);
            fallbackItems5Pool2 = Utils.setSubtract(fallbackItems5Pool2, itemIds);
        }
    }
}
