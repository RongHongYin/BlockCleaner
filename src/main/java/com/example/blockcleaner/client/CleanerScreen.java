package com.example.blockcleaner.client;

import com.example.blockcleaner.CleanerBlockEntity;
import com.example.blockcleaner.CleanerScreenHandler;
import com.example.blockcleaner.ModNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CleanerScreen extends HandledScreen<CleanerScreenHandler> {
    private enum Page {
        MAIN,
        CLEAR,
        BLACKLIST
    }

    private Page page = Page.MAIN;
    private ButtonWidget directionButton;
    private ButtonWidget modeButton;
    private ButtonWidget speedModeButton;
    private ButtonWidget keepDurabilityButton;
    private ButtonWidget startStopButton;
    private ButtonWidget rangeMinusButton;
    private ButtonWidget rangePlusButton;
    private ButtonWidget speedMinusButton;
    private ButtonWidget speedPlusButton;
    private TextFieldWidget rangeInput;
    private TextFieldWidget targetYInput;
    private TextFieldWidget speedInput;
    private TextFieldWidget blacklistSearchInput;
    private final List<ButtonWidget> leftGridButtons = new ArrayList<>();
    private final List<ButtonWidget> rightGridButtons = new ArrayList<>();
    private final List<Item> allFilteredItems = new ArrayList<>();
    private final List<Item> blacklistedItemsView = new ArrayList<>();
    private int leftScrollRow = 0;
    private int rightScrollRow = 0;
    private boolean draggingLeftScrollbar = false;
    private boolean draggingRightScrollbar = false;
    private Set<Integer> syncedBlacklistRawIds = new HashSet<>();
    private boolean suppressInputCallbacks = false;
    private int clearScrollOffset = 0;
    private final List<ClickableWidget> scrollWidgets = new ArrayList<>();
    private static final int BLACKLIST_COLS = 5;
    private static final int BLACKLIST_ROWS = 4;
    private static final int BLACKLIST_CELL_SIZE = 18;

    public CleanerScreen(CleanerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 248;
        this.backgroundHeight = 236;
        this.titleX = 10;
        this.titleY = 8;
    }

    @Override
    protected void init() {
        super.init();
        this.clearChildren();
        this.directionButton = null;
        this.modeButton = null;
        this.speedModeButton = null;
        this.keepDurabilityButton = null;
        this.startStopButton = null;
        this.rangeMinusButton = null;
        this.rangePlusButton = null;
        this.speedMinusButton = null;
        this.speedPlusButton = null;
        this.rangeInput = null;
        this.targetYInput = null;
        this.speedInput = null;
        this.blacklistSearchInput = null;
        this.leftGridButtons.clear();
        this.rightGridButtons.clear();
        this.scrollWidgets.clear();
        if (page == Page.MAIN) {
            initMainPage();
        } else if (page == Page.CLEAR) {
            initClearPage();
        } else {
            initBlacklistPage();
        }
    }

    private void initMainPage() {
        clearScrollOffset = 0;
        int x = this.x + 24;
        int y = this.y + 44;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("清除"), b -> {
            page = Page.CLEAR;
            init();
        }).dimensions(x, y, 200, 24).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("建造（预留）"), b -> {
        }).dimensions(x, y + 34, 200, 24).build());
    }

    private void initClearPage() {
        int left = this.x + 14;
        int row = getScrollTop() + clearScrollOffset;
        int rightEdge = this.x + this.backgroundWidth - 14;
        int buttonW = 92;
        int buttonH = 20;
        int smallW = 30;
        int gap = 6;

        int mainButtonX = rightEdge - buttonW;
        int plusX = rightEdge - smallW;
        int minusX = plusX - gap - smallW;
        int inputW = 58;
        int inputX = minusX - gap - inputW;

        // 1) 模式
        this.modeButton = addScrollable(this.addDrawableChild(ButtonWidget.builder(
                Text.literal(handler.getMode() == CleanerBlockEntity.MODE_CREATIVE ? "创造" : "生存"),
                b -> sendAction(8)).dimensions(mainButtonX, row, buttonW, buttonH).build()));

        // 2) 保留1耐久
        this.keepDurabilityButton = addScrollable(this.addDrawableChild(ButtonWidget.builder(
                Text.literal(handler.shouldKeepOneDurability() ? "是" : "否"),
                b -> sendAction(10)).dimensions(mainButtonX, row + 28, buttonW, buttonH).build()));

        // 3) 方向
        this.directionButton = addScrollable(this.addDrawableChild(ButtonWidget.builder(
                Text.literal(handler.getDirection() == CleanerBlockEntity.DIR_UP ? "↑" : "↓"),
                b -> sendAction(handler.getDirection() == CleanerBlockEntity.DIR_UP ? 2 : 1))
                .dimensions(mainButtonX, row + 56, buttonW, buttonH).build()));

        // 4) 目标Y轴（输入）
        this.targetYInput = addScrollable(this.addDrawableChild(new TextFieldWidget(this.textRenderer, inputX, row + 84, inputW, buttonH, Text.literal("目标Y"))));
        this.targetYInput.setMaxLength(6);
        this.targetYInput.setText(Integer.toString(handler.getTargetY()));
        this.targetYInput.setChangedListener(this::onTargetYChanged);

        // 5) 范围（输入 + -）
        this.rangeInput = addScrollable(this.addDrawableChild(new TextFieldWidget(this.textRenderer, inputX, row + 112, inputW, buttonH, Text.literal("范围"))));
        this.rangeInput.setMaxLength(3);
        this.rangeInput.setText(Integer.toString(handler.getRangeChunks()));
        this.rangeInput.setChangedListener(this::onRangeChanged);

        this.rangeMinusButton = addScrollable(this.addDrawableChild(ButtonWidget.builder(Text.literal("-"),
                b -> sendAction(4)).dimensions(minusX, row + 112, smallW, buttonH).build()));
        this.rangePlusButton = addScrollable(this.addDrawableChild(ButtonWidget.builder(Text.literal("+"),
                b -> sendAction(3)).dimensions(plusX, row + 112, smallW, buttonH).build()));

        // 6) 速度模式
        this.speedModeButton = addScrollable(this.addDrawableChild(ButtonWidget.builder(
                Text.literal(handler.getSpeedMode() == CleanerBlockEntity.SPEED_FIXED ? "固定" : "原版"),
                b -> sendAction(9)).dimensions(mainButtonX, row + 140, buttonW, buttonH).build()));

        // 7) 速度（输入 + -）
        this.speedInput = addScrollable(this.addDrawableChild(new TextFieldWidget(this.textRenderer, inputX, row + 168, inputW, buttonH, Text.literal("速度"))));
        this.speedInput.setMaxLength(5);
        this.speedInput.setText(Integer.toString(handler.getSpeedPerSecond()));
        this.speedInput.setChangedListener(this::onSpeedChanged);

        this.speedMinusButton = addScrollable(this.addDrawableChild(ButtonWidget.builder(Text.literal("-"),
                b -> sendAction(6)).dimensions(minusX, row + 168, smallW, buttonH).build()));
        this.speedPlusButton = addScrollable(this.addDrawableChild(ButtonWidget.builder(Text.literal("+"),
                b -> sendAction(5)).dimensions(plusX, row + 168, smallW, buttonH).build()));

        // 启动 / 返回 同一行
        int bottomButtonW = 64;
        int bottomButtonH = 24;
        int bottomY = this.y + this.backgroundHeight - 34;
        int startX = rightEdge - (bottomButtonW * 3 + gap * 2);
        int blacklistX = startX + bottomButtonW + gap;
        int backX = blacklistX + bottomButtonW + gap;

        this.startStopButton = this.addDrawableChild(ButtonWidget.builder(Text.literal(handler.isActive() ? "停止" : "启动"),
                b -> sendAction(7)).dimensions(startX, bottomY, bottomButtonW, bottomButtonH).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("黑名单"),
                b -> {
                    page = Page.BLACKLIST;
                    init();
                }).dimensions(blacklistX, bottomY, bottomButtonW, bottomButtonH).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("返回"),
                b -> {
                    page = Page.MAIN;
                    init();
                }).dimensions(backX, bottomY, bottomButtonW, bottomButtonH).build());

        updateScrollableVisibility();
    }

    private void initBlacklistPage() {
        this.syncedBlacklistRawIds = handler.getSyncedBlacklistRawIds();
        int left = this.x + 14;
        int right = this.x + this.backgroundWidth - 14;
        int top = this.y + 44;
        int inputH = 20;

        this.blacklistSearchInput = this.addDrawableChild(
                new TextFieldWidget(this.textRenderer, left, top, right - left, inputH, Text.literal("搜索物品")));
        this.blacklistSearchInput.setMaxLength(80);
        this.blacklistSearchInput.setChangedListener(this::onBlacklistSearchChanged);

        int leftGridX = getLeftGridX();
        int rightGridX = getRightGridX();
        int gridY = getBlacklistGridY();
        for (int row = 0; row < BLACKLIST_ROWS; row++) {
            for (int col = 0; col < BLACKLIST_COLS; col++) {
                int slot = row * BLACKLIST_COLS + col;
                int x = leftGridX + col * BLACKLIST_CELL_SIZE;
                int y = gridY + row * BLACKLIST_CELL_SIZE;
                ButtonWidget cell = this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> onBlacklistGridCellClicked(slot))
                        .dimensions(x, y, BLACKLIST_CELL_SIZE, BLACKLIST_CELL_SIZE)
                        .build());
                leftGridButtons.add(cell);
            }
        }
        for (int row = 0; row < BLACKLIST_ROWS; row++) {
            for (int col = 0; col < BLACKLIST_COLS; col++) {
                int slot = row * BLACKLIST_COLS + col;
                int x = rightGridX + col * BLACKLIST_CELL_SIZE;
                int y = gridY + row * BLACKLIST_CELL_SIZE;
                ButtonWidget cell = this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> onRightBlacklistGridCellClicked(slot))
                        .dimensions(x, y, BLACKLIST_CELL_SIZE, BLACKLIST_CELL_SIZE)
                        .build());
                rightGridButtons.add(cell);
            }
        }
        rebuildBlacklistSearchResults();
        rebuildBlacklistedItemsView();

        int bottomButtonW = 104;
        int bottomButtonH = 24;
        int bottomY = this.y + this.backgroundHeight - 34;
        int gap = 8;
        int clearX = right - (bottomButtonW * 2 + gap);
        int backX = right - bottomButtonW;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("返回"),
                b -> {
                    page = Page.CLEAR;
                    init();
                }).dimensions(backX, bottomY, bottomButtonW, bottomButtonH).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("清空黑名单"),
                b -> clearBlacklistFromScreen()).dimensions(clearX, bottomY, bottomButtonW, bottomButtonH).build());
        requestBlacklistSync();
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x0 = this.x;
        int y0 = this.y;
        int x1 = this.x + this.backgroundWidth;
        int y1 = this.y + this.backgroundHeight;

        // Vanilla-like beveled container: bright top/left + dark bottom/right.
        context.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, 0xFF1F1F1F); // outer stroke
        context.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF8F8F8F); // frame base

        // First bevel ring
        context.fill(x0, y0, x1, y0 + 1, 0xFFFFFFFF); // top highlight
        context.fill(x0, y0, x0 + 1, y1, 0xFFFFFFFF); // left highlight
        context.fill(x1 - 1, y0, x1, y1, 0xFF555555); // right shadow
        context.fill(x0, y1 - 1, x1, y1, 0xFF555555); // bottom shadow

        // Inner panel + subtle second bevel, closer to vanilla inventory look
        context.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFFC6C6C6);
        context.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, 0xFFE3E3E3);
        context.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 1, 0xFFE3E3E3);
        context.fill(x1 - 2, y0 + 1, x1 - 1, y1 - 1, 0xFF6B6B6B);
        context.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, 0xFF6B6B6B);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Keep scene visible (lighter mask, closer to vanilla feel).
        context.fill(0, 0, this.width, this.height, 0x33000000);
        super.render(context, mouseX, mouseY, delta);

        if (page == Page.CLEAR) {
            int left = this.x + 14;
            int row = getScrollTop() + clearScrollOffset;
            int textColor = 0xFF202020;

            drawScrollLabel(context, Text.literal("模式"), left, row + 6, textColor);
            drawScrollLabel(context, Text.literal("保留1耐久"), left, row + 34, textColor);
            drawScrollLabel(context, Text.literal("方向"), left, row + 62, textColor);
            drawScrollLabel(context, Text.literal("目标Y"), left, row + 90, textColor);
            drawScrollLabel(context, Text.literal("范围"), left, row + 118, textColor);
            drawScrollLabel(context, Text.literal("（奇数 1-99）"), left + 24, row + 118, 0xFF565656);
            drawScrollLabel(context, Text.literal("速度模式"), left, row + 146, textColor);
            drawScrollLabel(context, Text.literal("速度"), left, row + 174, textColor);
            drawScrollLabel(context, Text.literal("（10 的倍数）"), left + 20, row + 174, 0xFF565656);
        } else if (page == Page.BLACKLIST) {
            int left = this.x + 14;
            int top = this.y + 44;
            int textColor = 0xFF202020;
            context.drawText(this.textRenderer, Text.literal("左侧全部物品  ->  右侧黑名单"), left, top - 12, textColor, false);
            context.drawText(this.textRenderer, Text.literal("已加入: " + syncedBlacklistRawIds.size()),
                    left, top + 24, 0xFF565656, false);
            drawBlacklistPanels(context, mouseX, mouseY);
        }

        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(this.textRenderer, Text.literal("区块清除器"), 10, 8, 0xFF202020, false);
        if (page == Page.MAIN) {
            context.drawText(this.textRenderer, Text.literal("选择功能"), 10, 24, 0xFF202020, false);
        } else if (page == Page.CLEAR) {
            context.drawText(this.textRenderer, Text.literal("清除配置"), 10, 24, 0xFF202020, false);
        } else {
            context.drawText(this.textRenderer, Text.literal("掉落物黑名单"), 10, 24, 0xFF202020, false);
        }
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        if (page == Page.BLACKLIST) {
            Set<Integer> latest = handler.getSyncedBlacklistRawIds();
            if (!latest.equals(syncedBlacklistRawIds)) {
                syncedBlacklistRawIds = latest;
                rebuildBlacklistedItemsView();
                updateBlacklistGridButtonState();
            }
            return;
        }
        if (page != Page.CLEAR) {
            return;
        }
        if (directionButton != null) {
            directionButton.setMessage(Text.literal(handler.getDirection() == CleanerBlockEntity.DIR_UP ? "↑" : "↓"));
        }
        if (modeButton != null) {
            modeButton.setMessage(Text.literal(handler.getMode() == CleanerBlockEntity.MODE_CREATIVE ? "创造" : "生存"));
        }
        if (speedModeButton != null) {
            speedModeButton.setMessage(Text.literal(handler.getSpeedMode() == CleanerBlockEntity.SPEED_FIXED ? "固定" : "原版"));
        }
        if (keepDurabilityButton != null) {
            keepDurabilityButton.setMessage(Text.literal(handler.shouldKeepOneDurability() ? "是" : "否"));
        }
        if (startStopButton != null) {
            startStopButton.setMessage(Text.literal(handler.isActive() ? "停止" : "启动"));
        }
        boolean speedEditable = handler.getSpeedMode() == CleanerBlockEntity.SPEED_FIXED;
        if (speedMinusButton != null) {
            speedMinusButton.active = speedEditable;
        }
        if (speedPlusButton != null) {
            speedPlusButton.active = speedEditable;
        }
        if (speedInput != null) {
            speedInput.setEditable(speedEditable);
            if (!speedEditable && speedInput.isFocused()) {
                this.setFocused(null);
            }
        }
        if (rangeInput != null && !rangeInput.isFocused()) {
            String target = Integer.toString(handler.getRangeChunks());
            if (!target.equals(rangeInput.getText())) {
                suppressInputCallbacks = true;
                rangeInput.setText(target);
                suppressInputCallbacks = false;
            }
        }
        if (targetYInput != null && !targetYInput.isFocused()) {
            String target = Integer.toString(handler.getTargetY());
            if (!target.equals(targetYInput.getText())) {
                suppressInputCallbacks = true;
                targetYInput.setText(target);
                suppressInputCallbacks = false;
            }
        }
        if (speedInput != null && !speedInput.isFocused()) {
            String target = Integer.toString(handler.getSpeedPerSecond());
            if (!target.equals(speedInput.getText())) {
                suppressInputCallbacks = true;
                speedInput.setText(target);
                suppressInputCallbacks = false;
            }
        }
        updateScrollableVisibility();
    }

    private void sendAction(int action) {
        if (this.client != null && this.client.interactionManager != null) {
            boolean startingNow = action == 7 && (startStopButton == null
                    || "启动".equals(startStopButton.getMessage().getString())
                    || !handler.isActive());
            this.client.interactionManager.clickButton(this.handler.syncId, action);
            // Optimistic local refresh for fast repeated +/- clicking.
            if (action == 3 || action == 4) {
                int range = parseIntOr(rangeInput != null ? rangeInput.getText() : "", handler.getRangeChunks());
                range = (action == 3) ? range + 2 : range - 2;
                range = Math.max(1, Math.min(99, range));
                if (range % 2 == 0) {
                    range = Math.min(99, range + 1);
                }
                if (rangeInput != null) {
                    suppressInputCallbacks = true;
                    rangeInput.setText(Integer.toString(range));
                    suppressInputCallbacks = false;
                }
            } else if (action == 5 || action == 6) {
                int speed = parseIntOr(speedInput != null ? speedInput.getText() : "", handler.getSpeedPerSecond());
                speed = (action == 5) ? speed + 10 : speed - 10;
                speed = Math.max(10, Math.min(10000, speed));
                speed = (speed / 10) * 10;
                if (speedInput != null) {
                    suppressInputCallbacks = true;
                    speedInput.setText(Integer.toString(speed));
                    suppressInputCallbacks = false;
                }
            }
            if (startingNow) {
                this.client.setScreen(null);
            }
        }
    }

    private void onRangeChanged(String text) {
        if (suppressInputCallbacks) {
            return;
        }
        if (this.client == null || this.client.interactionManager == null) {
            return;
        }
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            int value = Integer.parseInt(text);
            value = Math.max(1, Math.min(99, value));
            if (value % 2 == 0) {
                value = Math.min(99, value + 1);
            }
            this.client.interactionManager.clickButton(this.handler.syncId, 1000 + value);
        } catch (NumberFormatException ignored) {
        }
    }

    private void onSpeedChanged(String text) {
        if (suppressInputCallbacks) {
            return;
        }
        if (this.client == null || this.client.interactionManager == null) {
            return;
        }
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            int value = Integer.parseInt(text);
            value = Math.max(10, Math.min(10000, value));
            value = (value / 10) * 10;
            this.client.interactionManager.clickButton(this.handler.syncId, 2000 + value);
        } catch (NumberFormatException ignored) {
        }
    }

    private void onTargetYChanged(String text) {
        if (suppressInputCallbacks) {
            return;
        }
        if (this.client == null || this.client.interactionManager == null) {
            return;
        }
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            int value = Integer.parseInt(text);
            value = Math.max(-1024, Math.min(3072, value));
            this.client.interactionManager.clickButton(this.handler.syncId, 30000 + value + 1024);
        } catch (NumberFormatException ignored) {
        }
    }

    private int parseIntOr(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (page == Page.CLEAR && isInsideScrollArea(mouseX, mouseY) && verticalAmount != 0) {
            int oldOffset = clearScrollOffset;
            int viewportHeight = getScrollBottom() - getScrollTop();
            int contentHeight = 196;
            int minOffset = Math.min(0, viewportHeight - contentHeight);
            int step = 14;
            clearScrollOffset += verticalAmount > 0 ? step : -step;
            clearScrollOffset = Math.max(minOffset, Math.min(0, clearScrollOffset));
            if (clearScrollOffset != oldOffset) {
                init();
                return true;
            }
        }
        if (page == Page.BLACKLIST && verticalAmount != 0) {
            if (isInsideLeftListArea(mouseX, mouseY) || isInsideLeftScrollbar(mouseX, mouseY)) {
                leftScrollRow = clampScroll(leftScrollRow + (verticalAmount > 0 ? -1 : 1), getAllItemsMaxScrollRow());
                updateBlacklistGridButtonState();
                return true;
            }
            if (isInsideRightListArea(mouseX, mouseY) || isInsideRightScrollbar(mouseX, mouseY)) {
                rightScrollRow = clampScroll(rightScrollRow + (verticalAmount > 0 ? -1 : 1), getBlacklistedMaxScrollRow());
                updateBlacklistGridButtonState();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private int getScrollTop() {
        return this.y + 52;
    }

    private int getScrollBottom() {
        return this.y + this.backgroundHeight - 44;
    }

    private boolean isInsideScrollArea(double mouseX, double mouseY) {
        return mouseX >= this.x + 8
                && mouseX <= this.x + this.backgroundWidth - 8
                && mouseY >= getScrollTop()
                && mouseY <= getScrollBottom();
    }

    private <T extends ClickableWidget> T addScrollable(T widget) {
        this.scrollWidgets.add(widget);
        return widget;
    }

    private void updateScrollableVisibility() {
        if (page != Page.CLEAR) {
            return;
        }
        int top = getScrollTop();
        int bottom = getScrollBottom();
        for (ClickableWidget widget : scrollWidgets) {
            boolean visible = widget.getY() >= top && widget.getY() + widget.getHeight() <= bottom;
            widget.visible = visible;
            if (!visible) {
                widget.active = false;
                if (widget instanceof TextFieldWidget textField && textField.isFocused()) {
                    this.setFocused(null);
                }
            }
        }
    }

    private void drawScrollLabel(DrawContext context, Text text, int x, int y, int color) {
        int top = getScrollTop();
        int bottom = getScrollBottom();
        int textBottom = y + this.textRenderer.fontHeight;
        if (y >= top && textBottom <= bottom) {
            context.drawText(this.textRenderer, text, x, y, color, false);
        }
    }

    private void onBlacklistSearchChanged(String text) {
        rebuildBlacklistSearchResults();
    }

    private void rebuildBlacklistSearchResults() {
        allFilteredItems.clear();
        String keyword = blacklistSearchInput == null ? "" : blacklistSearchInput.getText().trim().toLowerCase(Locale.ROOT);
        for (Item item : Registries.ITEM) {
            int rawId = Registries.ITEM.getRawId(item);
            if (rawId <= 0) {
                continue;
            }
            String id = Registries.ITEM.getId(item).toString();
            String name = item.getName().getString().toLowerCase(Locale.ROOT);
            if (!keyword.isEmpty()
                    && !id.toLowerCase(Locale.ROOT).contains(keyword)
                    && !name.contains(keyword)) {
                continue;
            }
            allFilteredItems.add(item);
        }
        allFilteredItems.sort(Comparator.comparing(item -> Registries.ITEM.getId(item).toString()));
        leftScrollRow = clampScroll(leftScrollRow, getAllItemsMaxScrollRow());
        updateBlacklistGridButtonState();
    }

    private void rebuildBlacklistedItemsView() {
        blacklistedItemsView.clear();
        for (Integer rawId : syncedBlacklistRawIds) {
            Item item = Registries.ITEM.get(rawId);
            if (item != null) {
                blacklistedItemsView.add(item);
            }
        }
        blacklistedItemsView.sort(Comparator.comparing(item -> Registries.ITEM.getId(item).toString()));
        rightScrollRow = clampScroll(rightScrollRow, getBlacklistedMaxScrollRow());
    }

    private void clearBlacklistFromScreen() {
        if (this.client == null || this.client.interactionManager == null) {
            return;
        }
        for (Integer rawId : new ArrayList<>(syncedBlacklistRawIds)) {
            this.client.interactionManager.clickButton(this.handler.syncId,
                    CleanerBlockEntity.ACTION_REMOVE_BLACKLIST_BASE + rawId);
        }
        syncedBlacklistRawIds.clear();
        handler.setSyncedBlacklistRawIds(syncedBlacklistRawIds);
        rebuildBlacklistedItemsView();
        updateBlacklistGridButtonState();
    }

    private boolean isInsideRect(double mouseX, double mouseY, int left, int top, int width, int height) {
        int right = left + width;
        int bottom = top + height;
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }

    private int getLeftGridX() {
        return this.x + 14;
    }

    private int getRightGridX() {
        return this.x + this.backgroundWidth - 14 - BLACKLIST_COLS * BLACKLIST_CELL_SIZE - 6;
    }

    private int getBlacklistGridY() {
        return this.y + 84;
    }

    private int getGridPixelWidth() {
        return BLACKLIST_COLS * BLACKLIST_CELL_SIZE;
    }

    private int getGridPixelHeight() {
        return BLACKLIST_ROWS * BLACKLIST_CELL_SIZE;
    }

    private int getAllItemsMaxScrollRow() {
        int totalRows = (allFilteredItems.size() + BLACKLIST_COLS - 1) / BLACKLIST_COLS;
        return Math.max(0, totalRows - BLACKLIST_ROWS);
    }

    private int getBlacklistedMaxScrollRow() {
        int totalRows = (blacklistedItemsView.size() + BLACKLIST_COLS - 1) / BLACKLIST_COLS;
        return Math.max(0, totalRows - BLACKLIST_ROWS);
    }

    private int clampScroll(int value, int max) {
        return Math.max(0, Math.min(max, value));
    }

    private boolean isInsideLeftListArea(double mouseX, double mouseY) {
        return isInsideRect(mouseX, mouseY, getLeftGridX(), getBlacklistGridY(), getGridPixelWidth(), getGridPixelHeight());
    }

    private boolean isInsideRightListArea(double mouseX, double mouseY) {
        return isInsideRect(mouseX, mouseY, getRightGridX(), getBlacklistGridY(), getGridPixelWidth(), getGridPixelHeight());
    }

    private int getLeftScrollbarX() {
        return getLeftGridX() + getGridPixelWidth() + 2;
    }

    private int getRightScrollbarX() {
        return getRightGridX() + getGridPixelWidth() + 2;
    }

    private boolean isInsideLeftScrollbar(double mouseX, double mouseY) {
        return isInsideRect(mouseX, mouseY, getLeftScrollbarX(), getBlacklistGridY(), 6, getGridPixelHeight());
    }

    private boolean isInsideRightScrollbar(double mouseX, double mouseY) {
        return isInsideRect(mouseX, mouseY, getRightScrollbarX(), getBlacklistGridY(), 6, getGridPixelHeight());
    }

    private void drawBlacklistPanels(DrawContext context, int mouseX, int mouseY) {
        int leftGridX = getLeftGridX();
        int rightGridX = getRightGridX();
        int gridY = getBlacklistGridY();
        int gridWidth = getGridPixelWidth();
        int gridHeight = getGridPixelHeight();

        drawItemGrid(context, mouseX, mouseY, leftGridX, gridY, allFilteredItems, leftScrollRow, true);
        drawItemGrid(context, mouseX, mouseY, rightGridX, gridY, blacklistedItemsView, rightScrollRow, false);

        int centerX = leftGridX + gridWidth + 16;
        int centerY = gridY + gridHeight / 2 - 4;
        context.drawText(this.textRenderer, Text.literal("=>"), centerX, centerY, 0xFF202020, false);

        drawBlacklistScroller(context, getLeftScrollbarX(), gridY, gridHeight, leftScrollRow, getAllItemsMaxScrollRow());
        drawBlacklistScroller(context, getRightScrollbarX(), gridY, gridHeight, rightScrollRow, getBlacklistedMaxScrollRow());
    }

    private void drawItemGrid(DrawContext context, int mouseX, int mouseY, int gridX, int gridY,
                              List<Item> source, int scrollRow, boolean leftList) {
        int gridWidth = getGridPixelWidth();
        int gridHeight = getGridPixelHeight();
        context.fill(gridX - 1, gridY - 1, gridX + gridWidth + 1, gridY + gridHeight + 1, 0xFF3A3A3A);
        context.fill(gridX, gridY, gridX + gridWidth, gridY + gridHeight, 0xFF8B8B8B);

        int startIndex = scrollRow * BLACKLIST_COLS;
        int visibleCount = BLACKLIST_COLS * BLACKLIST_ROWS;
        for (int i = 0; i < visibleCount; i++) {
            int index = startIndex + i;
            int col = i % BLACKLIST_COLS;
            int row = i / BLACKLIST_COLS;
            int cellX = gridX + col * BLACKLIST_CELL_SIZE;
            int cellY = gridY + row * BLACKLIST_CELL_SIZE;

            context.fill(cellX + 1, cellY + 1, cellX + BLACKLIST_CELL_SIZE - 1, cellY + BLACKLIST_CELL_SIZE - 1, 0xFFC6C6C6);
            if (index >= source.size()) {
                continue;
            }

            Item item = source.get(index);
            int rawId = Registries.ITEM.getRawId(item);
            boolean selected = syncedBlacklistRawIds.contains(rawId);
            if (leftList && selected) {
                context.fill(cellX + 1, cellY + 1, cellX + BLACKLIST_CELL_SIZE - 1, cellY + BLACKLIST_CELL_SIZE - 1, 0xAA6AA84F);
            }
            if (!leftList) {
                context.fill(cellX + 1, cellY + 1, cellX + BLACKLIST_CELL_SIZE - 1, cellY + BLACKLIST_CELL_SIZE - 1, 0xAA8E5A5A);
            }

            context.drawItem(new ItemStack(item), cellX + 1, cellY + 1);

            if (mouseX >= cellX && mouseX < cellX + BLACKLIST_CELL_SIZE && mouseY >= cellY && mouseY < cellY + BLACKLIST_CELL_SIZE) {
                context.fill(cellX, cellY, cellX + BLACKLIST_CELL_SIZE, cellY + BLACKLIST_CELL_SIZE, 0x66FFFFFF);
                context.drawTooltip(this.textRenderer,
                        List.of(
                                item.getName(),
                                Text.literal(Registries.ITEM.getId(item).toString()),
                                Text.literal(leftList ? (selected ? "已加入黑名单（点击移除）" : "未加入黑名单（点击加入）")
                                        : "黑名单物品（点击移除）")
                        ), mouseX, mouseY);
            }
        }
    }

    private void drawBlacklistScroller(DrawContext context, int x, int y, int height, int scrollRow, int maxScrollRow) {
        context.fill(x, y, x + 6, y + height, 0xFF5C5C5C);
        if (maxScrollRow <= 0) {
            context.fill(x, y, x + 6, y + 15, 0xFF8A8A8A);
            return;
        }
        float progress = scrollRow / (float) maxScrollRow;
        int knobY = y + (int) ((height - 15) * progress);
        context.fill(x, knobY, x + 6, knobY + 15, 0xFFE0E0E0);
    }

    private void toggleBlacklistItem(Item item) {
        int rawId = Registries.ITEM.getRawId(item);
        if (rawId <= 0 || this.client == null || this.client.interactionManager == null) {
            return;
        }
        boolean selected = syncedBlacklistRawIds.contains(rawId);
        int action = selected
                ? CleanerBlockEntity.ACTION_REMOVE_BLACKLIST_BASE + rawId
                : CleanerBlockEntity.ACTION_ADD_BLACKLIST_BASE + rawId;
        this.client.interactionManager.clickButton(this.handler.syncId, action);
        if (selected) {
            syncedBlacklistRawIds.remove(rawId);
        } else {
            syncedBlacklistRawIds.add(rawId);
        }
        // Keep local handler cache in sync to avoid one-frame flicker
        // before the authoritative S2C payload arrives.
        handler.setSyncedBlacklistRawIds(syncedBlacklistRawIds);
        rebuildBlacklistedItemsView();
        updateBlacklistGridButtonState();
    }

    private void onBlacklistGridCellClicked(int slot) {
        int index = leftScrollRow * BLACKLIST_COLS + slot;
        if (index < 0 || index >= allFilteredItems.size()) {
            return;
        }
        toggleBlacklistItem(allFilteredItems.get(index));
    }

    private void onRightBlacklistGridCellClicked(int slot) {
        int index = rightScrollRow * BLACKLIST_COLS + slot;
        if (index < 0 || index >= blacklistedItemsView.size()) {
            return;
        }
        toggleBlacklistItem(blacklistedItemsView.get(index));
    }

    private void updateBlacklistGridButtonState() {
        int leftStart = leftScrollRow * BLACKLIST_COLS;
        for (int slot = 0; slot < leftGridButtons.size(); slot++) {
            ButtonWidget button = leftGridButtons.get(slot);
            int index = leftStart + slot;
            boolean hasItem = index >= 0 && index < allFilteredItems.size();
            button.active = hasItem;
            button.visible = hasItem;
            button.setMessage(Text.empty());
        }
        int rightStart = rightScrollRow * BLACKLIST_COLS;
        for (int slot = 0; slot < rightGridButtons.size(); slot++) {
            ButtonWidget button = rightGridButtons.get(slot);
            int index = rightStart + slot;
            boolean hasItem = index >= 0 && index < blacklistedItemsView.size();
            button.active = hasItem;
            button.visible = hasItem;
            button.setMessage(Text.empty());
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (page == Page.BLACKLIST) {
            double mouseX = click.x();
            double mouseY = click.y();
            if (isInsideLeftScrollbar(mouseX, mouseY)) {
                draggingLeftScrollbar = true;
                draggingRightScrollbar = false;
                leftScrollRow = scrollByBarClick(mouseY, getBlacklistGridY(), getGridPixelHeight(), getAllItemsMaxScrollRow());
                updateBlacklistGridButtonState();
                return true;
            }
            if (isInsideRightScrollbar(mouseX, mouseY)) {
                draggingRightScrollbar = true;
                draggingLeftScrollbar = false;
                rightScrollRow = scrollByBarClick(mouseY, getBlacklistGridY(), getGridPixelHeight(), getBlacklistedMaxScrollRow());
                updateBlacklistGridButtonState();
                return true;
            }
            draggingLeftScrollbar = false;
            draggingRightScrollbar = false;
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (page == Page.BLACKLIST) {
            boolean changed = false;
            if (draggingLeftScrollbar) {
                leftScrollRow = scrollByBarClick(click.y(), getBlacklistGridY(), getGridPixelHeight(), getAllItemsMaxScrollRow());
                changed = true;
            }
            if (draggingRightScrollbar) {
                rightScrollRow = scrollByBarClick(click.y(), getBlacklistGridY(), getGridPixelHeight(), getBlacklistedMaxScrollRow());
                changed = true;
            }
            if (changed) {
                updateBlacklistGridButtonState();
                return true;
            }
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingLeftScrollbar = false;
        draggingRightScrollbar = false;
        return super.mouseReleased(click);
    }

    private int scrollByBarClick(double mouseY, int barTop, int barHeight, int maxScrollRow) {
        if (maxScrollRow <= 0) {
            return 0;
        }
        double ratio = (mouseY - barTop) / (double) Math.max(1, barHeight - 1);
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        return (int) Math.round(ratio * maxScrollRow);
    }

    private void requestBlacklistSync() {
        if (this.client == null || this.client.player == null) {
            return;
        }
        ClientPlayNetworking.send(new ModNetworking.RequestBlacklistSyncPayload(this.handler.syncId));
    }
}
