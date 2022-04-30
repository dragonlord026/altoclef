package adris.altoclef.tasks.speedrun;

import adris.altoclef.AltoClef;
import adris.altoclef.control.KillAura;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.tasks.DoToClosestBlockTask;
import adris.altoclef.tasks.InteractWithBlockTask;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.container.DoStuffInContainerTask;
import adris.altoclef.tasks.container.LootContainerTask;
import adris.altoclef.tasks.container.SmeltInFurnaceTask;
import adris.altoclef.tasks.entity.KillEntitiesTask;
import adris.altoclef.tasks.misc.EquipArmorTask;
import adris.altoclef.tasks.misc.LootDesertTempleTask;
import adris.altoclef.tasks.misc.PlaceBedAndSetSpawnTask;
import adris.altoclef.tasks.misc.SleepThroughNightTask;
import adris.altoclef.tasks.movement.*;
import adris.altoclef.tasks.resources.*;
import adris.altoclef.tasks.slot.ClickSlotTask;
import adris.altoclef.tasks.slot.ThrowCursorTask;
import adris.altoclef.tasks.squashed.CataloguedResourceTask;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.Dimension;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.MiningRequirement;
import adris.altoclef.util.SmeltTarget;
import adris.altoclef.util.helpers.*;
import adris.altoclef.util.slots.Slot;
import adris.altoclef.util.time.TimerGame;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.CreditsScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.RaycastContext;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Arrays.*;
import static net.minecraft.item.Items.*;
import static net.minecraft.item.Items.SHIELD;

@SuppressWarnings("ALL")
public class BeatMinecraft2Task<DANGER_KEEP_DISTANCE> extends Task {

    private static BeatMinecraftConfig _config;
    static {
        ConfigHelper.loadConfig("configs/beat_minecraft.json", BeatMinecraftConfig::new, BeatMinecraftConfig.class, newConfig -> _config = newConfig);
    }

    public static BeatMinecraftConfig getConfig() {
        return _config;
    }
    private static final Block[] TRACK_BLOCKS = new Block[] {
            Blocks.END_PORTAL_FRAME,
            Blocks.END_PORTAL,
            Blocks.CRAFTING_TABLE, // For pearl trading + gold crafting
            Blocks.CHEST, // For ruined portals
            Blocks.SPAWNER, // For silverfish,
            Blocks.STONE_PRESSURE_PLATE // For desert temples
    };
    private static final ItemTarget[] COLLECT_NETHERITE_INGOT = toItemTargets(NETHERITE_INGOT, 10);

    private static final ItemTarget[] COLLECT_NETHERITE_SMELTING_MATERIAL = combine(
            toItemTargets(FURNACE),
            toItemTargets(COAL, 5),
            toItemTargets(SMITHING_TABLE)
    );

    private static final Item[] COLLECT_NETHERITE_ARMOR = ItemHelper.NETHERITE_ARMORS;
    private static final ItemTarget[] COLLECT_NETHERITE_GEAR = combine(
            toItemTargets(NETHERITE_SWORD),
            toItemTargets(NETHERITE_PICKAXE, 3),
            toItemTargets(NETHERITE_AXE),
            toItemTargets(NETHERITE_SHOVEL)
    );
    private static final ItemTarget[] COLLECT_NETHERITE_GEAR_MIN = combine(
            toItemTargets(NETHERITE_SWORD),
            toItemTargets(NETHERITE_PICKAXE)
    );
    private static final Item[] COLLECT_DIAMOND_ARMOR = ItemHelper.DIAMOND_ARMORS;
    private static final ItemTarget[] COLLECT_DIAMOND_GEAR = combine(
            toItemTargets(DIAMOND_SWORD),
            toItemTargets(DIAMOND_PICKAXE, 3),
            toItemTargets(DIAMOND_AXE),
            toItemTargets(DIAMOND_SHOVEL),
            toItemTargets(CRAFTING_TABLE)
    );
    private static final ItemTarget[] COLLECT_DIAMOND_GEAR_MIN = combine(
            toItemTargets(DIAMOND_SWORD),
            toItemTargets(DIAMOND_PICKAXE)
    );
    private static final Item[] COLLECT_IRON_ARMOR = ItemHelper.IRON_ARMORS;
    private static final ItemTarget[] COLLECT_SHIELD = combine(
            toItemTargets(SHIELD)
    );
    private static final ItemTarget[] COLLECT_IRON_GEAR = combine(
            toItemTargets(IRON_SWORD),
            toItemTargets(IRON_PICKAXE, 3),
            toItemTargets(IRON_AXE),
            toItemTargets(IRON_SHOVEL)
    );
    private static final ItemTarget[] IRON_GEAR_MIN = combine(
            toItemTargets(IRON_SWORD)
    );
    private static final ItemTarget[] COLLECT_STONE_GEAR = combine(
            toItemTargets(STONE_PICKAXE, 2),
            toItemTargets(STONE_SHOVEL, 1),
            toItemTargets(STONE_SWORD,1)
    );
    private static final ItemTarget [] COLLECT_WOOD_GEAR = combine(
            toItemTargets(WOODEN_PICKAXE),
            toItemTargets(WOODEN_SHOVEL),
            toItemTargets(WOODEN_AXE)
    );

    private static final int END_PORTAL_FRAME_COUNT = 12;
    private static final double END_PORTAL_BED_SPAWN_RANGE = 8;


    private BlockPos _endPortalCenterLocation;
    private boolean _ranStrongholdLocator;
    private boolean _endPortalOpened;
    private BlockPos _bedSpawnLocation;

    private List<BlockPos> _notRuinedPortalChests = new ArrayList<>();

    private int _cachedFilledPortalFrames = 0;

    private final HashMap<Item, Integer> _cachedEndItemDrops = new HashMap<>();
    private final HashMap<Item, Integer> _cachedNetherItemDrops = new HashMap<>();
    private final HashMap<Item, Integer> _cachedOverworldItemDrops = new HashMap<>();

    // Controls whether we CAN walk on the end portal.
    private boolean _enterindEndPortal = false;

    // For some reason, after death there's a frame where the game thinks there are NO items in the end.
    private final TimerGame _cachedEndItemNothingWaitTime = new TimerGame(2);
    private final TimerGame _cachedNetherItemNothingWaitTime = new TimerGame(2);
    private final TimerGame _cachedOverworldItemNothingWaitTime = new TimerGame(2);

    // We don't want curse of binding
    private static final Predicate<ItemStack> _noCurseOfBinding = stack -> {
        boolean hasBinding = false;
        for (NbtElement elm : stack.getEnchantments()) {
            NbtCompound comp = (NbtCompound) elm;
            if (comp.getString("id").equals("minecraft:binding_curse")) {
                return false;
            }
        }
        return true;
    };
    private String _playerName;
    private ItemTarget[] _targets;
    private CataloguedResourceTask _resourceTask;

    public void GiveItemToPlayerTask(String player, ItemTarget... targets) {
        _playerName = player;
        _targets = targets;
        _resourceTask = TaskCatalogue.getSquashedItemTask(targets);
    }

