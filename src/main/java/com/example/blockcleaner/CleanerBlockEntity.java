package com.example.blockcleaner;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CleanerBlockEntity extends BlockEntity implements NamedScreenHandlerFactory {
    public static final int MODE_CREATIVE = 0;
    public static final int MODE_SURVIVAL = 1;
    public static final int DIR_UP = 0;
    public static final int DIR_DOWN = 1;
    public static final int RANGE_MODE_CENTER = 0;
    public static final int RANGE_MODE_TOP_LEFT = 1;
    public static final int SPEED_FIXED = 0;
    public static final int SPEED_VANILLA = 1;
    public static final int ACTION_ADD_BLACKLIST_BASE = 400000;
    public static final int ACTION_REMOVE_BLACKLIST_BASE = 500000;
    public static final int ACTION_SET_BUILD_FACE_BLOCK_BASE = 800000;
    public static final int BUILD_FACE_UP = 0;
    public static final int BUILD_FACE_DOWN = 1;
    public static final int BUILD_FACE_FRONT = 2;
    public static final int BUILD_FACE_BACK = 3;
    public static final int BUILD_FACE_LEFT = 4;
    public static final int BUILD_FACE_RIGHT = 5;
    public static final int BUILD_LAYER_INNER = 0;
    public static final int BUILD_LAYER_OUTER = 1;
    private static final int BUILD_FACE_COUNT = 6;

    private int mode = MODE_SURVIVAL;
    private int direction = DIR_DOWN;
    private int rangeMode = RANGE_MODE_CENTER;
    private int rangeChunks = 1;
    private int targetY = 0;
    private int speedPerSecond = 30;
    private int speedMode = SPEED_FIXED;
    private boolean keepOneDurability = true;
    private boolean buildWithClear = false;
    private boolean buildActive = false;
    private int buildLayerMode = BUILD_LAYER_INNER;
    private final boolean[] buildFaceEnabled = new boolean[]{true, true, true, true, true, true};
    private final boolean[] buildFaceUseSpecific = new boolean[BUILD_FACE_COUNT];
    private final int[] buildFaceSpecificRawIds = new int[]{-1, -1, -1, -1, -1, -1};
    private final Set<Identifier> pendingLegacyBlacklist = new HashSet<>();
    private boolean active = false;
    private int breakCooldownTicks = 0;
    private int startupFastScanTicks = 0;

    private int cursorX;
    private int cursorY;
    private int cursorZ;
    private boolean cursorInitialized = false;

    private final PropertyDelegate properties = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> mode;
                case 1 -> direction;
                case 2 -> rangeChunks;
                case 3 -> targetY;
                case 4 -> speedPerSecond;
                case 5 -> active ? 1 : 0;
                case 6 -> speedMode;
                case 7 -> keepOneDurability ? 1 : 0;
                case 8 -> rangeMode;
                case 9 -> buildWithClear ? 1 : 0;
                case 10 -> buildActive ? 1 : 0;
                case 11, 12, 13, 14, 15, 16 -> buildFaceEnabled[index - 11] ? 1 : 0;
                case 17, 18, 19, 20, 21, 22 -> buildFaceUseSpecific[index - 17] ? 1 : 0;
                case 23, 24, 25, 26, 27, 28 -> buildFaceSpecificRawIds[index - 23];
                case 29 -> buildLayerMode;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> mode = value;
                case 1 -> direction = value;
                case 2 -> rangeChunks = value;
                case 3 -> targetY = value;
                case 4 -> speedPerSecond = value;
                case 5 -> active = value == 1;
                case 6 -> speedMode = value;
                case 7 -> keepOneDurability = value == 1;
                case 8 -> rangeMode = value == RANGE_MODE_TOP_LEFT ? RANGE_MODE_TOP_LEFT : RANGE_MODE_CENTER;
                case 9 -> buildWithClear = value == 1;
                case 10 -> buildActive = value == 1;
                case 11, 12, 13, 14, 15, 16 -> buildFaceEnabled[index - 11] = value == 1;
                case 17, 18, 19, 20, 21, 22 -> buildFaceUseSpecific[index - 17] = value == 1;
                case 23, 24, 25, 26, 27, 28 -> buildFaceSpecificRawIds[index - 23] = value >= 0 ? value : -1;
                case 29 -> buildLayerMode = value == BUILD_LAYER_OUTER ? BUILD_LAYER_OUTER : BUILD_LAYER_INNER;
                default -> {
                }
            }
        }

        @Override
        public int size() {
            return 30;
        }
    };

    public CleanerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLEANER_BLOCK_ENTITY, pos, state);
        this.targetY = pos.getY() - 1;
    }

    @Override
    public Text getDisplayName() {
        return Text.literal("区块清除器");
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new CleanerScreenHandler(syncId, playerInventory, this, properties);
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);
        view.putInt("mode", mode);
        view.putInt("direction", direction);
        view.putInt("rangeMode", rangeMode);
        view.putInt("rangeChunks", rangeChunks);
        view.putInt("targetY", targetY);
        view.putInt("speedPerSecond", speedPerSecond);
        view.putInt("speedMode", speedMode);
        view.putBoolean("keepOneDurability", keepOneDurability);
        view.putBoolean("buildWithClear", buildWithClear);
        view.putBoolean("buildActive", buildActive);
        view.putInt("buildLayerMode", buildLayerMode);
        for (int i = 0; i < BUILD_FACE_COUNT; i++) {
            view.putBoolean("buildFaceEnabled" + i, buildFaceEnabled[i]);
            view.putBoolean("buildFaceUseSpecific" + i, buildFaceUseSpecific[i]);
            view.putInt("buildFaceSpecificRawId" + i, buildFaceSpecificRawIds[i]);
        }
        view.putBoolean("active", active);
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);
        mode = view.getInt("mode", MODE_SURVIVAL);
        direction = view.getInt("direction", DIR_DOWN);
        rangeMode = view.getInt("rangeMode", RANGE_MODE_CENTER);
        if (rangeMode != RANGE_MODE_TOP_LEFT) {
            rangeMode = RANGE_MODE_CENTER;
        }
        rangeChunks = Math.max(1, view.getInt("rangeChunks", 1));
        targetY = view.getInt("targetY", pos.getY() - 1);
        speedPerSecond = Math.max(1, view.getInt("speedPerSecond", 30));
        speedMode = view.getInt("speedMode", SPEED_FIXED);
        keepOneDurability = view.getBoolean("keepOneDurability", true);
        buildWithClear = view.getBoolean("buildWithClear", false);
        buildActive = view.getBoolean("buildActive", false);
        buildLayerMode = view.getInt("buildLayerMode", BUILD_LAYER_INNER);
        if (buildLayerMode != BUILD_LAYER_OUTER) {
            buildLayerMode = BUILD_LAYER_INNER;
        }
        for (int i = 0; i < BUILD_FACE_COUNT; i++) {
            buildFaceEnabled[i] = view.getBoolean("buildFaceEnabled" + i, true);
            buildFaceUseSpecific[i] = view.getBoolean("buildFaceUseSpecific" + i, false);
            int rawId = view.getInt("buildFaceSpecificRawId" + i, -1);
            buildFaceSpecificRawIds[i] = rawId >= 0 ? rawId : -1;
        }
        pendingLegacyBlacklist.clear();
        pendingLegacyBlacklist.addAll(GlobalBlacklistStorage.parseSerializedIds(view.getString("dropBlacklist", "")));
        active = view.getBoolean("active", false);
    }

    public void serverTick() {
        if (world == null || world.isClient()) {
            return;
        }
        migrateLegacyBlacklistIfNeeded();
        if (!active && !buildActive) {
            return;
        }

        if (speedMode == SPEED_VANILLA) {
            if (breakCooldownTicks > 0) {
                breakCooldownTicks--;
                return;
            }
        }

        int blocksPerTick = speedMode == SPEED_FIXED ? Math.max(1, speedPerSecond / 20) : 1;
        int done = 0;
        int attempts = 0;
        // Prevent end-phase scans from monopolizing the server tick.
        int maxAttemptsPerTick = Math.max(1024, blocksPerTick * 64);
        // On startup, scan much faster so "already empty" ranges stop quickly.
        if (startupFastScanTicks > 0) {
            maxAttemptsPerTick = Math.max(maxAttemptsPerTick, 200_000);
            startupFastScanTicks--;
        }

        while (done < blocksPerTick && attempts < maxAttemptsPerTick) {
            attempts++;
            BlockPos target = nextTargetPos();
            if (target == null) {
                resetCursor();
                if (active) {
                    active = false;
                    announceStatus("清理任务完成，机器已自动停止");
                    markDirty();
                }
                if (buildActive) {
                    buildActive = false;
                    announceStatus("建造任务完成，机器已自动停止");
                    markDirty();
                }
                return;
            }

            if (active) {
                boolean cleaned = processBlock(target);
                boolean built = buildWithClear && performBuildAt(target);
                if (cleaned || built) {
                    startupFastScanTicks = 0;
                    if (speedMode == SPEED_VANILLA) {
                        done = blocksPerTick;
                    }
                    done++;
                }
            } else if (!active && buildActive && performBuildAt(target)) {
                startupFastScanTicks = 0;
                done++;
            }
        }
    }

    private boolean performBuildAt(BlockPos centerPos) {
        boolean placedAny = false;
        ScanBounds bounds = getCurrentScanBounds();
        if (bounds == null) {
            return false;
        }
        Direction front = getCachedState().get(CleanerBlock.FACING);
        // Keep left/right consistent with input/output side definition:
        // facing the machine front, left is clockwise and right is counterclockwise.
        Direction left = front.rotateYClockwise();
        Direction right = front.rotateYCounterclockwise();
        for (int face = 0; face < BUILD_FACE_COUNT; face++) {
            if (!buildFaceEnabled[face]) {
                continue;
            }
            Direction dir = switch (face) {
                case BUILD_FACE_UP -> Direction.UP;
                case BUILD_FACE_DOWN -> Direction.DOWN;
                case BUILD_FACE_FRONT -> front.getOpposite();
                case BUILD_FACE_BACK -> front;
                case BUILD_FACE_LEFT -> left;
                case BUILD_FACE_RIGHT -> right;
                default -> null;
            };
            if (dir == null) {
                continue;
            }
            if (!isOnBoundaryFace(centerPos, dir, bounds)) {
                continue;
            }
            BlockPos placePos = buildLayerMode == BUILD_LAYER_OUTER ? centerPos.offset(dir) : centerPos;
            if (placePos.equals(this.pos)) {
                continue;
            }
            if (world.getBlockEntity(placePos) != null) {
                continue;
            }
            if (buildLayerMode == BUILD_LAYER_OUTER && !prepareOuterPlacementPlacePos(placePos)) {
                continue;
            }
            BlockState currentState = world.getBlockState(placePos);
            if (!currentState.isAir() && currentState.getFluidState().isEmpty()) {
                continue;
            }
            if (placeBuildBlock(placePos, face)) {
                placedAny = true;
            }
        }
        return placedAny;
    }

    private boolean placeBuildBlock(BlockPos placePos, int face) {
        List<Inventory> inventories = collectSideInventories(true);
        if (inventories.isEmpty()) {
            return false;
        }
        for (Inventory inv : inventories) {
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
                    continue;
                }
                int rawId = Registries.ITEM.getRawId(stack.getItem());
                if (buildFaceUseSpecific[face]) {
                    int expectedRawId = buildFaceSpecificRawIds[face];
                    if (expectedRawId < 0 || rawId != expectedRawId) {
                        continue;
                    }
                }
                BlockState placeState = blockItem.getBlock().getDefaultState();
                if (placeState.isAir() || !placeState.getFluidState().isEmpty()) {
                    continue;
                }
                if (!world.setBlockState(placePos, placeState, Block.NOTIFY_ALL)) {
                    continue;
                }
                stack.decrement(1);
                inv.setStack(i, stack);
                inv.markDirty();
                return true;
            }
        }
        return false;
    }

    private boolean isOnBoundaryFace(BlockPos posToCheck, Direction faceDir, ScanBounds bounds) {
        return switch (faceDir) {
            case UP -> posToCheck.getY() == bounds.maxY();
            case DOWN -> posToCheck.getY() == bounds.minY();
            case NORTH -> posToCheck.getZ() == bounds.minZ();
            case SOUTH -> posToCheck.getZ() == bounds.maxZ();
            case WEST -> posToCheck.getX() == bounds.minX();
            case EAST -> posToCheck.getX() == bounds.maxX();
        };
    }

    private boolean prepareOuterPlacementPlacePos(BlockPos placePos) {
        BlockState state = world.getBlockState(placePos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return true;
        }
        if (shouldSkipBlock(state, placePos)) {
            return false;
        }
        return processBlock(placePos) || world.getBlockState(placePos).isAir() || !world.getBlockState(placePos).getFluidState().isEmpty();
    }

    public void cycleFaceSpecificBlock(int face) {
        if (face < 0 || face >= BUILD_FACE_COUNT) {
            return;
        }
        List<Integer> candidates = new ArrayList<>();
        for (Inventory inv : collectSideInventories(true)) {
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
                    continue;
                }
                BlockState placeState = blockItem.getBlock().getDefaultState();
                if (placeState.isAir() || !placeState.getFluidState().isEmpty()) {
                    continue;
                }
                int rawId = Registries.ITEM.getRawId(stack.getItem());
                if (rawId >= 0 && !candidates.contains(rawId)) {
                    candidates.add(rawId);
                }
            }
        }
        if (candidates.isEmpty()) {
            buildFaceSpecificRawIds[face] = -1;
            markDirty();
            return;
        }
        int current = buildFaceSpecificRawIds[face];
        int idx = candidates.indexOf(current);
        int nextIdx = (idx + 1) % candidates.size();
        buildFaceSpecificRawIds[face] = candidates.get(nextIdx);
        markDirty();
    }

    private boolean processBlock(BlockPos targetPos) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return false;
        }
        if (targetPos.equals(this.pos)) {
            return false;
        }

        BlockState state = world.getBlockState(targetPos);
        if (state.isAir() || shouldSkipBlock(state, targetPos)) {
            return false;
        }

        if (isFluidBlock(state)) {
            if (mode == MODE_CREATIVE) {
                return world.setBlockState(targetPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }
            if (!fillFluidWithInputBlock(targetPos)) {
                return false;
            }
            // Keep the placed block in place to prevent nearby fluid from immediately flowing back.
            return true;
        } else if (state.getHardness(world, targetPos) < 0) {
            return false;
        }

        if (mode == MODE_CREATIVE) {
            return world.breakBlock(targetPos, false);
        }

        ItemStack tool = findAndUseToolFor(state, serverWorld);
        if (tool.isEmpty()) {
            return false;
        }

        if (speedMode == SPEED_VANILLA) {
            breakCooldownTicks = Math.max(0, estimateBreakTicks(state, targetPos, tool));
        }

        List<ItemStack> drops = Block.getDroppedStacks(state, serverWorld, targetPos, world.getBlockEntity(targetPos), null, tool);
        boolean broken = world.breakBlock(targetPos, false);
        if (!broken) {
            return false;
        }

        for (ItemStack drop : drops) {
            if (isDropBlacklisted(drop)) {
                continue;
            }
            ItemStack remaining = insertToOutput(drop.copy());
            if (!remaining.isEmpty()) {
                ItemScatterer.spawn(world, targetPos.getX(), targetPos.getY(), targetPos.getZ(), remaining);
            }
        }
        return true;
    }

    private boolean isFluidBlock(BlockState state) {
        return state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA);
    }

    private boolean fillFluidWithInputBlock(BlockPos targetPos) {
        List<Inventory> inventories = collectSideInventories(true);
        if (inventories.isEmpty()) {
            return false;
        }

        for (Inventory inv : inventories) {
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
                    continue;
                }
                BlockState placeState = blockItem.getBlock().getDefaultState();
                if (placeState.isAir() || !placeState.getFluidState().isEmpty()) {
                    continue;
                }
                if (!world.setBlockState(targetPos, placeState, Block.NOTIFY_ALL)) {
                    continue;
                }
                stack.decrement(1);
                inv.setStack(i, stack);
                inv.markDirty();
                return true;
            }
        }
        return false;
    }

    private ItemStack findAndUseToolFor(BlockState state, ServerWorld serverWorld) {
        List<Inventory> inventories = collectSideInventories(true);
        if (inventories.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ToolHint hint = requiredTool(state);
        // Phase 1: always reuse an existing single tool first.
        for (Inventory inv : inventories) {
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (stack.isEmpty() || !stack.isDamageable() || stack.getCount() != 1) {
                    continue;
                }
                if (!matchesHint(stack, hint)) {
                    continue;
                }
                ItemStack used = useToolFromSlot(inv, i);
                if (!used.isEmpty()) {
                    return used;
                }
            }
        }

        // Phase 2: no reusable single tool found, split one from a stack and use it.
        for (Inventory inv : inventories) {
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (stack.isEmpty() || !stack.isDamageable()) {
                    continue;
                }
                if (!matchesHint(stack, hint)) {
                    continue;
                }
                if (stack.getCount() <= 1) {
                    // Single tools were already handled in phase 1.
                    continue;
                }
                EmptySlot splitTarget = splitOneToolToEmptySlot(inventories, inv, i, stack);
                if (splitTarget == null) {
                    continue;
                }
                ItemStack used = useToolFromSlot(splitTarget.inventory, splitTarget.slot);
                if (!used.isEmpty()) {
                    return used;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private EmptySlot splitOneToolToEmptySlot(List<Inventory> inventories, Inventory sourceInv, int sourceSlot, ItemStack stackedTool) {
        if (stackedTool.getCount() <= 1) {
            return new EmptySlot(sourceInv, sourceSlot);
        }

        EmptySlot target = findEmptySlotForSingleTool(inventories, sourceInv, sourceSlot);
        if (target == null) {
            return null;
        }

        ItemStack singleTool = stackedTool.copyWithCount(1);
        stackedTool.decrement(1);
        sourceInv.setStack(sourceSlot, stackedTool);
        sourceInv.markDirty();

        target.inventory.setStack(target.slot, singleTool);
        target.inventory.markDirty();
        return target;
    }

    private ItemStack useToolFromSlot(Inventory inv, int slot) {
        ItemStack tool = inv.getStack(slot);
        if (tool.isEmpty() || !tool.isDamageable()) {
            return ItemStack.EMPTY;
        }
        if (keepOneDurability && tool.getDamage() >= tool.getMaxDamage() - 1) {
            return ItemStack.EMPTY;
        }

        ItemStack usedTool = tool.copy();
        usedTool.setCount(1);
        usedTool.setDamage(tool.getDamage() + 1);

        if (usedTool.getDamage() >= usedTool.getMaxDamage()) {
            inv.setStack(slot, ItemStack.EMPTY);
        } else {
            inv.setStack(slot, usedTool);
        }
        inv.markDirty();
        return usedTool;
    }

    private EmptySlot findEmptySlotForSingleTool(List<Inventory> inventories, Inventory sourceInv, int sourceSlot) {
        for (Inventory inv : inventories) {
            for (int i = 0; i < inv.size(); i++) {
                if (inv == sourceInv && i == sourceSlot) {
                    continue;
                }
                if (inv.getStack(i).isEmpty()) {
                    return new EmptySlot(inv, i);
                }
            }
        }
        return null;
    }

    private ItemStack insertToOutput(ItemStack stack) {
        for (Inventory inv : collectSideInventories(false)) {
            stack = tryInsert(inv, stack);
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    private ItemStack tryInsert(Inventory inv, ItemStack stack) {
        for (int i = 0; i < inv.size(); i++) {
            ItemStack slot = inv.getStack(i);
            if (slot.isEmpty()) {
                inv.setStack(i, stack.copy());
                inv.markDirty();
                return ItemStack.EMPTY;
            }
            if (ItemStack.areItemsAndComponentsEqual(slot, stack) && slot.getCount() < slot.getMaxCount()) {
                int move = Math.min(stack.getCount(), slot.getMaxCount() - slot.getCount());
                slot.increment(move);
                stack.decrement(move);
                inv.markDirty();
                if (stack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }
        return stack;
    }

    private List<Inventory> collectSideInventories(boolean leftInput) {
        Direction facing = getCachedState().get(CleanerBlock.FACING);
        // Keep input/output aligned to player perspective while facing the machine front:
        // left side is input, right side is output.
        Direction side = leftInput ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
        BlockPos start = pos.offset(side);
        return collectConnectedInventories(start);
    }

    private List<Inventory> collectConnectedInventories(BlockPos start) {
        List<Inventory> result = new ArrayList<>();
        if (world == null) {
            return result;
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            if (!visited.add(p)) {
                continue;
            }
            if (!(world.getBlockEntity(p) instanceof Inventory inv)) {
                continue;
            }
            result.add(inv);
            for (Direction d : Direction.values()) {
                queue.add(p.offset(d));
            }
        }
        return result;
    }

    private ToolHint requiredTool(BlockState state) {
        if (state.isIn(BlockTags.PICKAXE_MINEABLE)) {
            return ToolHint.PICKAXE;
        }
        if (state.isIn(BlockTags.AXE_MINEABLE)) {
            return ToolHint.AXE;
        }
        if (state.isIn(BlockTags.SHOVEL_MINEABLE)) {
            return ToolHint.SHOVEL;
        }
        return ToolHint.ANY;
    }

    private boolean matchesHint(ItemStack stack, ToolHint hint) {
        return switch (hint) {
            case PICKAXE -> stack.isIn(BlockCleanerTags.PICKAXE_TOOLS);
            case AXE -> stack.isIn(BlockCleanerTags.AXE_TOOLS);
            case SHOVEL -> stack.isIn(BlockCleanerTags.SHOVEL_TOOLS);
            case ANY -> true;
        };
    }

    private int estimateBreakTicks(BlockState state, BlockPos targetPos, ItemStack tool) {
        float hardness = state.getHardness(world, targetPos);
        if (hardness <= 0) {
            return 0;
        }
        float speed = Math.max(1.0f, tool.getMiningSpeedMultiplier(state));
        float seconds = (hardness * 1.5f) / speed;
        return Math.max(1, (int) (seconds * 20));
    }

    private boolean shouldSkipBlock(BlockState state, BlockPos targetPos) {
        if (targetPos.equals(this.pos)) {
            return true;
        }
        // Mandatory safety filters in v2.
        return state.getBlock() == Blocks.BEDROCK
                || state.getBlock() == Blocks.BARRIER
                || state.getBlock() == Blocks.COMMAND_BLOCK
                || state.getBlock() == Blocks.CHAIN_COMMAND_BLOCK
                || state.getBlock() == Blocks.REPEATING_COMMAND_BLOCK
                || state.getBlock() == Blocks.STRUCTURE_BLOCK
                || state.getBlock() == Blocks.JIGSAW
                || world.getBlockEntity(targetPos) != null;
    }

    private BlockPos nextTargetPos() {
        ScanBounds bounds = getCurrentScanBounds();
        if (bounds == null) {
            return null;
        }
        int minX = bounds.minX();
        int maxX = bounds.maxX();
        int minZ = bounds.minZ();
        int maxZ = bounds.maxZ();
        int minY = bounds.minY();
        int maxY = bounds.maxY();
        int startY = bounds.startY();

        if (!cursorInitialized) {
            cursorX = minX;
            cursorZ = minZ;
            cursorY = startY;
            cursorInitialized = true;
        }

        if (cursorX > maxX || cursorZ > maxZ || cursorY < minY || cursorY > maxY) {
            resetCursor();
            return null;
        }

        BlockPos out = new BlockPos(cursorX, cursorY, cursorZ);
        advanceCursor(minX, maxX, minZ, maxZ, minY, maxY);
        return out;
    }

    private ScanBounds getCurrentScanBounds() {
        if (world == null) {
            return null;
        }
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        int minChunkX;
        int maxChunkX;
        int minChunkZ;
        int maxChunkZ;
        if (rangeMode == RANGE_MODE_TOP_LEFT) {
            minChunkX = chunkX;
            maxChunkX = chunkX + rangeChunks - 1;
            minChunkZ = chunkZ;
            maxChunkZ = chunkZ + rangeChunks - 1;
        } else {
            int half = (rangeChunks - 1) / 2;
            minChunkX = chunkX - half;
            maxChunkX = chunkX + half;
            minChunkZ = chunkZ - half;
            maxChunkZ = chunkZ + half;
        }
        int minX = minChunkX << 4;
        int maxX = ((maxChunkX + 1) << 4) - 1;
        int minZ = minChunkZ << 4;
        int maxZ = ((maxChunkZ + 1) << 4) - 1;

        int worldMinY = world.getBottomY();
        int worldMaxY = world.getTopYInclusive();
        int targetYClamped = Math.max(worldMinY, Math.min(worldMaxY, targetY));
        int startY = direction == DIR_UP
                ? Math.max(pos.getY() + 1, worldMinY)
                : Math.min(pos.getY() - 1, worldMaxY);

        int minY;
        int maxY;
        if (direction == DIR_UP) {
            minY = startY;
            maxY = Math.min(worldMaxY, targetYClamped);
            if (maxY < minY) {
                return null;
            }
        } else {
            minY = Math.max(worldMinY, targetYClamped);
            maxY = startY;
            if (minY > maxY) {
                return null;
            }
        }
        return new ScanBounds(minX, maxX, minY, maxY, minZ, maxZ, startY);
    }

    private void advanceCursor(int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
        cursorX++;
        if (cursorX <= maxX) {
            return;
        }
        cursorX = minX;
        cursorZ++;
        if (cursorZ <= maxZ) {
            return;
        }
        cursorZ = minZ;
        if (direction == DIR_UP) {
            cursorY++;
            // Keep cursor initialized and let nextTargetPos detect out-of-range and finish.
            if (cursorY > maxY) {
                cursorY = maxY + 1;
            }
        } else {
            cursorY--;
            // Keep cursor initialized and let nextTargetPos detect out-of-range and finish.
            if (cursorY < minY) {
                cursorY = minY - 1;
            }
        }
    }

    private void resetCursor() {
        cursorInitialized = false;
    }

    public void addDropBlacklistItem(Item item) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        Identifier id = Registries.ITEM.getId(item);
        if (id == null || id.equals(Identifier.of("minecraft", "air"))) {
            return;
        }
        if (GlobalBlacklistStorage.add(serverWorld.getServer(), id)) {
            markDirty();
        }
    }

    public void removeDropBlacklistItem(Item item) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        Identifier id = Registries.ITEM.getId(item);
        if (id == null) {
            return;
        }
        if (GlobalBlacklistStorage.remove(serverWorld.getServer(), id)) {
            markDirty();
        }
    }

    public void applyAction(int action) {
        boolean wasActive = active;
        switch (action) {
            case 1 -> direction = DIR_UP;
            case 2 -> direction = DIR_DOWN;
            case 3 -> rangeChunks = Math.min(99, rangeChunks + (rangeMode == RANGE_MODE_CENTER ? 2 : 1));
            case 4 -> rangeChunks = Math.max(1, rangeChunks - (rangeMode == RANGE_MODE_CENTER ? 2 : 1));
            case 5 -> speedPerSecond = Math.min(10000, speedPerSecond + 10);
            case 6 -> speedPerSecond = Math.max(10, speedPerSecond - 10);
            case 7 -> active = !active;
            case 8 -> mode = (mode == MODE_CREATIVE) ? MODE_SURVIVAL : MODE_CREATIVE;
            case 9 -> speedMode = (speedMode == SPEED_FIXED) ? SPEED_VANILLA : SPEED_FIXED;
            case 10 -> keepOneDurability = !keepOneDurability;
            case 11 -> rangeMode = (rangeMode == RANGE_MODE_CENTER) ? RANGE_MODE_TOP_LEFT : RANGE_MODE_CENTER;
            case 12 -> buildWithClear = !buildWithClear;
            case 13 -> buildActive = !buildActive;
            case 14 -> buildLayerMode = (buildLayerMode == BUILD_LAYER_INNER) ? BUILD_LAYER_OUTER : BUILD_LAYER_INNER;
            case 100, 101, 102, 103, 104, 105 -> {
                int face = action - 100;
                buildFaceEnabled[face] = !buildFaceEnabled[face];
            }
            case 106 -> {
                for (int i = 0; i < BUILD_FACE_COUNT; i++) {
                    buildFaceEnabled[i] = true;
                }
            }
            case 107 -> {
                for (int i = 0; i < BUILD_FACE_COUNT; i++) {
                    buildFaceEnabled[i] = false;
                }
            }
            case 200, 201, 202, 203, 204, 205 -> {
                int face = action - 200;
                buildFaceUseSpecific[face] = !buildFaceUseSpecific[face];
            }
            default -> {
            }
        }
        speedPerSecond = normalizeSpeed(speedPerSecond);
        rangeChunks = normalizeRange(rangeChunks);
        targetY = normalizeTargetY(targetY);
        markDirty();

        if (!wasActive && active) {
            resetCursor();
            breakCooldownTicks = 0;
            startupFastScanTicks = 20;
            announceStatus("开始执行清理任务");
        } else if (wasActive && !active) {
            startupFastScanTicks = 0;
            announceStatus("任务已手动停止");
        }
        if (!wasActive && !active && buildActive) {
            resetCursor();
            startupFastScanTicks = 20;
            announceStatus("开始执行建造任务");
        }
    }

    public void copyFaceConfigToAll(int sourceFace) {
        if (sourceFace < 0 || sourceFace >= BUILD_FACE_COUNT) {
            return;
        }
        boolean enabled = buildFaceEnabled[sourceFace];
        boolean useSpecific = buildFaceUseSpecific[sourceFace];
        int specificRawId = buildFaceSpecificRawIds[sourceFace];
        for (int i = 0; i < BUILD_FACE_COUNT; i++) {
            buildFaceEnabled[i] = enabled;
            buildFaceUseSpecific[i] = useSpecific;
            buildFaceSpecificRawIds[i] = specificRawId;
        }
        markDirty();
    }

    public void setFaceSpecificBlock(int face, Item item) {
        if (face < 0 || face >= BUILD_FACE_COUNT || item == null) {
            return;
        }
        if (!(item instanceof BlockItem blockItem)) {
            return;
        }
        BlockState placeState = blockItem.getBlock().getDefaultState();
        if (placeState.isAir() || !placeState.getFluidState().isEmpty()) {
            return;
        }
        int rawId = Registries.ITEM.getRawId(item);
        if (rawId < 0) {
            return;
        }
        buildFaceSpecificRawIds[face] = rawId;
        buildFaceUseSpecific[face] = true;
        markDirty();
    }

    private void announceStatus(String content) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        String msg = "[区块清除器] (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ") " + content;
        Text text = Text.literal(msg);
        serverWorld.getPlayers().forEach(player -> player.sendMessage(text, false));
    }

    public void setRangeChunks(int value) {
        rangeChunks = normalizeRange(value);
        markDirty();
    }

    public void setSpeedPerSecond(int value) {
        speedPerSecond = normalizeSpeed(value);
        markDirty();
    }

    public void setTargetY(int value) {
        targetY = normalizeTargetY(value);
        markDirty();
    }

    private int normalizeRange(int value) {
        int clamped = Math.max(1, Math.min(99, value));
        if (rangeMode == RANGE_MODE_CENTER && clamped % 2 == 0) {
            clamped = Math.min(99, clamped + 1);
        }
        return clamped;
    }

    private int normalizeSpeed(int value) {
        int clamped = Math.max(10, Math.min(10000, value));
        int normalized = (clamped / 10) * 10;
        return Math.max(10, normalized);
    }

    private int normalizeTargetY(int value) {
        if (world == null) {
            return value;
        }
        return Math.max(world.getBottomY(), Math.min(world.getTopYInclusive(), value));
    }

    private boolean isDropBlacklisted(ItemStack stack) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return false;
        }
        if (stack.isEmpty()) {
            return false;
        }
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        return itemId != null && GlobalBlacklistStorage.contains(serverWorld.getServer(), itemId);
    }

    public String serializeDropBlacklistForSync() {
        if (!(world instanceof ServerWorld serverWorld)) {
            return "";
        }
        return GlobalBlacklistStorage.serialize(serverWorld.getServer());
    }

    public int[] getDropBlacklistRawIds() {
        if (!(world instanceof ServerWorld serverWorld)) {
            return new int[0];
        }
        return GlobalBlacklistStorage.rawIds(serverWorld.getServer());
    }

    private void migrateLegacyBlacklistIfNeeded() {
        if (pendingLegacyBlacklist.isEmpty() || !(world instanceof ServerWorld serverWorld)) {
            return;
        }
        GlobalBlacklistStorage.addAll(serverWorld.getServer(), pendingLegacyBlacklist);
        pendingLegacyBlacklist.clear();
    }

    public enum ToolHint {
        PICKAXE, AXE, SHOVEL, ANY
    }

    private record EmptySlot(Inventory inventory, int slot) {
    }

    private record ScanBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ, int startY) {
    }
}
