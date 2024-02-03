package dev.isxander.controlify.gui.screen;

import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.controller.Controller;
import dev.isxander.controlify.controller.composable.gyro.GyroState;
import dev.isxander.controlify.controllermanager.ControllerManager;
import dev.isxander.controlify.utils.ClientUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Controller calibration screen does a few things:
 * <ul>
 *     <li>Calculates deadzones</li>
 *     <li>Does gyroscope calibration</li>
 *     <li>Detects triggers on unmapped joysticks</li>
 * </ul>
 */
public class ControllerCalibrationScreen extends Screen implements DontInteruptScreen {
    private static final int CALIBRATION_TIME = 100;

    protected final Controlify controlify;
    protected final ControllerManager controllerManager;
    protected final Controller<?> controller;
    private final Supplier<Screen> parent;

    private MultiLineLabel waitLabel, infoLabel, completeLabel;

    protected Button readyButton, laterButton;

    protected boolean calibrating = false, calibrated = false;
    protected int calibrationTicks = 0;

    private final Map<ResourceLocation, float[]> axisData;
    private GyroState accumulatedGyroVelocity = new GyroState();

    public ControllerCalibrationScreen(Controller<?> controller, Screen parent) {
        this(controller, () -> parent);
    }

    public ControllerCalibrationScreen(Controller<?> controller, Supplier<Screen> parent) {
        super(Component.translatable("controlify.calibration.title"));
        this.controlify = Controlify.instance();
        this.controllerManager = controlify.getControllerManager().orElseThrow();
        this.controller = controller;
        this.parent = parent;
        this.axisData = new HashMap<>(controller.axisCount());
    }

    @Override
    protected void init() {
        addRenderableWidget(readyButton =
                Button.builder(Component.translatable("controlify.calibration.ready"), btn -> onButtonPress())
                        .width(150)
                        .pos(this.width / 2 - 150 - 5, this.height - 8 - 20)
                        .build()
        );
        addRenderableWidget(laterButton =
                Button.builder(Component.translatable("controlify.calibration.later"), btn -> onLaterButtonPress())
                        .width(150)
                        .pos(this.width / 2 + 5, this.height - 8 - 20)
                        .tooltip(Tooltip.create(Component.translatable("controlify.calibration.later.tooltip")))
                        .build()
        );


        this.infoLabel = MultiLineLabel.create(font, Component.translatable("controlify.calibration.info"), width - 30);
        this.waitLabel = MultiLineLabel.create(font, Component.translatable("controlify.calibration.wait"), width - 30);
        this.completeLabel = MultiLineLabel.create(font, Component.translatable("controlify.calibration.complete"), width - 30);
    }

    protected void startCalibration() {
        calibrating = true;

        readyButton.active = false;
        readyButton.setMessage(Component.translatable("controlify.calibration.calibrating"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);

        super.render(graphics, mouseX, mouseY, delta);

        graphics.drawCenteredString(font, Component.translatable("controlify.calibration.title", controller.name()).withStyle(ChatFormatting.BOLD), width / 2, 8, -1);

        graphics.pose().pushPose();
        graphics.pose().scale(2f, 2f, 1f);

        float progress = (calibrationTicks - 1 + delta) / 100f;
        progress = 1 - (float)Math.pow(1 - progress, 3);
        ClientUtils.drawBar(graphics, width / 2 / 2, 30 / 2, progress);

        graphics.pose().popPose();

        MultiLineLabel label;
        if (calibrating) label = waitLabel;
        else if (calibrated) label = completeLabel;
        else label = infoLabel;

        label.renderCentered(graphics, width / 2, 55);

        graphics.pose().pushPose();
        float scale = Math.min(3f, (readyButton.getY() - (55 + font.lineHeight * label.getLineCount()) - 2) / 64f);
        graphics.pose().translate(width / 2f - 32 * scale, 55 + font.lineHeight * label.getLineCount(), 0f);
        graphics.pose().scale(scale, scale, 1f);
        graphics.blitSprite(controller.type().getIconSprite(), 0, 0, 64, 64);
        graphics.pose().popPose();
    }

    @Override
    public void tick() {
        if (!controllerManager.isControllerConnected(controller.uid())) {
            onClose();
            return;
        }

        if (!calibrating)
            return;

        if (stateChanged()) {
            calibrationTicks = 0;
            axisData.clear();
            accumulatedGyroVelocity = new GyroState();
        }

        if (calibrationTicks < CALIBRATION_TIME) {
            processAxisData(calibrationTicks);
            processGyroData();

            calibrationTicks++;
        } else {
            calibrateAxis();
            generateGyroCalibration();

            calibrating = false;
            calibrated = true;
            readyButton.active = true;
            readyButton.setMessage(Component.translatable("controlify.calibration.done"));

            controller.config().deadzonesCalibrated = true;
            controller.config().delayedCalibration = false;
            // no need to save because of setCurrentController

            Controlify.instance().setCurrentController(controller, true);
        }
    }

    private void processAxisData(int tick) {
        for (ResourceLocation axis : controller.state().getAxes()) {
            float[] axisData = this.axisData.computeIfAbsent(axis, k -> new float[CALIBRATION_TIME]);
            axisData[tick] = controller.state().getAxisState(axis);
        }
    }

    private void processGyroData() {
        if (controller.supportsGyro()) {
            accumulatedGyroVelocity.add(controller.state().getGyroState());
        }
    }

    private void calibrateAxis() {
        controller.config().deadzones.clear();

        for (ResourceLocation axis : controller.state().getAxes()) {
            float[] axisData = this.axisData.get(axis);
            if (axisData == null)
                continue;

            float maxAbs = 0;
            for (int tick = 0; tick < CALIBRATION_TIME; tick++) {
                float axisValue = axisData[tick];
                maxAbs = Math.max(maxAbs, Math.abs(axisValue));
            }

            controller.config().deadzones.put(axis, maxAbs + 0.08f);
        }
    }

    private void generateGyroCalibration() {
        controller.config().gyroCalibration = accumulatedGyroVelocity.div(CALIBRATION_TIME);
    }

    private boolean stateChanged() {
        var amt = 0.4f;

        for (ResourceLocation axis : controller.state().getAxes()) {
            float[] axisData = this.axisData.get(axis);
            if (axisData == null)
                continue;

            float axisValue = controller.state().getAxisState(axis);
            float prevAxisValue = controller.prevState().getAxisState(axis);

            if (Math.abs(axisValue - prevAxisValue) > amt)
                return true;
        }

        return false;
    }

    private void onButtonPress() {
        if (!calibrated) {
            startCalibration();

            removeWidget(laterButton);
            readyButton.setX(this.width / 2 - 75);
        } else
            onClose();
    }

    private void onLaterButtonPress() {
        if (!calibrated) {
            if (!controller.config().deadzonesCalibrated) {
                controller.config().delayedCalibration = true;
                Controlify.instance().config().setDirty();
                Controlify.instance().setCurrentController(null, true);
            }

            onClose();
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent.get());
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