    private Task _foodTask;
    private Task _gearTask;
    private Task _lootTask;
    private CataloguedResourceTask _droppingItems;
    private Task _throwTask;
    private final List<ItemTarget> _throwTarget = new ArrayList<>();
    private final Task _buildMaterialsTask;
    private final PlaceBedAndSetSpawnTask _setBedSpawnTask = new PlaceBedAndSetSpawnTask();
    private final GoToStrongholdPortalTask _locateStrongholdTask;
    private final Task _goToNetherTask = new DefaultGoToDimensionTask(Dimension.NETHER); // To keep the portal build cache.
    private boolean _collectingEyes;
    private final Task _getOneBedTask = TaskCatalogue.getItemTask("bed", 1);
    private final Task _sleepThroughNightTask = new SleepThroughNightTask();
    private final Task _killDragonBedStratsTask = new KillEnderDragonWithBedsTask(new WaitForDragonAndPearlTask());
    public static Class[] HOSTILE_ANNOYING_CLASSES = new Class[]{SkeletonEntity.class, ZombieEntity.class, SpiderEntity.class, CaveSpiderEntity.class, WitchEntity.class, PiglinEntity.class, PiglinBruteEntity.class, HoglinEntity.class, ZoglinEntity.class, BlazeEntity.class, WitherSkeletonEntity.class, PillagerEntity.class, DrownedEntity.class};
    private final KillAura _killAura = new KillAura();
    private static final int TOO_MANY_MOBS = 5;
    private static final double TOO_LITTLE_HEALTH = 5;
    private static final double MOB_RADIUS = 8;
    private static boolean isHoveringAboveLavaOrTooHigh(AltoClef mod, Entity entity) {
        int MAX_HEIGHT = 23;
        for (BlockPos check = entity.getBlockPos(); entity.getBlockPos().getY() - check.getY() < MAX_HEIGHT; check = check.down()) {
            if (mod.getWorld().getBlockState(check).getBlock() == Blocks.LAVA) return true;
            if (WorldHelper.isSolid(mod, check)) return false;
        }
        return true;
    }



    // End specific dragon breath avoidance
    private final DragonBreathTracker _dragonBreathTracker = new DragonBreathTracker();
    private boolean _escapingDragonsBreath;

    public BeatMinecraft2Task() {
        _locateStrongholdTask = new GoToStrongholdPortalTask(_config.targetEyes);
        _buildMaterialsTask = new GetBuildingMaterialsTask(_config.buildMaterialCount);
    }

    @Override
    protected void onStart(AltoClef mod) {

        // Add a warning to make sure the user at least knows to change the settings.
        String settingsWarningTail = "in \".minecraft/altoclef_settings.json\". @gamer may break if you don't add this! (sorry!)";
        if (!ArrayUtils.contains(mod.getModSettings().getThrowawayItems(mod), END_STONE)) {
            Debug.logWarning("\"end_stone\" is not part of your \"throwawayItems\" list " + settingsWarningTail);
        }
        if (!mod.getModSettings().shouldThrowawayUnusedItems()) {
            Debug.logWarning("\"throwawayUnusedItems\" is not set to true " + settingsWarningTail);
        }

        mod.getBlockTracker().trackBlock(TRACK_BLOCKS);
        mod.getBlockTracker().trackBlock(ItemHelper.itemsToBlocks(ItemHelper.BED));
        mod.getBehaviour().push();
        mod.getBehaviour().addProtectedItems(ENDER_EYE, BLAZE_ROD, ENDER_PEARL, CRAFTING_TABLE);
        mod.getBehaviour().addProtectedItems(ItemHelper.BED);
        // Allow walking on end portal
        mod.getBehaviour().allowWalkingOn(blockPos -> _enterindEndPortal && mod.getChunkTracker().isChunkLoaded(blockPos) && mod.getWorld().getBlockState(blockPos).getBlock() == Blocks.END_PORTAL);

        // Avoid dragon breath
        mod.getBehaviour().avoidWalkingThrough(blockPos -> {
            return WorldHelper.getCurrentDimension() == Dimension.END && !_escapingDragonsBreath && _dragonBreathTracker.isTouchingDragonBreath(blockPos);
        });

        // Don't break the bed we placed near the end portal
        mod.getBehaviour().avoidBlockBreaking(blockPos -> {
            if (_bedSpawnLocation != null) {
                return blockPos.equals(WorldHelper.getBedHead(mod, _bedSpawnLocation)) || blockPos.equals(WorldHelper.getBedFoot(mod, _bedSpawnLocation));
            }
            return false;
        });
    }

