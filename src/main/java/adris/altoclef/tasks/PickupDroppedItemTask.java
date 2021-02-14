package adris.altoclef.tasks;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.progresscheck.IProgressChecker;
import adris.altoclef.util.progresscheck.LinearProgressChecker;
import adris.altoclef.util.baritone.BaritoneHelper;
import adris.altoclef.util.baritone.GoalGetToPosition;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.csharpisbetter.Util;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalTwoBlocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class PickupDroppedItemTask extends Task {

    private final List<ItemTarget> _itemTargets;

    private AltoClef _mod;

    private final TargetPredicate _targetPredicate = new TargetPredicate();

    private Vec3d _itemGoal;

    private IProgressChecker<Double> _progressChecker;

    public PickupDroppedItemTask(List<ItemTarget> itemTargets) {
        _itemTargets = itemTargets;
        _itemGoal = null;
        _progressChecker = new LinearProgressChecker(7.0, 0.1);
    }

    public PickupDroppedItemTask(Item item, int targetCount) {
        this(Collections.singletonList(new ItemTarget(item, targetCount)));
    }

    @Override
    protected void onStart(AltoClef mod) {
        _mod = mod;

        // Config
        mod.getConfigState().push();
        mod.getConfigState().setFollowDistance(0);

        // Reset baritone process
        mod.getClientBaritone().getCustomGoalProcess().onLostControl();

        _progressChecker.reset();
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    protected Task onTick(AltoClef mod) {

        Vec3d playerPos = mod.getPlayer().getPos();

        boolean isPathing = _itemGoal != null;
        if (isPathing) {
            setDebugState("Going to " + _itemGoal);
            // Update blacklist if we're not making good progress.
            double progress = -1 * BaritoneHelper.calculateGenericHeuristic(playerPos, _itemGoal);
            _progressChecker.setProgress(progress);
            if (_progressChecker.failed()) {
                mod.getEntityTracker().blacklist(_itemGoal);
                Debug.logMessage("Failed to get to " + _itemGoal + ", adding to blacklist.");
                // Cancel so we re-path
                mod.getClientBaritone().getCustomGoalProcess().onLostControl();
                _itemGoal = null;
                return null;
            }
        }

        ItemEntity closest = mod.getEntityTracker().getClosestItemDrop(playerPos, Util.toArray(ItemTarget.class, _itemTargets));

        if (!taskAssert(mod, closest != null, "Failed to find any items to pick up. Should have checked this condition earlier")) {
            return null;
        }

//        setDebugState("FOUND: " + (closest.getStack() != null? closest.getStack().getItem().getTranslationKey() : " (nothing)"));

        // These two lines must be paired in this order. path must be called once.
        // Setting goal makes the goal process active, but not pathing! This is undesirable.
        Vec3d goal = closest.getPos();
        boolean wasActive = mod.getClientBaritone().getCustomGoalProcess().isActive();
        if (!wasActive || !isPathing || _itemGoal.squaredDistanceTo(goal) > 1) {
            Debug.logInternal("Pickup: Restarting goal process");
            mod.getClientBaritone().getCustomGoalProcess().setGoalAndPath(new GoalTwoBlocks(closest.getBlockPos()));//new GoalGetToPosition(goal));//new GoalGetToPosition(goal));
            _itemGoal = goal;
            // If this check isn't here, the check will NEVER fail if the goal process fails frequently.
            if (wasActive) {
                _progressChecker.reset();
            }
        }

        return null;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {
        mod.getConfigState().pop();
        // Stop baritone IF the other task isn't an item task.
        if (!(interruptTask instanceof PickupDroppedItemTask)) {
            mod.getClientBaritone().getCustomGoalProcess().onLostControl();
            //_mod.getClientBaritone().getFollowProcess().cancel();
        }
    }

    @Override
    protected boolean isEqual(Task other) {
        // Same target items
        if (other instanceof PickupDroppedItemTask) {
            PickupDroppedItemTask t = (PickupDroppedItemTask) other;
            if (t._itemTargets.size() != _itemTargets.size()) return false;
            for (int i = 0; i < _itemTargets.size(); ++i) {
                if (!_itemTargets.get(i).equals(t._itemTargets.get(i))) return false;
            }
            return true;
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        StringBuilder result = new StringBuilder();
        result.append("Pickup Dropped Items: [");
        int c = 0;
        for (ItemTarget target : _itemTargets) {
            result.append(target.toString());
            if (++c != _itemTargets.size()) {
                result.append(", ");
            }
        }
        result.append("]");
        return result.toString();
    }

    class TargetPredicate implements Predicate<Entity> {

        @Override
        public boolean test(Entity entity) {
            if (entity instanceof ItemEntity) {
                ItemEntity iEntity = (ItemEntity) entity;
                for (ItemTarget target : _itemTargets) {
                    // If we already have this item, ignore it
                    if (_mod.getInventoryTracker().targetMet(target)) continue;

                    // Match for item
                    if (target.matches(iEntity.getStack().getItem())) {
                        return true;
                    }
                }
            }
            return false;
        }
    }


}
