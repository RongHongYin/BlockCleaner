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
        BUILD,
        BUILD_BLOCK_PICKER,
        BLACKLIST
    }

    private Page page = Page.MAIN;
    private ButtonWidget directionButton;
    private ButtonWidget modeButton;
    private ButtonWidget speedModeButton;
    private ButtonWidget keepDurabilityButton;
    private ButtonWidget rangeModeButton;
    private ButtonWidget startStopButton;
    private ButtonWidget clearBuildLinkButton;
    private ButtonWidget buildStartStopButton;
    private ButtonWidget[] buildFaceEnabledButtons = new ButtonWidget[6];
    private ButtonWidget[] buildFaceModeButtons = new ButtonWidget[6];
    private ButtonWidget[] buildFaceBlockButtons = new ButtonWidget[6];
    private ButtonWidget buildDetailEnableButton;
    private ButtonWidget buildLayerModeButton;
    private ButtonWidget buildDetailModeButton;
    private ButtonWidget buildDetailBlockButton;
    private ButtonWidget buildAllEnableButton;
    private ButtonWidget buildAllDisableButton;
    private ButtonWidget buildCopyToAllButton;
    private ButtonWidget buildLinkButton;
    private ButtonWidget rangeMinusButton;
    private ButtonWidget rangePlusButton;
    private ButtonWidget speedMinusButton;
    private ButtonWidget speedPlusButton;
    private TextFieldWidget rangeInput;
    private TextFieldWidget targetYInput;
    private TextFieldWidget speedInput;
    private TextFieldWidget blacklistSearchInput;
    private TextFieldWidget buildBlockSearchInput;
    private final List<ButtonWidget> leftGridButtons = new ArrayList<>();
    private final List<ButtonWidget> rightGridButtons = new ArrayList<>();
    private final List<Item> allFilteredItems = new ArrayList<>();
    private final List<Item> blacklistedItemsView = new ArrayList<>();
    private final List<Item> buildBlockFilteredItems = new ArrayList<>();
    private int leftScrollRow = 0;
    private int rightScrollRow = 0;
    private boolean draggingLeftScrollbar = false;
    private boolean draggingRightScrollbar = false;
    private Set<Integer> syncedBlacklistRawIds = new HashSet<>();
    private boolean suppressInputCallbacks = false;
    private int clearScrollOffset = 0;
    private final List<ClickableWidget> scrollWidgets = new ArrayList<>();
    private int selectedBuildFace = 0;
    private int buildBlockPickerFace = 0;
    private static final int BLACKLIST_COLS = 5;
    private static final int BLACKLIST_ROWS = 4;
    private static final int BLACKLIST_CELL_SIZE = 18;

    public CleanerScreen(CleanerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 340;
        this.backgroundHeight = 260;
        this.titleX = 10;
        this.titleY = 8;
    }

    @Override
    protected void init() {
        int desiredWidth = 340;
        int desiredHeight = 260;
        this.backgroundWidth = Math.min(desiredWidth, Math.max(300, this.width - 12));
        this.backgroundHeight = Math.min(desiredHeight, Math.max(220, this.height - 12));
        super.init();
        this.clearChildren();
        this.directionButton = null;
        this.modeButton = null;
        this.speedModeButton = null;
        this.keepDurabilityButton = null;
        this.rangeModeButton = null;
        this.startStopButton = null;
        this.clearBuildLinkButton = null;
        this.buildStartStopButton = null;
        this.buildFaceEnabledButtons = new ButtonWidget[6];
        this.buildFaceModeButtons = new ButtonWidget[6];
        this.buildFaceBlockButtons = new ButtonWidget[6];
        this.buildDetailEnableButton = null;
        this.buildLayerModeButton = null;
        this.buildDetailModeButton = null;
        this.buildDetailBlockButton = null;
        this.buildAllEnableButton = null;
        this.buildAllDisableButton = null;
        this.buildCopyToAllButton = null;
        this.buildLinkButton = null;
        this.rangeMinusButton = null;
        this.rangePlusButton = null;
        this.speedMinusButton = null;
        this.speedPlusButton = null;
        this.rangeInput = null;
        this.targetYInput = null;
        this.speedInput = null;
        this.blacklistSearchInput = null;
        this.buildBlockSearchInput = null;
        this.leftGridButtons.clear();
        this.rightGridButtons.clear();
        this.scrollWidgets.clear();
        if (page == Page.MAIN) {
            initMainPage();
        } else if (page == Page.CLEAR) {
            initClearPage();
        } else if (page == Page.BUILD) {
            initBuildPage();
        } else if (page == Page.BUILD_BLOCK_PICKER) {
            initBuildBlockPickerPage();
        } else {
            initBlacklistPage();
        }
    }

    private void initMainPage() {
        clearScrollOffset = 0;
        int x = this.x + 28;
        int y = this.y + 44;
        int buttonW = this.backgroundWidth - 56;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("清除"), b -> {
            page = Page.CLEAR;
            init();
        }).dimensions(x, y, buttonW, 24).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("建造"), b -> {
            page = Page.BUILD;
            init();
        }).dimensions(x, y + 34, buttonW, 24).build());
    }

    private void initClearPage() {
        int left = this.x + 14;
        int right = this.x + this.backgroundWidth - 14;
        int top = getScrollTop();
        int contentTop = top + 8 + clearScrollOffset;
        int colGap = 10;
        int colW = (right - left - colGap) / 2;
        int rowGap = 6;
        int rowH = 20;

        int leftColX = left;
        int rightColX = left + colW + colGap;
        int row0 = contentTop;
        int row1 = row0 + rowH + rowGap;
        int row2 = row1 + rowH + rowGap;
        int row3 = row2 + rowH + rowGap;

        int labelW = 62;
        int valueW = colW - labelW - 10;
        int valueXLeft = leftColX + labelW + 8;
        int valueXRight = rightColX + labelW + 8;

        // 左列：清除模式、范围模式、目标Y、速度
        this.modeButton = addScrollable(this.addDrawableChild(ButtonWidget.builder(
                Text.literal(handler.getMode() == CleanerBlockEntity.MODE_CREATIVE ? "创造模式" : "生存模式"),
                b -> sendAction(8)).dimensions(valueXLeft, row0, valueW, rowH).build()));
        this.rangeModeButton = addScrollable(this.addDrawableChild(ButtonWidget.builder(
                Text.literal(handler.getRangeMode() == CleanerBlockEntity.RANGE_MODE_TOP_LEFT ? "左上角" : "中心"),
                b -> sendAction(11)).dimensions(valueXLeft, row1, valueW, rowH).build()));
        this.targetYInput = addScrollable(this.addDrawableChild(new TextFieldWidget(this.textRenderer, valueXLeft, row2, valueW, rowH, Text.literal("目标Y"))));
        this.targetYInput.setMaxLength(6);
        this.targetYInput.setText(Integer.toString(handler.getTargetY()));
        this.targetYInput.setChangedListener(this::onTargetYChanged);
        this.speedInput = addScrollable(this.addDrawableChild(new TextFieldWidget(this.textRenderer, valueXLeft, row3, valueW, rowH, Text.literal("速度"))));
        this.speedInput.setMaxLength(5);
        this.speedInput.setText(Integer.toString(handler.getSpeedPerSecond()));
        this.speedInput.setChangedListener(this::onSpeedChanged);

        // 右列：方向、范围、速度模式、保留1耐久
        this.directionButton = addScrollable(this.addDrawableChild(ButtonWidget.builder(
                Text.literal(handler.getDirection() == CleanerBlockEntity.DIR_UP ? "向上" : "向下"),
                b -> sendAction(handler.getDirection() == CleanerBlockEntity.DIR_UP ? 2 : 1))
                .dimensions(valueXRight, row0, valueW, rowH).build()));

        int stepW = 22;
        int rangeInputW = valueW - stepW * 2 - 8;
        this.rangeInput = addScrollable(this.addDrawableChild(new TextFieldWidget(this.textRenderer, valueXRight + stepW + 4, row1, rangeInputW, rowH, Text.literal("范围"))));
        this.rangeInput.setMaxLength(3);
        this.rangeInput.setText(Integer.toString(handler.getRangeChunks()));
        this.rangeInput.setChangedListener(this::onRangeChanged);
        this.rangeMinusButton = addScrollable(this.addDrawableChild(ButtonWidget.builder(Text.literal("-"),
                b -> sendAction(4)).dimensions(valueXRight, row1, stepW, rowH).build()));
        this.rangePlusButton = addScrollable(this.addDrawableChild(ButtonWidget.builder(Text.literal("+"),
                b -> sendAction(3)).dimensions(valueXRight + stepW + 4 + rangeInputW + 4, row1, stepW, rowH).build()));

        this.speedModeButton = addScrollable(this.addDrawableChild(ButtonWidget.builder(
                Text.literal(handler.getSpeedMode() == CleanerBlockEntity.SPEED_FIXED ? "固定" : "原版"),
                b -> sendAction(9)).dimensions(valueXRight, row2, valueW, rowH).build()));
        this.keepDurabilityButton = addScrollable(this.addDrawableChild(ButtonWidget.builder(
                Text.literal(handler.shouldKeepOneDurability() ? "开" : "关"),
                b -> sendAction(10)).dimensions(valueXRight, row3, valueW, rowH).build()));

        int sectionTop = row3 + rowH + 14;
        int moduleGap = 8;
        int sectionH = 36;
        int blacklistTop = sectionTop + sectionH + moduleGap;
        int sectionLabelW = 116;
        int sectionBtnW = 104;
        int sectionBtn2W = 96;
        int sectionValueW = 84;

        // 建造联动模块
        this.clearBuildLinkButton = addScrollable(this.addDrawableChild(ButtonWidget.builder(
                Text.literal(handler.isBuildWithClear() ? "开" : "关"),
                b -> sendAction(12)).dimensions(left + sectionLabelW + 8, sectionTop + 8, sectionValueW, 20).build()));
        addScrollable(this.addDrawableChild(ButtonWidget.builder(Text.literal("建造配置  >>"),
                b -> {
                    page = Page.BUILD;
                    init();
                }).dimensions(right - sectionBtnW, sectionTop + 6, sectionBtnW, 24).build()));

        // 黑名单模块
        addScrollable(this.addDrawableChild(ButtonWidget.builder(Text.literal("管理  >>"),
                b -> {
                    page = Page.BLACKLIST;
                    init();
                }).dimensions(right - sectionBtn2W, blacklistTop + 6, sectionBtn2W, 24).build()));

        // 第三层：底部关键按钮（固定，不滚动）
        int gap = 10;
        int buttonW = 96;
        int bottomY = this.y + this.backgroundHeight - 34;
        int backX = right - buttonW;
        int linkX = backX - gap - buttonW;
        int startX = linkX - gap - buttonW;
        this.startStopButton = this.addDrawableChild(ButtonWidget.builder(Text.literal(handler.isActive() ? "停止清除" : "启动清除"),
                b -> sendAction(7)).dimensions(startX, bottomY, buttonW, 24).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal(handler.isBuildWithClear() ? "联动开" : "联动关"),
                b -> sendAction(12)).dimensions(linkX, bottomY, buttonW, 24).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("返回"),
                b -> {
                    page = Page.MAIN;
                    init();
                }).dimensions(backX, bottomY, buttonW, 24).build());
        updateScrollableVisibility();
    }

    private void initBuildPage() {
        selectedBuildFace = Math.max(0, Math.min(5, selectedBuildFace));
        int leftX = this.x + 14;
        int panelTop = this.y + 44;
        int rowTop = panelTop + 12;
        int leftPanelW = 132;
        int rightPanelX = leftX + leftPanelW + 14;
        int rightPanelW = this.x + this.backgroundWidth - 14 - rightPanelX;
        int bottomY = this.y + this.backgroundHeight - 34;
        int rowH = 20;
        int batchButtonH = 18;
        int batchGap = 2;
        int batchTotalH = batchButtonH * 3 + batchGap * 2;
        int batchTop = bottomY - 8 - batchTotalH;
        int rowGap = Math.max(18, (batchTop - rowTop - rowH) / 5);

        for (int i = 0; i < 6; i++) {
            int y = rowTop + i * rowGap;
            int face = i;
            buildFaceEnabledButtons[i] = this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(getBuildFaceListLabel(i)),
                    b -> {
                        selectedBuildFace = face;
                        init();
                    }).dimensions(leftX, y, leftPanelW, rowH).build());
        }

        buildAllEnableButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("全部启用"),
                b -> sendAction(106)).dimensions(leftX, batchTop, leftPanelW, batchButtonH).build());
        buildAllDisableButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("全部关闭"),
                b -> sendAction(107)).dimensions(leftX, batchTop + batchButtonH + batchGap, leftPanelW, batchButtonH).build());
        buildCopyToAllButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("复制到全部"),
                b -> sendAction(700000 + selectedBuildFace)).dimensions(leftX, batchTop + (batchButtonH + batchGap) * 2, leftPanelW, batchButtonH).build());

        int contentX = rightPanelX + 14;
        int toggleW = 84;
        int detailValueX = rightPanelX + rightPanelW - toggleW - 14;
        buildLayerModeButton = this.addDrawableChild(ButtonWidget.builder(
                Text.literal(handler.getBuildLayerMode() == CleanerBlockEntity.BUILD_LAYER_OUTER ? "外侧" : "内侧"),
                b -> sendAction(14)).dimensions(detailValueX, rowTop - 6, toggleW, 20).build());
        buildDetailEnableButton = this.addDrawableChild(ButtonWidget.builder(
                Text.literal(handler.isBuildFaceEnabled(selectedBuildFace) ? "开" : "关"),
                b -> sendAction(100 + selectedBuildFace)).dimensions(detailValueX, rowTop + 22, toggleW, 20).build());
        buildDetailModeButton = this.addDrawableChild(ButtonWidget.builder(
                Text.literal(handler.isBuildFaceUseSpecific(selectedBuildFace) ? "指定方块" : "任意方块"),
                b -> sendAction(200 + selectedBuildFace)).dimensions(detailValueX - 16, rowTop + 54, toggleW + 16, 20).build());
        buildDetailBlockButton = this.addDrawableChild(ButtonWidget.builder(
                Text.literal(getBuildFaceBlockLabel(selectedBuildFace)),
                b -> {
                    buildBlockPickerFace = selectedBuildFace;
                    page = Page.BUILD_BLOCK_PICKER;
                    init();
                }).dimensions(contentX + 64, rowTop + 86, rightPanelW - 88, 20).build());

        int gap = 10;
        int buttonW = 96;
        int backX = this.x + this.backgroundWidth - 14 - buttonW;
        int linkX = backX - gap - buttonW;
        int startX = linkX - gap - buttonW;
        this.buildStartStopButton = this.addDrawableChild(ButtonWidget.builder(
                Text.literal(handler.isBuildActive() ? "停止建造" : "启动建造"),
                b -> sendAction(13)).dimensions(startX, bottomY, buttonW, 24).build());
        this.buildLinkButton = this.addDrawableChild(ButtonWidget.builder(
                Text.literal(handler.isBuildWithClear() ? "联动开" : "联动关"),
                b -> sendAction(12)).dimensions(linkX, bottomY, buttonW, 24).build());
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("返回"),
                b -> {
                    page = Page.MAIN;
                    init();
                }).dimensions(backX, bottomY, buttonW, 24).build());
    }

    private void initBuildBlockPickerPage() {
        int left = this.x + 14;
        int right = this.x + this.backgroundWidth - 14;
        int top = this.y + 44;
        this.buildBlockSearchInput = this.addDrawableChild(
                new TextFieldWidget(this.textRenderer, left, top, right - left, 20, Text.literal("搜索方块")));
        this.buildBlockSearchInput.setMaxLength(80);
        this.buildBlockSearchInput.setChangedListener(this::onBuildBlockSearchChanged);

        int gridY = getBuildPickerGridY();
        int gridX = left;
        for (int row = 0; row < BLACKLIST_ROWS; row++) {
            for (int col = 0; col < BLACKLIST_COLS; col++) {
                int slot = row * BLACKLIST_COLS + col;
                int x = gridX + col * BLACKLIST_CELL_SIZE;
                int y = gridY + row * BLACKLIST_CELL_SIZE;
                ButtonWidget cell = this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> onBuildBlockGridCellClicked(slot))
                        .dimensions(x, y, BLACKLIST_CELL_SIZE, BLACKLIST_CELL_SIZE).build());
                leftGridButtons.add(cell);
            }
        }
        rebuildBuildBlockSearchResults();

        int bottomY = this.y + this.backgroundHeight - 34;
        int buttonW = 90;
        int gap = 10;
        int backX = right - buttonW;
        int clearX = backX - gap - buttonW;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("返回"),
                b -> {
                    page = Page.BUILD;
                    init();
                }).dimensions(backX, bottomY, buttonW, 24).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("清空指定"),
                b -> {
                    this.client.interactionManager.clickButton(this.handler.syncId, 200 + buildBlockPickerFace);
                    page = Page.BUILD;
                    init();
                }).dimensions(clearX, bottomY, buttonW, 24).build());
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
            int right = this.x + this.backgroundWidth - 14;
            int top = getScrollTop();
            int contentTop = top + 8 + clearScrollOffset;
            int colGap = 10;
            int colW = (right - left - colGap) / 2;
            int rowGap = 6;
            int rowH = 20;
            int leftColX = left;
            int rightColX = left + colW + colGap;
            int row0 = contentTop;
            int row1 = row0 + rowH + rowGap;
            int row2 = row1 + rowH + rowGap;
            int row3 = row2 + rowH + rowGap;
            int textColor = 0xFF202020;

            int viewportTop = getScrollTop();
            int viewportBottom = getScrollBottom();
            context.enableScissor(left - 4, viewportTop, right + 4, viewportBottom);

            drawFrame(context, leftColX - 2, row0 - 2, colW + 4, (row3 + rowH) - row0 + 4);
            drawFrame(context, rightColX - 2, row0 - 2, colW + 4, (row3 + rowH) - row0 + 4);

            context.drawText(this.textRenderer, Text.literal("清除模式"), leftColX + 8, row0 + 8, textColor, false);
            context.drawText(this.textRenderer, Text.literal("范围模式"), leftColX + 8, row1 + 8, textColor, false);
            context.drawText(this.textRenderer, Text.literal("目标 Y"), leftColX + 8, row2 + 8, textColor, false);
            context.drawText(this.textRenderer, Text.literal("速度(方块/秒)"), leftColX + 8, row3 + 8, textColor, false);

            context.drawText(this.textRenderer, Text.literal("方向"), rightColX + 8, row0 + 8, textColor, false);
            context.drawText(this.textRenderer, Text.literal("范围(区块)"), rightColX + 8, row1 + 8, textColor, false);
            context.drawText(this.textRenderer, Text.literal("速度模式"), rightColX + 8, row2 + 8, textColor, false);
            context.drawText(this.textRenderer, Text.literal("保留1耐久"), rightColX + 8, row3 + 8, textColor, false);

            int sectionTop = row3 + rowH + 14;
            int moduleGap = 8;
            int sectionH = 36;
            int blacklistTop = sectionTop + sectionH + moduleGap;
            drawFrame(context, left - 2, sectionTop - 2, right - left + 4, sectionH + 4);
            context.drawText(this.textRenderer, Text.literal("建造联动"), left + 8, sectionTop + 8, textColor, false);

            drawFrame(context, left - 2, blacklistTop - 2, right - left + 4, sectionH + 4);
            context.drawText(this.textRenderer, Text.literal("黑名单管理（全局共享）"), left + 8, blacklistTop + 8, textColor, false);
            context.drawText(this.textRenderer, Text.literal("数量: " + syncedBlacklistRawIds.size()), left + 170, blacklistTop + 12, 0xFF565656, false);
            context.disableScissor();

            // 可滚动第二层的可视区域边界提示
            context.fill(left - 4, viewportTop - 1, right + 4, viewportTop, 0x66FFFFFF);
            context.fill(left - 4, viewportBottom, right + 4, viewportBottom + 1, 0x66555555);
        } else if (page == Page.BUILD) {
            int leftX = this.x + 14;
            int panelTop = this.y + 44;
            int rowTop = panelTop + 12;
            int leftPanelW = 146;
            int rightPanelX = leftX + leftPanelW + 14;
            int rightPanelW = this.x + this.backgroundWidth - 14 - rightPanelX;
            int splitBottom = this.y + this.backgroundHeight - 42;

            // Left/right grouped panels and separator.
            drawFrame(context, leftX - 2, panelTop + 12, leftPanelW + 4, splitBottom - (panelTop + 12));
            drawFrame(context, rightPanelX, panelTop + 12, rightPanelW, splitBottom - (panelTop + 12));
            context.drawText(this.textRenderer, Text.literal("面选择"), leftX, panelTop + 2, 0xFF202020, false);
            context.drawText(this.textRenderer, Text.literal("当前面: " + getBuildFaceName(selectedBuildFace)), rightPanelX + 10, panelTop + 2, 0xFF202020, false);
            context.drawText(this.textRenderer, Text.literal("建造层"), rightPanelX + 14, rowTop, 0xFF202020, false);
            context.drawText(this.textRenderer, Text.literal("启用"), rightPanelX + 14, rowTop + 28, 0xFF202020, false);
            context.drawText(this.textRenderer, Text.literal("材料模式"), rightPanelX + 14, rowTop + 60, 0xFF202020, false);
            context.drawText(this.textRenderer, Text.literal("选择方块"), rightPanelX + 14, rowTop + 92, 0xFF202020, false);
            context.drawText(this.textRenderer, Text.literal("说明"), rightPanelX + 14, rowTop + 130, 0xFF202020, false);
            context.drawText(this.textRenderer, Text.literal("指定方块：仅消耗该方块"), rightPanelX + 18, rowTop + 146, 0xFF565656, false);
            context.drawText(this.textRenderer, Text.literal("任意方块：可消耗任意可放置方块"), rightPanelX + 18, rowTop + 160, 0xFF565656, false);
        } else if (page == Page.BUILD_BLOCK_PICKER) {
            int left = this.x + 14;
            int top = this.y + 44;
            int gridY = getBuildPickerGridY();
            context.drawText(this.textRenderer, Text.literal("选择指定方块 - " + getBuildFaceName(buildBlockPickerFace)),
                    left, top - 12, 0xFF202020, false);
            drawBuildBlockPickerPanel(context, mouseX, mouseY, left, gridY);
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
        } else if (page == Page.BUILD) {
            context.drawText(this.textRenderer, Text.literal("建造配置"), 10, 24, 0xFF202020, false);
        } else if (page == Page.BUILD_BLOCK_PICKER) {
            context.drawText(this.textRenderer, Text.literal("方块选择"), 10, 24, 0xFF202020, false);
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
        if (page == Page.BUILD_BLOCK_PICKER) {
            updateBuildBlockPickerButtonState();
            return;
        }
        if (page != Page.CLEAR) {
            if (page == Page.BUILD) {
                if (buildStartStopButton != null) {
                    buildStartStopButton.setMessage(Text.literal(handler.isBuildActive() ? "停止建造" : "启动建造"));
                }
                if (buildLinkButton != null) {
                    buildLinkButton.setMessage(Text.literal(handler.isBuildWithClear() ? "联动:开" : "联动:关"));
                }
                for (int i = 0; i < 6; i++) {
                    if (buildFaceEnabledButtons[i] != null) {
                        buildFaceEnabledButtons[i].setMessage(Text.literal(getBuildFaceListLabel(i)));
                    }
                }
                if (buildDetailEnableButton != null) {
                    buildDetailEnableButton.setMessage(Text.literal(handler.isBuildFaceEnabled(selectedBuildFace) ? "开" : "关"));
                }
                if (buildLayerModeButton != null) {
                    buildLayerModeButton.setMessage(Text.literal(handler.getBuildLayerMode() == CleanerBlockEntity.BUILD_LAYER_OUTER ? "外侧" : "内侧"));
                }
                if (buildDetailModeButton != null) {
                    buildDetailModeButton.setMessage(Text.literal(handler.isBuildFaceUseSpecific(selectedBuildFace) ? "指定方块" : "任意方块"));
                }
                if (buildDetailBlockButton != null) {
                    buildDetailBlockButton.setMessage(Text.literal(getBuildFaceBlockLabel(selectedBuildFace)));
                    buildDetailBlockButton.active = handler.isBuildFaceUseSpecific(selectedBuildFace);
                }
            }
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
        if (rangeModeButton != null) {
            rangeModeButton.setMessage(Text.literal(handler.getRangeMode() == CleanerBlockEntity.RANGE_MODE_TOP_LEFT ? "左上角" : "中心"));
        }
        if (keepDurabilityButton != null) {
            keepDurabilityButton.setMessage(Text.literal(handler.shouldKeepOneDurability() ? "是" : "否"));
        }
        if (startStopButton != null) {
            startStopButton.setMessage(Text.literal(handler.isActive() ? "停止清除" : "启动清除"));
        }
        if (clearBuildLinkButton != null) {
            clearBuildLinkButton.setMessage(Text.literal(handler.isBuildWithClear() ? "开" : "关"));
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
                    || startStopButton.getMessage().getString().contains("启动")
                    || !handler.isActive());
            this.client.interactionManager.clickButton(this.handler.syncId, action);
            // Optimistic local refresh for fast repeated +/- clicking.
            if (action == 3 || action == 4) {
                int range = parseIntOr(rangeInput != null ? rangeInput.getText() : "", handler.getRangeChunks());
                int step = handler.getRangeMode() == CleanerBlockEntity.RANGE_MODE_CENTER ? 2 : 1;
                range = (action == 3) ? range + step : range - step;
                range = Math.max(1, Math.min(99, range));
                if (handler.getRangeMode() == CleanerBlockEntity.RANGE_MODE_CENTER && range % 2 == 0) {
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
            if (handler.getRangeMode() == CleanerBlockEntity.RANGE_MODE_CENTER && value % 2 == 0) {
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

    private String getBuildFaceBlockLabel(int face) {
        int rawId = handler.getBuildFaceSpecificRawId(face);
        if (rawId <= 0) {
            return "选择方块";
        }
        Item item = Registries.ITEM.get(rawId);
        if (item == null) {
            return "选择方块";
        }
        return item.getName().getString();
    }

    private String getBuildFaceName(int face) {
        return switch (face) {
            case 0 -> "上表面";
            case 1 -> "下表面";
            case 2 -> "前表面";
            case 3 -> "后表面";
            case 4 -> "左表面";
            case 5 -> "右表面";
            default -> "未知";
        };
    }

    private String getBuildFaceListLabel(int face) {
        String marker = face == selectedBuildFace ? "> " : "  ";
        String state = handler.isBuildFaceEnabled(face) ? "√" : "×";
        return marker + getBuildFaceName(face) + " " + state;
    }

    private void drawFrame(DrawContext context, int x, int y, int w, int h) {
        int right = x + w;
        int bottom = y + h;
        context.fill(x, y, right, y + 1, 0xFFE3E3E3);
        context.fill(x, y, x + 1, bottom, 0xFFE3E3E3);
        context.fill(x, bottom - 1, right, bottom, 0xFF6B6B6B);
        context.fill(right - 1, y, right, bottom, 0xFF6B6B6B);
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
        if (page == Page.BUILD_BLOCK_PICKER && verticalAmount != 0) {
            if (isInsideBuildPickerListArea(mouseX, mouseY) || isInsideBuildPickerScrollbar(mouseX, mouseY)) {
                leftScrollRow = clampScroll(leftScrollRow + (verticalAmount > 0 ? -1 : 1), getBuildBlockMaxScrollRow());
                updateBuildBlockPickerButtonState();
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

    private void onBuildBlockSearchChanged(String text) {
        rebuildBuildBlockSearchResults();
    }

    private void rebuildBuildBlockSearchResults() {
        buildBlockFilteredItems.clear();
        String keyword = buildBlockSearchInput == null ? "" : buildBlockSearchInput.getText().trim().toLowerCase(Locale.ROOT);
        for (Item item : Registries.ITEM) {
            int rawId = Registries.ITEM.getRawId(item);
            if (rawId <= 0 || !(item instanceof net.minecraft.item.BlockItem blockItem)) {
                continue;
            }
            if (blockItem.getBlock().getDefaultState().isAir()) {
                continue;
            }
            String id = Registries.ITEM.getId(item).toString().toLowerCase(Locale.ROOT);
            String name = item.getName().getString().toLowerCase(Locale.ROOT);
            if (!keyword.isEmpty() && !id.contains(keyword) && !name.contains(keyword)) {
                continue;
            }
            buildBlockFilteredItems.add(item);
        }
        buildBlockFilteredItems.sort(Comparator.comparing(item -> Registries.ITEM.getId(item).toString()));
        leftScrollRow = clampScroll(leftScrollRow, getBuildBlockMaxScrollRow());
        updateBuildBlockPickerButtonState();
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

    private int getBuildPickerGridY() {
        return this.y + 74;
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

    private int getBuildBlockMaxScrollRow() {
        int totalRows = (buildBlockFilteredItems.size() + BLACKLIST_COLS - 1) / BLACKLIST_COLS;
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

    private boolean isInsideBuildPickerListArea(double mouseX, double mouseY) {
        return isInsideRect(mouseX, mouseY, getLeftGridX(), getBuildPickerGridY(), getGridPixelWidth(), getGridPixelHeight());
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

    private boolean isInsideBuildPickerScrollbar(double mouseX, double mouseY) {
        return isInsideRect(mouseX, mouseY, getLeftScrollbarX(), getBuildPickerGridY(), 6, getGridPixelHeight());
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

    private void drawBuildBlockPickerPanel(DrawContext context, int mouseX, int mouseY, int gridX, int gridY) {
        int gridWidth = getGridPixelWidth();
        int gridHeight = getGridPixelHeight();
        drawItemGrid(context, mouseX, mouseY, gridX, gridY, buildBlockFilteredItems, leftScrollRow, false);
        drawBlacklistScroller(context, getLeftScrollbarX(), gridY, gridHeight, leftScrollRow, getBuildBlockMaxScrollRow());
        context.drawText(this.textRenderer, Text.literal("点击选择后返回建造页"), gridX + gridWidth + 12, gridY + 4, 0xFF565656, false);
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

    private void onBuildBlockGridCellClicked(int slot) {
        int index = leftScrollRow * BLACKLIST_COLS + slot;
        if (index < 0 || index >= buildBlockFilteredItems.size() || this.client == null || this.client.interactionManager == null) {
            return;
        }
        Item item = buildBlockFilteredItems.get(index);
        int rawId = Registries.ITEM.getRawId(item);
        if (rawId <= 0) {
            return;
        }
        int action = CleanerBlockEntity.ACTION_SET_BUILD_FACE_BLOCK_BASE + buildBlockPickerFace * 100000 + rawId;
        this.client.interactionManager.clickButton(this.handler.syncId, action);
        page = Page.BUILD;
        init();
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

    private void updateBuildBlockPickerButtonState() {
        int leftStart = leftScrollRow * BLACKLIST_COLS;
        for (int slot = 0; slot < leftGridButtons.size(); slot++) {
            ButtonWidget button = leftGridButtons.get(slot);
            int index = leftStart + slot;
            boolean hasItem = index >= 0 && index < buildBlockFilteredItems.size();
            button.active = hasItem;
            button.visible = hasItem;
            button.setMessage(Text.empty());
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (page == Page.BUILD_BLOCK_PICKER) {
            double mouseX = click.x();
            double mouseY = click.y();
            if (isInsideBuildPickerScrollbar(mouseX, mouseY)) {
                draggingLeftScrollbar = true;
                leftScrollRow = scrollByBarClick(mouseY, getBuildPickerGridY(), getGridPixelHeight(), getBuildBlockMaxScrollRow());
                updateBuildBlockPickerButtonState();
                return true;
            }
            draggingLeftScrollbar = false;
        }
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
        if (page == Page.BUILD_BLOCK_PICKER && draggingLeftScrollbar) {
            leftScrollRow = scrollByBarClick(click.y(), getBuildPickerGridY(), getGridPixelHeight(), getBuildBlockMaxScrollRow());
            updateBuildBlockPickerButtonState();
            return true;
        }
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