    @Override
    protected Task onTick(AltoClef mod) {
        /*
        if in the overworld:
          if end portal found:
            if end portal opened:
              @make sure we have iron gear and enough beds to kill the dragon first, considering whether that gear was dropped in the end
              @enter end portal
            else if we have enough eyes of ender:
              @fill in the end portal
          else if we have enough eyes of ender:
            @locate the end portal
          else:
            if we don't have diamond gear:
              if we have no food:
                @get a little bit of food
              @get diamond gear
            @go to the nether
        if in the nether:
          if we don't have enough blaze rods:
            @kill blazes till we do
          else if we don't have enough pearls:
            @kill enderman till we do
          else:
            @leave the nether
        if in the end:
          if we have a bed:
            @do bed strats
          else:
            @just hit the dragon normally
         */

        // By default, don't walk over end portals.
        _enterindEndPortal = false;

        Predicate<Task> isCraftingTableTask = task -> {
            if (task instanceof DoStuffInContainerTask cont) {
                return cont.getContainerTarget().matches(CRAFTING_TABLE);
            }
            return false;
        };
        if (_throwTask != null && _throwTask.isActive() && !_throwTask.isFinished(mod)) {
            setDebugState("Throwing items");
            return _throwTask;
        }

        // Portable crafting table.
        // If we're NOT using our crafting table right now and there's one nearby, grab it.
        if (!_endPortalOpened && WorldHelper.getCurrentDimension() != Dimension.END && _config.rePickupCraftingTable && !mod.getItemStorage().hasItem(CRAFTING_TABLE) && !thisOrChildSatisfies(isCraftingTableTask)
                && (mod.getBlockTracker().anyFound(blockPos -> WorldHelper.canBreak(mod, blockPos), Blocks.CRAFTING_TABLE)
                || mod.getEntityTracker().itemDropped(CRAFTING_TABLE))) {
            setDebugState("Pick up crafting table while we're at it");
            return new MineAndCollectTask(CRAFTING_TABLE, 1, new Block[]{Blocks.CRAFTING_TABLE}, MiningRequirement.HAND);
        }

        // End stuff.
        if (WorldHelper.getCurrentDimension() == Dimension.END) {

            // Dragons breath avoidance
            _dragonBreathTracker.updateBreath(mod);
            for (BlockPos playerIn : WorldHelper.getBlocksTouchingPlayer(mod)) {
                if (_dragonBreathTracker.isTouchingDragonBreath(playerIn)) {
                    setDebugState("ESCAPE dragons breath");
                    _escapingDragonsBreath = true;
                    return _dragonBreathTracker.getRunAwayTask();
                }
            }
            _escapingDragonsBreath = false;

            // If we find an ender portal, just GO to it!!!
            if (mod.getBlockTracker().anyFound(Blocks.END_PORTAL)) {
                setDebugState("WOOHOO");
                _enterindEndPortal = true;
                return new DoToClosestBlockTask(
                        blockPos -> new GetToBlockTask(blockPos.up()),
                        Blocks.END_PORTAL
                );
            }

            // If we have bed, do bed strats, otherwise punk normally.
            updateCachedEndItems(mod);
            // Grab beds
            if (mod.getEntityTracker().itemDropped(ItemHelper.BED) && mod.getItemStorage().getItemCount(ItemHelper.BED) < _config.requiredBeds)
                return new PickupDroppedItemTask(new ItemTarget(ItemHelper.BED), true);
            // Grab tools
            if (!mod.getItemStorage().hasItem(IRON_PICKAXE, DIAMOND_PICKAXE, NETHERITE_PICKAXE)) {
                if (mod.getEntityTracker().itemDropped(IRON_PICKAXE))
                    return new PickupDroppedItemTask(IRON_PICKAXE, 1);
                if (mod.getEntityTracker().itemDropped(DIAMOND_PICKAXE))
                    return new PickupDroppedItemTask(DIAMOND_PICKAXE, 1);
                if (mod.getEntityTracker().itemDropped(NETHERITE_PICKAXE))
                    return new PickupDroppedItemTask(NETHERITE_PICKAXE, 1);
            }
            if (!mod.getItemStorage().hasItem(WATER_BUCKET) && mod.getEntityTracker().itemDropped(WATER_BUCKET))
                return new PickupDroppedItemTask(WATER_BUCKET, 1);
            // Grab armor
            for (Item armorCheck : COLLECT_NETHERITE_ARMOR) {
                if (!StorageHelper.isArmorEquipped(mod, armorCheck)) {
                    if (mod.getItemStorage().hasItem(armorCheck)) {
                        return new EquipArmorTask(armorCheck);
                    }
                    if (mod.getEntityTracker().itemDropped(armorCheck)) {
                        return new PickupDroppedItemTask(armorCheck, 1);
                    }
                }
            }
            if (mod.getItemStorage().hasItem(ItemHelper.BED) || (_killDragonBedStratsTask.isActive() && !_killDragonBedStratsTask.isFinished(mod))) {
                setDebugState("Bed strats");
                return _killDragonBedStratsTask;
            }
            setDebugState("No beds, regular strats.");
            return new KillEnderDragonTask();
        } else {
            // We're not in the end so reset our "end cache" timer
            _cachedEndItemNothingWaitTime.reset();
        }

        // If die in Nether
        if (WorldHelper.getCurrentDimension() == Dimension.NETHER) {
            updateCachedNetherItems(mod);
            // Grab tools
            if (!mod.getItemStorage().hasItem(IRON_PICKAXE, DIAMOND_PICKAXE, NETHERITE_PICKAXE)) {
                if (mod.getEntityTracker().itemDropped(IRON_PICKAXE))
                    return new PickupDroppedItemTask(IRON_PICKAXE, 1);
                if (mod.getEntityTracker().itemDropped(DIAMOND_PICKAXE))
                    return new PickupDroppedItemTask(DIAMOND_PICKAXE, 1);
                if (mod.getEntityTracker().itemDropped(NETHERITE_PICKAXE))
                    return new PickupDroppedItemTask(NETHERITE_PICKAXE, 1);
            }
            if (!mod.getItemStorage().hasItem(WATER_BUCKET) && mod.getEntityTracker().itemDropped(WATER_BUCKET))
                return new PickupDroppedItemTask(WATER_BUCKET, 1);
            // Grab Netherite Armor
            for (Item armorCheck : COLLECT_NETHERITE_ARMOR) {
                if (!StorageHelper.isArmorEquipped(mod, armorCheck)) {
                    if (mod.getItemStorage().hasItem(armorCheck)) {
                        return new EquipArmorTask(armorCheck);
                    }
                    if (mod.getEntityTracker().itemDropped(armorCheck)) {
                        return new PickupDroppedItemTask(armorCheck, 1);
                    }
                }
            }
        } else {
            // We're not in the nether so reset our "nether cache" timer
            _cachedNetherItemNothingWaitTime.reset();
        }

        // If die in Overworld
        if (WorldHelper.getCurrentDimension() == Dimension.OVERWORLD) {
            updateCachedOverworldItems(mod);
            // Grab tools
            if (!mod.getItemStorage().hasItem(IRON_PICKAXE, DIAMOND_PICKAXE, NETHERITE_PICKAXE)) {
                if (mod.getEntityTracker().itemDropped(IRON_PICKAXE))
                    return new PickupDroppedItemTask(IRON_PICKAXE, 1);
                if (mod.getEntityTracker().itemDropped(DIAMOND_PICKAXE))
                    return new PickupDroppedItemTask(DIAMOND_PICKAXE, 1);
                if (mod.getEntityTracker().itemDropped(NETHERITE_PICKAXE))
                    return new PickupDroppedItemTask(NETHERITE_PICKAXE, 1);
            }
            if (!mod.getItemStorage().hasItem(WATER_BUCKET) && mod.getEntityTracker().itemDropped(WATER_BUCKET))
                return new PickupDroppedItemTask(WATER_BUCKET, 1);
            // Grab Diamond Armor
            for (Item armorCheck : COLLECT_DIAMOND_ARMOR) {
                if (!StorageHelper.isArmorEquipped(mod, armorCheck)) {
                    if (mod.getItemStorage().hasItem(armorCheck)) {
                        return new EquipArmorTask(armorCheck);
                    }
                    if (mod.getEntityTracker().itemDropped(armorCheck)) {
                        return new PickupDroppedItemTask(armorCheck, 1);
                    }
                }
            }
        }

        Optional<Entity> toKill = Optional.empty();
        // If there is a mob, kill it.
        if (mod.getEntityTracker().entityFound(HOSTILE_ANNOYING_CLASSES)) {

            // If we're in danger and there are too many blazes, run away.
            if (mod.getEntityTracker().getTrackedEntities(HostileEntity.class).size() >= TOO_MANY_MOBS && mod.getPlayer().getHealth() <= TOO_LITTLE_HEALTH) {
                setDebugState("Running away as there are too many mobs nearby.");
                return new TimeoutWanderTask();
            }

            toKill = mod.getEntityTracker().getClosestEntity(mod.getPlayer().getPos(), HOSTILE_ANNOYING_CLASSES);

            if (toKill.isPresent()) {
                Entity kill = toKill.get();
                Vec3d nearest = kill.getPos();

                double sqDistanceToPlayer = nearest.squaredDistanceTo(mod.getPlayer().getPos());
                // Ignore if the mob is too far away.
                if (sqDistanceToPlayer > MOB_RADIUS * MOB_RADIUS) {
                    // If the mob can see us it needs to go
                    BlockHitResult hit = mod.getWorld().raycast(new RaycastContext(mod.getPlayer().getCameraPosVec(1.0F), kill.getCameraPosVec(1.0F), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mod.getPlayer()));
                    if (hit != null && hit.getBlockPos().getSquaredDistance(mod.getPlayer().getPos(), false) < sqDistanceToPlayer) {
                        toKill = Optional.empty();
                    }
                }
            }
        }

        if (toKill.isPresent() && toKill.get().isAlive()) {
            setDebugState("Killing Mob");
            return new KillEntitiesTask(entity -> !isHoveringAboveLavaOrTooHigh(mod, entity), HOSTILE_ANNOYING_CLASSES);
        }


        // Check for end portals. Always.
        if (!endPortalOpened(mod, _endPortalCenterLocation) && WorldHelper.getCurrentDimension() == Dimension.OVERWORLD) {
            Optional<BlockPos> endPortal = mod.getBlockTracker().getNearestTracking(Blocks.END_PORTAL);
            if (endPortal.isPresent()) {
                _endPortalCenterLocation = endPortal.get();
                _endPortalOpened = true;
            } else {
                // TODO: Test that this works, for some reason the bot gets stuck near the stronghold and it keeps "Searching" for the portal
                _endPortalCenterLocation = doSimpleSearchForEndPortal(mod);
            }
        }

        // Sleep through night.
        if (_config.sleepThroughNight && !_endPortalOpened && WorldHelper.getCurrentDimension() == Dimension.OVERWORLD) {
            if (WorldHelper.canSleep()) {
                setDebugState("Sleeping through night");
                return _sleepThroughNightTask;
            }
            if (!mod.getItemStorage().hasItem(ItemHelper.BED)) {
                if (mod.getBlockTracker().anyFound(blockPos -> WorldHelper.canBreak(mod, blockPos), ItemHelper.itemsToBlocks(ItemHelper.BED))
                        || shouldForce(mod, _getOneBedTask)) {
                    setDebugState("Grabbing a bed we found to sleep through the night.");
                    return _getOneBedTask;
                }
            }
        }

        // Do we need more eyes?
        boolean noEyesPlease = (endPortalOpened(mod, _endPortalCenterLocation) || WorldHelper.getCurrentDimension() == Dimension.END);
        int filledPortalFrames = getFilledPortalFrames(mod, _endPortalCenterLocation);
        int eyesNeededMin = noEyesPlease ? 0 : _config.minimumEyes - filledPortalFrames;
        int eyesNeeded    = noEyesPlease ? 0 : _config.targetEyes  - filledPortalFrames;
        int eyes = mod.getItemStorage().getItemCount(ENDER_EYE);
        if (eyes < eyesNeededMin || (!_ranStrongholdLocator && _collectingEyes && eyes < eyesNeeded)) {
            _collectingEyes = true;
            return getEyesOfEnderTask(mod, eyesNeeded);
        } else {
            _collectingEyes = false;
        }

        // We have eyes. Locate our portal + enter.
        switch (WorldHelper.getCurrentDimension()) {
            case OVERWORLD -> {
                // If we found our end portal...
                if (endPortalFound(mod, _endPortalCenterLocation)) {

                    // Destroy silverfish spawner
                    if (StorageHelper.miningRequirementMetInventory(mod, MiningRequirement.WOOD)) {
                        Optional<BlockPos> silverfish = mod.getBlockTracker().getNearestTracking(blockPos -> {
                            return WorldHelper.getSpawnerEntity(mod, blockPos) instanceof SilverfishEntity;
                        }, Blocks.SPAWNER);
                        if (silverfish.isPresent()) {
                            return new DestroyBlockTask(silverfish.get());
                        }
                    }

                    // Get remaining beds.
                    if (needsBeds(mod)) {
                        setDebugState("Collecting beds.");
                        return getBedTask(mod);
                    }
                    if (_config.placeSpawnNearEndPortal) {
                        if (!spawnSetNearPortal(mod, _endPortalCenterLocation)) {
                            setDebugState("Setting spawn near end portal");
                            return setSpawnNearPortalTask(mod);
                        }
                    }
                    if (endPortalOpened(mod, _endPortalCenterLocation)) {
                        // Does our (current inventory) + (end dropped items inventory) satisfy (base requirements)?
                        //      If not, obtain (base requirements) - (end dropped items).
                        setDebugState("Getting equipment for End");
                        if (!hasItemOrDroppedInEnd(mod, IRON_SWORD) && !hasItemOrDroppedInEnd(mod, NETHERITE_SWORD)) {
                            return TaskCatalogue.getItemTask(NETHERITE_SWORD, 1);
                        }
                        if (!hasItemOrDroppedInEnd(mod, WATER_BUCKET)) {
                            return TaskCatalogue.getItemTask(WATER_BUCKET, 1);
                        }
                        if (!hasItemOrDroppedInEnd(mod, IRON_PICKAXE) && !hasItemOrDroppedInEnd(mod, NETHERITE_PICKAXE)) {
                            return TaskCatalogue.getItemTask(NETHERITE_PICKAXE, 1);
                        }
                        if (needsBuildingMaterials(mod)) {
                            return _buildMaterialsTask;
                        }

                        // We're as ready as we'll ever be, hop into the portal!
                        setDebugState("Entering End");
                        _enterindEndPortal = true;
                        return new DoToClosestBlockTask(
                                blockPos -> new GetToBlockTask(blockPos.up()),
                                Blocks.END_PORTAL
                        );
                    } else {

                        // Open the portal! (we have enough eyes, do it)
                        setDebugState("Opening End Portal");
                        return new DoToClosestBlockTask(
                                blockPos -> new InteractWithBlockTask(ENDER_EYE, blockPos),
                                blockPos -> !isEndPortalFrameFilled(mod, blockPos),
                                Blocks.END_PORTAL_FRAME
                        );
                    }
                } else {
                    // Get beds before starting our portal location.
                    if (WorldHelper.getCurrentDimension() == Dimension.OVERWORLD && needsBeds(mod)) {
                        setDebugState("Getting beds before stronghold search.");
                        return getBedTask(mod);
                    }
                    // Portal Location
                    setDebugState("Locating End Portal...");
                    _ranStrongholdLocator = true;
                    return _locateStrongholdTask;
                }
            }
            case NETHER -> {
                // Portal Location
                setDebugState("Locating End Portal...");
                if (needsBuildingMaterials(mod)) {
                    return _buildMaterialsTask;
                }
                return _locateStrongholdTask;
            }
        }
        return null;
    }

