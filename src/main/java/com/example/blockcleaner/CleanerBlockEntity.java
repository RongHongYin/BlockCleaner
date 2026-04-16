package com.example.blockcleaner;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
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
    public static final int SPEED_FIXED = 0;
    public static final int SPEED_VANILLA = 1;

    private int mode = MODE_SURVIVAL;
    private int direction = DIR_DOWN;
    private int rangeChunks = 1;
    private int targetY = 0;
    private int speedPerSecond = 30;
    private int speedMode = SPEED_FIXED;
    private boolean keepOneDurability = true;
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
                default -> {
                }
            }
        }

        @Override
        public int size() {
            return 8;
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
        view.putInt("rangeChunks", rangeChunks);
        view.putInt("targetY", targetY);
        view.putInt("speedPerSecond", speedPerSecond);
        view.putInt("speedMode", speedMode);
        view.putBoolean("keepOneDurability", keepOneDurability);
        view.putBoolean("active", active);
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);
        mode = view.getInt("mode", MODE_SURVIVAL);
        direction = view.getInt("direction", DIR_DOWN);
        rangeChunks = Math.max(1, view.getInt("rangeChunks", 1));
        targetY = view.getInt("targetY", pos.getY() - 1);
        speedPerSecond = Math.max(1, view.getInt("speedPerSecond", 30));
        speedMode = view.getInt("speedMode", SPEED_FIXED);
        keepOneDurability = view.getBoolean("keepOneDurability", true);
        active = view.getBoolean("active", false);
    }

    public void serverTick() {
        if (world == null || world.isClient() || !active) {
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
                return;
            }

            if (processBlock(target)) {
                startupFastScanTicks = 0;
                if (speedMode == SPEED_VANILLA) {
                    done = blocksPerTick;
                }
                done++;
            }
        }
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
            state = world.getBlockState(targetPos);
            if (state.isAir() || shouldSkipBlock(state, targetPos) || state.getHardness(world, targetPos) < 0) {
                return false;
            }
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
        for (Inventory inv : inventories) {
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (stack.isEmpty() || !stack.isDamageable()) {
                    continue;
                }
                if (!matchesHint(stack, hint)) {
                    continue;
                }
                if (keepOneDurability && stack.getDamage() >= stack.getMaxDamage() - 1) {
                    continue;
                }

                stack.setDamage(stack.getDamage() + 1);
                if (stack.getDamage() >= stack.getMaxDamage()) {
                    inv.setStack(i, ItemStack.EMPTY);
                } else {
                    inv.setStack(i, stack);
                }
                inv.markDirty();
                return stack;
            }
        }
        return ItemStack.EMPTY;
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
        if (world == null) {
            return null;
        }
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        int half = (rangeChunks - 1) / 2;
        int minX = (chunkX - half) << 4;
        int maxX = ((chunkX + half + 1) << 4) - 1;
        int minZ = (chunkZ - half) << 4;
        int maxZ = ((chunkZ + half + 1) << 4) - 1;

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

    public void applyAction(int action) {
        boolean wasActive = active;
        switch (action) {
            case 1 -> direction = DIR_UP;
            case 2 -> direction = DIR_DOWN;
            case 3 -> rangeChunks = Math.min(99, rangeChunks + 2);
            case 4 -> rangeChunks = Math.max(1, rangeChunks - 2);
            case 5 -> speedPerSecond = Math.min(10000, speedPerSecond + 10);
            case 6 -> speedPerSecond = Math.max(10, speedPerSecond - 10);
            case 7 -> active = !active;
            case 8 -> mode = (mode == MODE_CREATIVE) ? MODE_SURVIVAL : MODE_CREATIVE;
            case 9 -> speedMode = (speedMode == SPEED_FIXED) ? SPEED_VANILLA : SPEED_FIXED;
            case 10 -> keepOneDurability = !keepOneDurability;
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
        if (clamped % 2 == 0) {
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

    public enum ToolHint {
        PICKAXE, AXE, SHOVEL, ANY
    }
}
