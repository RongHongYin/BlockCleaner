package com.example.blockcleaner.client;

import com.example.blockcleaner.CleanerBlockEntity;
import com.example.blockcleaner.CleanerScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

public class CleanerScreen extends HandledScreen<CleanerScreenHandler> {
    private enum Page {
        MAIN,
        CLEAR
    }

    private Page page = Page.MAIN;
    private ButtonWidget directionButton;
    private ButtonWidget modeButton;
    private ButtonWidget speedModeButton;
    private ButtonWidget startStopButton;
    private ButtonWidget rangeMinusButton;
    private ButtonWidget rangePlusButton;
    private ButtonWidget speedMinusButton;
    private ButtonWidget speedPlusButton;
    private TextFieldWidget rangeInput;
    private TextFieldWidget targetYInput;
    private TextFieldWidget speedInput;
    private boolean suppressInputCallbacks = false;

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
        this.startStopButton = null;
        this.rangeMinusButton = null;
        this.rangePlusButton = null;
        this.speedMinusButton = null;
        this.speedPlusButton = null;
        this.rangeInput = null;
        this.targetYInput = null;
        this.speedInput = null;
        if (page == Page.MAIN) {
            initMainPage();
        } else {
            initClearPage();
        }
    }

    private void initMainPage() {
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
        int row = this.y + 32;
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
        this.modeButton = this.addDrawableChild(ButtonWidget.builder(
                Text.literal(handler.getMode() == CleanerBlockEntity.MODE_CREATIVE ? "创造" : "生存"),
                b -> sendAction(8)).dimensions(mainButtonX, row, buttonW, buttonH).build());

        // 2) 方向
        this.directionButton = this.addDrawableChild(ButtonWidget.builder(
                Text.literal(handler.getDirection() == CleanerBlockEntity.DIR_UP ? "↑" : "↓"),
                b -> sendAction(handler.getDirection() == CleanerBlockEntity.DIR_UP ? 2 : 1))
                .dimensions(mainButtonX, row + 28, buttonW, buttonH).build());

        // 3) 目标Y轴（输入）
        this.targetYInput = this.addDrawableChild(new TextFieldWidget(this.textRenderer, inputX, row + 56, inputW, buttonH, Text.literal("目标Y")));
        this.targetYInput.setMaxLength(6);
        this.targetYInput.setText(Integer.toString(handler.getTargetY()));
        this.targetYInput.setChangedListener(this::onTargetYChanged);

        // 4) 范围（输入 + -）
        this.rangeInput = this.addDrawableChild(new TextFieldWidget(this.textRenderer, inputX, row + 84, inputW, buttonH, Text.literal("范围")));
        this.rangeInput.setMaxLength(3);
        this.rangeInput.setText(Integer.toString(handler.getRangeChunks()));
        this.rangeInput.setChangedListener(this::onRangeChanged);

        this.rangeMinusButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("-"),
                b -> sendAction(4)).dimensions(minusX, row + 84, smallW, buttonH).build());
        this.rangePlusButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("+"),
                b -> sendAction(3)).dimensions(plusX, row + 84, smallW, buttonH).build());

        // 5) 速度模式
        this.speedModeButton = this.addDrawableChild(ButtonWidget.builder(
                Text.literal(handler.getSpeedMode() == CleanerBlockEntity.SPEED_FIXED ? "固定" : "原版"),
                b -> sendAction(9)).dimensions(mainButtonX, row + 112, buttonW, buttonH).build());

        // 6) 速度（输入 + -）
        this.speedInput = this.addDrawableChild(new TextFieldWidget(this.textRenderer, inputX, row + 140, inputW, buttonH, Text.literal("速度")));
        this.speedInput.setMaxLength(5);
        this.speedInput.setText(Integer.toString(handler.getSpeedPerSecond()));
        this.speedInput.setChangedListener(this::onSpeedChanged);

        this.speedMinusButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("-"),
                b -> sendAction(6)).dimensions(minusX, row + 140, smallW, buttonH).build());
        this.speedPlusButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("+"),
                b -> sendAction(5)).dimensions(plusX, row + 140, smallW, buttonH).build());

        // 启动 / 返回 同一行
        int bottomButtonW = 104;
        int bottomButtonH = 24;
        int bottomY = row + 176;
        int startX = rightEdge - (bottomButtonW * 2 + gap);
        int backX = rightEdge - bottomButtonW;

        this.startStopButton = this.addDrawableChild(ButtonWidget.builder(Text.literal(handler.isActive() ? "停止" : "启动"),
                b -> sendAction(7)).dimensions(startX, bottomY, bottomButtonW, bottomButtonH).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("返回"),
                b -> {
                    page = Page.MAIN;
                    init();
                }).dimensions(backX, bottomY, bottomButtonW, bottomButtonH).build());
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
            int row = this.y + 32;
            int textColor = 0xFF202020;

            context.drawText(this.textRenderer, Text.literal("模式"), left, row + 6, textColor, false);
            context.drawText(this.textRenderer, Text.literal("方向"), left, row + 34, textColor, false);
            context.drawText(this.textRenderer, Text.literal("目标Y"), left, row + 62, textColor, false);
            context.drawText(this.textRenderer, Text.literal("范围"), left, row + 90, textColor, false);
            context.drawText(this.textRenderer, Text.literal("（奇数 1-99）"), left + 24, row + 90, 0xFF565656, false);
            context.drawText(this.textRenderer, Text.literal("速度模式"), left, row + 118, textColor, false);
            context.drawText(this.textRenderer, Text.literal("速度"), left, row + 146, textColor, false);
            context.drawText(this.textRenderer, Text.literal("（10 的倍数）"), left + 20, row + 146, 0xFF565656, false);
        }

        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(this.textRenderer, Text.literal("区块清除器"), 10, 8, 0xFF202020, false);
        if (page == Page.MAIN) {
            context.drawText(this.textRenderer, Text.literal("选择功能"), 10, 24, 0xFF202020, false);
        } else {
            context.drawText(this.textRenderer, Text.literal("清除配置"), 10, 24, 0xFF202020, false);
        }
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
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
}