    private boolean needsBuildingMaterials(AltoClef mod) {
        return StorageHelper.getBuildingMaterialCount(mod) < _config.minBuildMaterialCount || shouldForce(mod, _buildMaterialsTask);
    }

    private void updateCachedEndItems(AltoClef mod) {
        List<ItemEntity> droppedItems = mod.getEntityTracker().getDroppedItems();
        // If we have no items, it COULD be because we're dead. Wait a little.
        if (droppedItems.isEmpty()) {
            if (!_cachedEndItemNothingWaitTime.elapsed()) {
                return;
            }
        } else {
            _cachedEndItemNothingWaitTime.reset();
        }
        _cachedEndItemDrops.clear();
        for (ItemEntity entity : droppedItems) {
            Item item = entity.getStack().getItem();
            int count = entity.getStack().getCount();
            _cachedEndItemDrops.put(item, _cachedEndItemDrops.getOrDefault(item, 0) + count);
        }
    }
    private int getEndCachedCount(Item item) {
        return _cachedEndItemDrops.getOrDefault(item, 0);
    }
    private boolean droppedInEnd(Item item) {
        return getEndCachedCount(item) > 0;
    }
    private boolean hasItemOrDroppedInEnd(AltoClef mod, Item item) {
        return mod.getItemStorage().hasItem(item) || droppedInEnd(item);
    }

    private void updateCachedNetherItems (AltoClef mod) {
        List<ItemEntity> droppedItems = mod.getEntityTracker().getDroppedItems();
        // If we have no items, it COULD be because we're dead. Wait a little.
        if (droppedItems.isEmpty()) {
            if (!_cachedNetherItemNothingWaitTime.elapsed()) {
                return;
            }
        } else {
            _cachedNetherItemNothingWaitTime.reset();
        }
        _cachedNetherItemDrops.clear();
        for (ItemEntity entity : droppedItems) {
            Item item = entity.getStack().getItem();
            int count = entity.getStack().getCount();
            _cachedNetherItemDrops.put(item, _cachedNetherItemDrops.getOrDefault(item, 0) + count);
        }
    }
    private int getNetherCachedCount(Item item) {
        return _cachedNetherItemDrops.getOrDefault(item, 0);
    }
    private boolean droppedInNether(Item item) {
        return getNetherCachedCount(item) > 0;
    }
    private boolean hasItemOrDroppedInNether(AltoClef mod, Item item) {
        return mod.getItemStorage().hasItem(item) || droppedInNether(item);
    }

    private void updateCachedOverworldItems (AltoClef mod) {
        List<ItemEntity> droppedItems = mod.getEntityTracker().getDroppedItems();
        // If we have no items, it COULD be because we're dead. Wait a little.
        if (droppedItems.isEmpty()) {
            if (!_cachedOverworldItemNothingWaitTime.elapsed()) {
                return;
            }
        } else {
            _cachedOverworldItemNothingWaitTime.reset();
        }
        _cachedOverworldItemDrops.clear();
        for (ItemEntity entity : droppedItems) {
            Item item = entity.getStack().getItem();
            int count = entity.getStack().getCount();
            _cachedOverworldItemDrops.put(item, _cachedOverworldItemDrops.getOrDefault(item, 0) + count);
        }
    }
    private int getOverworldCachedCount(Item item) {
        return _cachedOverworldItemDrops.getOrDefault(item, 0);
    }
    private boolean droppedInOverworld(Item item) {
        return getOverworldCachedCount(item) > 0;
    }
    private boolean hasItemOrDroppedInOverworld(AltoClef mod, Item item) {
        return mod.getItemStorage().hasItem(item) || droppedInOverworld(item);
    }

    private List<Item> lootableItems(AltoClef mod) {
        List<Item> lootable = new ArrayList<>();
        lootable.add(GOLDEN_APPLE);
        lootable.add(ENCHANTED_GOLDEN_APPLE);
        lootable.add(GLISTERING_MELON_SLICE);
        lootable.add(GOLDEN_CARROT);
        lootable.add(OBSIDIAN);

        if (!mod.getItemStorage().hasItemInventoryOnly(GOLD_INGOT)) {
            lootable.add(GOLD_INGOT);
        }
        if (!mod.getItemStorage().hasItemInventoryOnly(FLINT_AND_STEEL)) {
            lootable.add(FLINT_AND_STEEL);
            if (!mod.getItemStorage().hasItemInventoryOnly(FIRE_CHARGE)) {
                lootable.add(FIRE_CHARGE);
            }
        }
        if (!mod.getItemStorage().hasItemInventoryOnly(BUCKET) && !mod.getItemStorage().hasItemInventoryOnly(WATER_BUCKET)) {
            lootable.add(IRON_INGOT);
        }
        if (!StorageHelper.itemTargetsMetInventory(mod, COLLECT_NETHERITE_GEAR_MIN)) {
            lootable.add(DIAMOND);
        }
        if (!mod.getItemStorage().hasItemInventoryOnly(FLINT)) {
            lootable.add(FLINT);
        }
        return lootable;
    }


    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.getBlockTracker().stopTracking(TRACK_BLOCKS);
        mod.getBlockTracker().stopTracking(ItemHelper.itemsToBlocks(ItemHelper.BED));
        mod.getBehaviour().pop();
    }

    @Override
    protected boolean isEqual(Task other) {
        return other instanceof BeatMinecraft2Task;
    }

    @Override
    protected String toDebugString() {
        return "Beating the Game.";
    }

    private boolean endPortalFound(AltoClef mod, BlockPos endPortalCenter) {
        if (endPortalCenter == null) {
            return false;
        }
        if (endPortalOpened(mod, endPortalCenter)) {
            return true;
        }
        return getFrameBlocks(endPortalCenter).stream().allMatch(frame -> mod.getBlockTracker().blockIsValid(frame, Blocks.END_PORTAL_FRAME));
    }
    private boolean endPortalOpened(AltoClef mod, BlockPos endPortalCenter) {
        return _endPortalOpened && endPortalCenter != null && mod.getBlockTracker().blockIsValid(endPortalCenter, Blocks.END_PORTAL);
    }
    private boolean spawnSetNearPortal(AltoClef mod, BlockPos endPortalCenter) {
        return _bedSpawnLocation != null && mod.getBlockTracker().blockIsValid(_bedSpawnLocation, ItemHelper.itemsToBlocks(ItemHelper.BED));
    }
    private int getFilledPortalFrames(AltoClef mod, BlockPos endPortalCenter) {
        // If we have our end portal, this doesn't matter.
        if (endPortalFound(mod, endPortalCenter)) {
            return END_PORTAL_FRAME_COUNT;
        }
        if (endPortalFound(mod, endPortalCenter)) {
            List<BlockPos> frameBlocks = getFrameBlocks(endPortalCenter);
            // If EVERY portal frame is loaded, consider updating our cached filled portal count.
            if (frameBlocks.stream().allMatch(blockPos -> mod.getChunkTracker().isChunkLoaded(blockPos))) {
                _cachedFilledPortalFrames = frameBlocks.stream().reduce(0, (count, blockPos) ->
                                count + (isEndPortalFrameFilled(mod, blockPos) ? 1 : 0),
                        Integer::sum);
            }
            return _cachedFilledPortalFrames;
        }
        return 0;
    }

    private boolean canBeLootablePortalChest(AltoClef mod, BlockPos blockPos) {
        if (mod.getWorld().getBlockState(blockPos.up(1)).getBlock() == Blocks.WATER || blockPos.getY() < 50) {
            return false;
        }
        for (BlockPos check : WorldHelper.scanRegion(mod, blockPos.add(-4, -2, -4), blockPos.add(4, 2, 4))) {
            if (mod.getWorld().getBlockState(check).getBlock() == Blocks.NETHERRACK) {
                return true;
            }
        }
        _notRuinedPortalChests.add(blockPos);
        return false;
    }

    private Optional<BlockPos> locateClosestUnopenedRuinedPortalChest(AltoClef mod) {
        if (WorldHelper.getCurrentDimension() != Dimension.OVERWORLD) {
            return Optional.empty();
        }
        return mod.getBlockTracker().getNearestTracking(blockPos -> !_notRuinedPortalChests.contains(blockPos) && WorldHelper.isUnopenedChest(mod, blockPos) && mod.getPlayer().getBlockPos().isWithinDistance(blockPos, 150) && canBeLootablePortalChest(mod, blockPos), Blocks.CHEST);
    }

    private static List<BlockPos> getFrameBlocks(BlockPos endPortalCenter) {
        Vec3i[] frameOffsets = new Vec3i[] {
                new Vec3i(2, 0, 1),
                new Vec3i(2, 0, 0),
                new Vec3i(2, 0, -1),
                new Vec3i(-2, 0, 1),
                new Vec3i(-2, 0, 0),
                new Vec3i(-2, 0, -1),
                new Vec3i(1, 0, 2),
                new Vec3i(0, 0, 2),
                new Vec3i(-1, 0, 2),
                new Vec3i(1, 0, -2),
                new Vec3i(0, 0, -2),
                new Vec3i(-1, 0, -2)
        };
        return stream(frameOffsets).map(endPortalCenter::add).toList();
    }

    private Task getEyesOfEnderTask(AltoClef mod, int targetEyes) {
        if (mod.getEntityTracker().itemDropped(ENDER_EYE)) {
            setDebugState("Picking up Dropped Eyes");
            return new PickupDroppedItemTask(ENDER_EYE, targetEyes);
        }

        int eyeCount = mod.getItemStorage().getItemCount(ENDER_EYE);

        int blazePowderCount = mod.getItemStorage().getItemCount(BLAZE_POWDER);
        int blazeRodCount = mod.getItemStorage().getItemCount(BLAZE_ROD);
        int blazeRodTarget = (int)Math.ceil(((double)targetEyes - eyeCount - blazePowderCount) / 2.0);
        int enderPearlTarget = targetEyes - eyeCount;
        boolean needsBlazeRods = blazeRodCount < blazeRodTarget;
        boolean needsBlazePowder = eyeCount + blazePowderCount < targetEyes;
        boolean needsEnderPearls = mod.getItemStorage().getItemCount(ENDER_PEARL) < enderPearlTarget;

        if (needsBlazePowder && !needsBlazeRods) {
            // We have enough blaze rods.
            setDebugState("Crafting blaze powder");
            return TaskCatalogue.getItemTask(BLAZE_POWDER, targetEyes - eyeCount);
        }

        if (!needsBlazePowder && !needsEnderPearls) {
            // Craft ender eyes
            setDebugState("Crafting Ender Eyes");
            return TaskCatalogue.getItemTask(ENDER_EYE, targetEyes);
        }

        // Get blaze rods + pearls...
        switch (WorldHelper.getCurrentDimension()) {
            case OVERWORLD -> {
                // Make sure we have gear, then food.
                for (Item iron : COLLECT_IRON_ARMOR) {
                    if (!StorageHelper.isArmorEquipped(mod, COLLECT_DIAMOND_ARMOR)) {
                        if (mod.getItemStorage().hasItem(iron) && !StorageHelper.isArmorEquipped(mod, iron))
                            return new EquipArmorTask(COLLECT_IRON_ARMOR);
                    }
                }
                for (Item diamond : COLLECT_DIAMOND_ARMOR) {
                    if (!StorageHelper.isArmorEquipped(mod, COLLECT_NETHERITE_ARMOR)) {
                        if (mod.getItemStorage().hasItem(diamond) && !StorageHelper.isArmorEquipped(mod, diamond))
                            if (StorageHelper.isArmorEquipped(mod, COLLECT_IRON_ARMOR))
                                return new EquipArmorTask(COLLECT_DIAMOND_ARMOR);
                    }
                }
                if (shouldForce(mod, _lootTask)) {
                    return _lootTask;
                }
                if (_config.searchRuinedPortals) {
                    // Check for ruined portals
                    Optional<BlockPos> chest = locateClosestUnopenedRuinedPortalChest(mod);
                    if (chest.isPresent()) {
                        setDebugState("Looting ruined portal chest for goodies");
                        _lootTask = new LootContainerTask(chest.get(), lootableItems(mod), _noCurseOfBinding);
                        return _lootTask;
                    }
                }
                if (_config.searchDesertTemples && StorageHelper.miningRequirementMetInventory(mod, MiningRequirement.WOOD)) {
                    // Check for desert temples
                    BlockPos temple = WorldHelper.getADesertTemple(mod);
                    if (temple != null) {
                        setDebugState("Looting desert temple for goodies");
                        _lootTask = new LootDesertTempleTask(temple, lootableItems(mod));
                        return _lootTask;
                    }
                }
                if (shouldForce(mod, _gearTask) && !StorageHelper.isArmorEquippedAll(mod, COLLECT_NETHERITE_ARMOR)) {
                    setDebugState("Getting gear for Ender Eye journey");
                    return _gearTask;
                }
                if (shouldForce(mod, _foodTask)) {
                    setDebugState("Getting Food for Ender Eye journey");
                    return _foodTask;
                }
                // Smelt remaining raw food
                if (_config.alwaysCookRawFood) {
                    for (Item raw : ItemHelper.RAW_FOODS) {
                        if (mod.getItemStorage().hasItem(raw)) {
                            Optional<Item> cooked = ItemHelper.getCookedFood(raw);
                            if (cooked.isPresent()) {
                                int targetCount = mod.getItemStorage().getItemCount(cooked.get()) + mod.getItemStorage().getItemCount(raw);
                                setDebugState("Smelting raw food: " + ItemHelper.stripItemName(raw));
                                return new SmeltInFurnaceTask(new SmeltTarget(new ItemTarget(cooked.get(), targetCount), new ItemTarget(raw, targetCount)));
                            }
                        }
                    }
                }

                Optional<Vec3d> lastPos = mod.getEntityTracker().getPlayerMostRecentPosition(_playerName);

                if (lastPos.isEmpty()) {
                    setDebugState("No player found/detected. Doing nothing until player loads into render distance.");
                    return null;
                }

                Vec3d targetPos = lastPos.get().add(0, 0.2f, 0);


                boolean diamondGearSatisfied = StorageHelper.itemTargetsMet(mod, COLLECT_DIAMOND_GEAR_MIN) && StorageHelper.isArmorEquippedAll(mod, COLLECT_DIAMOND_ARMOR) && StorageHelper.itemTargetsMet(mod, COLLECT_NETHERITE_SMELTING_MATERIAL);

                // Start with wood gear
                if (!StorageHelper.itemTargetsMet(mod, COLLECT_WOOD_GEAR) && !diamondGearSatisfied) {
                    _gearTask = TaskCatalogue.getSquashedItemTask(COLLECT_WOOD_GEAR);
                    return _gearTask;
                }

                // Then with stone
                if (!StorageHelper.itemTargetsMet(mod, COLLECT_STONE_GEAR) && !diamondGearSatisfied) {
                    _gearTask = TaskCatalogue.getSquashedItemTask(COLLECT_STONE_GEAR);
                    return _gearTask;
                }

                // Drop wood gear

                if (StorageHelper.itemTargetsMet(mod, COLLECT_WOOD_GEAR)) {
                    setDebugState("Throwing Wooden Gear");
                    for (int i = 0; i < _throwTarget.size(); ++i) {
                        ItemTarget target = _throwTarget.get(i);
                        if (target.getTargetCount() > 0) {
                            Optional<Slot> has = mod.getItemStorage().getSlotsWithItemPlayerInventory(false, target.getMatches()).stream().findFirst();
                            if (has.isPresent()) {
                                Slot currentlyPresent = has.get();
                                if (Slot.isCursor(currentlyPresent)) {
                                    ItemStack stack = StorageHelper.getItemStackInSlot(currentlyPresent);

                                    // Update target
                                    target = new ItemTarget(target, target.getTargetCount() - stack.getCount());
                                    _throwTarget.set(i, target);
                                    Debug.logMessage("THROWING: " + has.get());
                                    _throwTask = new ThrowCursorTask();
                                    return _throwTask;
                                } else {
                                    return new ClickSlotTask(currentlyPresent);
                                }
                            }
                        }
                    }
                    if (targetPos.isInRange(mod.getEntityTracker().itemDropped(COLLECT_WOOD_GEAR), 4)) {
                        mod.log("Finished dropping items.");
                        stop(mod);
                        return null;
                    }
                    return new RunAwayFromPositionTask(6, WorldHelper.toBlockPos(targetPos));
                }

                // Grab bed
                if (!mod.getItemStorage().hasItem(ItemHelper.BED)) {
                    return getBedTask(mod);
                }

                // Then get food
                if (StorageHelper.calculateInventoryFoodScore(mod) < _config.minFoodUnits) {
                    _foodTask = new CollectFoodTask(_config.foodUnits);
                    return _foodTask;
                }

                // Then get iron
                if (!StorageHelper.itemTargetsMet(mod, IRON_GEAR_MIN) && !diamondGearSatisfied) {
                    _gearTask = TaskCatalogue.getSquashedItemTask(Stream.concat(stream(COLLECT_IRON_ARMOR).filter(item -> !mod.getItemStorage().hasItem(item) && !StorageHelper.isArmorEquipped(mod, item)).map(item -> new ItemTarget(item, 1)), stream(COLLECT_IRON_GEAR)).toArray(ItemTarget[]::new));
                    return _gearTask;
                }

                // Drop stone gear

                if (StorageHelper.itemTargetsMet(mod, COLLECT_STONE_GEAR)) {
                    setDebugState("Throwing Stone Gear");
                    for (int i = 0; i < _throwTarget.size(); ++i) {
                        ItemTarget target = _throwTarget.get(i);
                        if (target.getTargetCount() > 0) {
                            Optional<Slot> has = mod.getItemStorage().getSlotsWithItemPlayerInventory(false, target.getMatches()).stream().findFirst();
                            if (has.isPresent()) {
                                Slot currentlyPresent = has.get();
                                if (Slot.isCursor(currentlyPresent)) {
                                    ItemStack stack = StorageHelper.getItemStackInSlot(currentlyPresent);

                                    // Update target
                                    target = new ItemTarget(target, target.getTargetCount() - stack.getCount());
                                    _throwTarget.set(i, target);
                                    Debug.logMessage("THROWING: " + has.get());
                                    _throwTask = new ThrowCursorTask();
                                    return _throwTask;
                                } else {
                                    return new ClickSlotTask(currentlyPresent);
                                }
                            }
                        }
                    }
                    if (targetPos.isInRange(mod.getEntityTracker().itemDropped(COLLECT_STONE_GEAR), 4)) {
                        mod.log("Finished dropping items.");
                        stop(mod);
                        return null;
                    }
                    return new RunAwayFromPositionTask(6, WorldHelper.toBlockPos(targetPos));
                }

                // Then get sheild
                if (!StorageHelper.itemTargetsMet(mod, COLLECT_SHIELD) && !diamondGearSatisfied) {
                    _gearTask = TaskCatalogue.getSquashedItemTask(COLLECT_SHIELD);
                    return _gearTask;
                }

                // If we happen to find beds...
                if (needsBeds(mod) && anyBedsFound(mod)) {
                    setDebugState("A bed was found, grabbing that first.");
                    return getBedTask(mod);
                }

                // Then get diamond
                if (!StorageHelper.itemTargetsMet(mod, COLLECT_DIAMOND_GEAR) && !StorageHelper.itemTargetsMet(mod, COLLECT_NETHERITE_GEAR)) {
                    _gearTask = TaskCatalogue.getSquashedItemTask(Stream.concat(stream(COLLECT_DIAMOND_ARMOR).filter(item -> !mod.getItemStorage().hasItem(item) && !StorageHelper.isArmorEquipped(mod, item)).map(item -> new ItemTarget(item, 1)), stream(COLLECT_DIAMOND_GEAR)).toArray(ItemTarget[]::new));
                    return _gearTask;
                }

                // Drop iron gear

                if (StorageHelper.itemTargetsMet(mod, COLLECT_IRON_GEAR) && StorageHelper.hasCataloguedItem(mod, String.valueOf(COLLECT_IRON_ARMOR))) {
                    setDebugState("Throwing Iron Gear");
                    for (int i = 0; i < _throwTarget.size(); ++i) {
                        ItemTarget target = _throwTarget.get(i);
                        if (target.getTargetCount() > 0) {
                            Optional<Slot> has = mod.getItemStorage().getSlotsWithItemPlayerInventory(false, target.getMatches()).stream().findFirst();
                            if (has.isPresent()) {
                                Slot currentlyPresent = has.get();
                                if (Slot.isCursor(currentlyPresent)) {
                                    ItemStack stack = StorageHelper.getItemStackInSlot(currentlyPresent);

                                    // Update target
                                    target = new ItemTarget(target, target.getTargetCount() - stack.getCount());
                                    _throwTarget.set(i, target);
                                    Debug.logMessage("THROWING: " + has.get());
                                    _throwTask = new ThrowCursorTask();
                                    return _throwTask;
                                } else {
                                    return new ClickSlotTask(currentlyPresent);
                                }
                            }
                        }
                    }
                    if (targetPos.isInRange(mod.getEntityTracker().itemDropped(COLLECT_IRON_GEAR), 4) && (mod.getEntityTracker().itemDropped(COLLECT_IRON_ARMOR))) {
                        mod.log("Finished dropping items.");
                        stop(mod);
                        return null;
                    }
                    return new RunAwayFromPositionTask(6, WorldHelper.toBlockPos(targetPos));
                }

                // Then get material to craft netherite
                if (!StorageHelper.itemTargetsMet(mod, COLLECT_NETHERITE_SMELTING_MATERIAL) && !diamondGearSatisfied) {
                    _gearTask = TaskCatalogue.getSquashedItemTask(COLLECT_NETHERITE_SMELTING_MATERIAL);
                    return _gearTask;
                }

                // Then go to the nether.
                if (diamondGearSatisfied) {
                    setDebugState("Going to Nether");
                    return _goToNetherTask;
                }
            }

            case NETHER -> {

                // Then get netherite ingots
                if (!StorageHelper.itemTargetsMet(mod, COLLECT_NETHERITE_INGOT) && StorageHelper.isArmorEquipped(mod, COLLECT_DIAMOND_ARMOR)) {
                    _gearTask = TaskCatalogue.getSquashedItemTask(COLLECT_NETHERITE_INGOT);
                    return _gearTask;
                }

                for (Item netherite : COLLECT_NETHERITE_ARMOR) {
                    if (mod.getItemStorage().hasItem(netherite) && !StorageHelper.isArmorEquipped(mod, netherite)) {
                        return new EquipArmorTask(COLLECT_NETHERITE_ARMOR);
                    }
                }

                boolean netheriteGearSatisfied = StorageHelper.itemTargetsMet(mod, COLLECT_NETHERITE_GEAR_MIN) && StorageHelper.isArmorEquippedAll(mod, COLLECT_NETHERITE_ARMOR);

                if (!StorageHelper.itemTargetsMet(mod, COLLECT_NETHERITE_GEAR) && !netheriteGearSatisfied) {
                    _gearTask = TaskCatalogue.getSquashedItemTask(Stream.concat(stream(COLLECT_NETHERITE_ARMOR).filter(item -> !mod.getItemStorage().hasItem(item) && !StorageHelper.isArmorEquipped(mod, item)).map(item -> new ItemTarget(item, 1)), stream(COLLECT_NETHERITE_GEAR)).toArray(ItemTarget[]::new));
                    return _gearTask;
                }
                if (needsBlazeRods) {
                    setDebugState("Getting Blaze Rods");
                    return getBlazeRodsTask(mod, blazeRodTarget);
                }
                if (needsEnderPearls) {
                    setDebugState("Getting Ender Pearls");
                    return getEnderPearlTask(mod, enderPearlTarget);
                }
            }
            case END -> throw new UnsupportedOperationException("You're in the end. Don't collect eyes here.");
        }
        return null;
    }

    private Task setSpawnNearPortalTask(AltoClef mod) {
        if (_setBedSpawnTask.isSpawnSet()) {
            _bedSpawnLocation = _setBedSpawnTask.getBedSleptPos();
        } else {
            _bedSpawnLocation = null;
        }
        if (shouldForce(mod, _setBedSpawnTask)) {
            // Set spawnpoint and set our bed spawn when it happens.
            setDebugState("Setting spawnpoint now.");
            return _setBedSpawnTask;
        }
        // Get close to portal. If we're close enough, set our bed spawn somewhere nearby.
        if (WorldHelper.inRangeXZ(mod.getPlayer(), WorldHelper.toVec3d(_endPortalCenterLocation), END_PORTAL_BED_SPAWN_RANGE)) {
            return _setBedSpawnTask;
        } else {
            setDebugState("Approaching portal (to set spawnpoint)");
            return new GetToXZTask(_endPortalCenterLocation.getX(), _endPortalCenterLocation.getZ());
        }
    }

    private Task getBlazeRodsTask(AltoClef mod, int count) {
        if (mod.getEntityTracker().itemDropped(BLAZE_POWDER)) {
            return new PickupDroppedItemTask(BLAZE_POWDER, 1);
        }
        return new CollectBlazeRodsTask(count);
    }
    private Task getEnderPearlTask(AltoClef mod, int count) {
        if (_config.barterPearlsInsteadOfEndermanHunt) {
            // Equip golden boots before trading...
            if (!StorageHelper.isArmorEquipped(mod, GOLDEN_BOOTS)) {
                return new EquipArmorTask(GOLDEN_BOOTS);
            }
            int goldBuffer = 32;
            return new TradeWithPiglinsTask(32, ENDER_PEARL, count);
        } else {
            if (mod.getEntityTracker().entityFound(EndermanEntity.class) || mod.getEntityTracker().itemDropped(ENDER_PEARL)) {
                return new KillAndLootTask(EndermanEntity.class, new ItemTarget(ENDER_PEARL, count));
            }
            // Search for warped forests this way...
            return new SearchChunkForBlockTask(Blocks.WARPED_NYLIUM);
        }
    }

    private int getTargetBeds(AltoClef mod) {
        boolean needsToSetSpawn = _config.placeSpawnNearEndPortal &&
                (
                        !spawnSetNearPortal(mod, _endPortalCenterLocation)
                                && !shouldForce(mod, _setBedSpawnTask)
                );
        int bedsInEnd = 0;
        for (Item bed : ItemHelper.BED) {
            bedsInEnd += _cachedEndItemDrops.getOrDefault(bed, 0);
        }

        return _config.requiredBeds + (needsToSetSpawn ? 1 : 0) - bedsInEnd;
    }
    private boolean needsBeds(AltoClef mod) {
        int inEnd = 0;
        for (Item item : ItemHelper.BED) {
            inEnd += _cachedEndItemDrops.getOrDefault(item, 0);
        }
        return (mod.getItemStorage().getItemCount(ItemHelper.BED) + inEnd) < getTargetBeds(mod);
    }
    private Task getBedTask(AltoClef mod) {
        int targetBeds = getTargetBeds(mod);
        // Collect beds. If we want to set our spawn, collect 1 more.
        setDebugState("Collecting " + targetBeds + " beds");
        if (!mod.getItemStorage().hasItem(SHEARS) && !anyBedsFound(mod)) {
            return TaskCatalogue.getItemTask(SHEARS, 1);
        }
        return TaskCatalogue.getItemTask("bed", targetBeds);
    }
    private boolean anyBedsFound(AltoClef mod) {
        return mod.getBlockTracker().anyFound(ItemHelper.itemsToBlocks(ItemHelper.BED));
    }

    private BlockPos doSimpleSearchForEndPortal(AltoClef mod) {
        List<BlockPos> frames = mod.getBlockTracker().getKnownLocations(Blocks.END_PORTAL_FRAME);
        if (frames.size() >= END_PORTAL_FRAME_COUNT) {
            // Get the center of the frames.
            Vec3d average = frames.stream()
                    .reduce(Vec3d.ZERO, (accum, bpos) -> accum.add(bpos.getX() + 0.5, bpos.getY() + 0.5, bpos.getZ() + 0.5), Vec3d::add)
                    .multiply(1.0f / frames.size());
            return new BlockPos(average.x, average.y, average.z);
        }
        return null;
    }

    private static boolean isEndPortalFrameFilled(AltoClef mod, BlockPos pos) {
        if (!mod.getChunkTracker().isChunkLoaded(pos))
            return false;
        BlockState state = mod.getWorld().getBlockState(pos);
        if (state.getBlock() != Blocks.END_PORTAL_FRAME) {
            Debug.logWarning("BLOCK POS " + pos + " DOES NOT CONTAIN END PORTAL FRAME! This is probably due to a bug/incorrect assumption.");
            return false;
        }
        return state.get(EndPortalFrameBlock.EYE);
    }

    @Override
    public boolean isFinished(AltoClef mod) {
        return MinecraftClient.getInstance().currentScreen instanceof CreditsScreen;
    }

    // Just a helpful utility to reduce reuse recycle.
    private static boolean shouldForce(AltoClef mod, Task task) {
        return task != null && task.isActive() && !task.isFinished(mod);
    }
    private static ItemTarget[] toItemTargets(Item ...items) {
        return stream(items).map(item -> new ItemTarget(item, 1)).toArray(ItemTarget[]::new);
    }
    private static ItemTarget[] toItemTargets(Item item, int count) {
        return new ItemTarget[] {new ItemTarget(item, count)};
    }
    private static ItemTarget[] combine(ItemTarget[] ...targets) {
        List<ItemTarget> result = new ArrayList<>();
        for (ItemTarget[] ts : targets) {
            result.addAll(asList(ts));
        }
        return result.toArray(ItemTarget[]::new);
    }
}