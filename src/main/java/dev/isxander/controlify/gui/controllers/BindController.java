package dev.isxander.controlify.gui.controllers;

import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.bindings.ControlifyBindings;
import dev.isxander.controlify.bindings.input.*;
import dev.isxander.controlify.controller.*;
import dev.isxander.controlify.controller.input.ControllerStateView;
import dev.isxander.controlify.controller.input.HatState;
import dev.isxander.controlify.controller.input.InputComponent;
import dev.isxander.controlify.gui.screen.BindConsumerScreen;
import dev.isxander.controlify.screenop.ComponentProcessor;
import dev.isxander.controlify.screenop.ScreenProcessor;
import dev.isxander.yacl3.api.Controller;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.ControllerWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

public class BindController implements Controller<Input> {
    private final Option<Input> option;
    public final ControllerEntity controller;
    private boolean conflicting;

    public BindController(Option<Input> option, ControllerEntity controller) {
        this.option = option;
        this.controller = controller;
    }

    @Override
    public Option<Input> option() {
        return this.option;
    }

    @Override
    public Component formatValue() {
        return Component.empty();
    }

    public void setConflicting(boolean conflicting) {
        this.conflicting = conflicting;
    }

    public boolean getConflicting() {
        return this.conflicting;
    }

    @Override
    public BindControllerElement provideWidget(YACLScreen yaclScreen, Dimension<Integer> dimension) {
        return new BindControllerElement(this, yaclScreen, dimension);
    }

    public static class BindControllerElement extends ControllerWidget<BindController> implements ComponentProcessor {
        public boolean awaitingControllerInput = false;
        private final Component awaitingText = Component.translatable("controlify.gui.bind_input_awaiting").withStyle(ChatFormatting.ITALIC);

        public BindControllerElement(BindController control, YACLScreen screen, Dimension<Integer> dim) {
            super(control, screen, dim);
        }

        @Override
        protected void drawValueText(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            if (awaitingControllerInput) {
                graphics.drawString(textRenderer, awaitingText, getDimension().xLimit() - textRenderer.width(awaitingText) - getXPadding(), (int)(getDimension().centerY() - textRenderer.lineHeight / 2f), 0xFFFFFF, true);
            } else {
                var bind = control.option().pendingValue();
                if (EmptyInput.equals(bind)) return;

                Component text = Controlify.instance().inputFontMapper()
                        .getComponentFromBind(control.controller.info().type().namespace(), bind);
                int width = textRenderer.width(text);

                graphics.drawString(textRenderer, text, getDimension().xLimit() - width - 1, (int)(getDimension().centerY() - textRenderer.lineHeight / 2f + 1), -1, false);
            }
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (isFocused() && keyCode == GLFW.GLFW_KEY_ENTER) {
                openConsumerScreen();
                return true;
            }

            return false;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (getDimension().isPointInside((int)mouseX, (int)mouseY)) {
                openConsumerScreen();
                return true;
            }

            return false;
        }

        private void openConsumerScreen() {
            awaitingControllerInput = true;
            Minecraft.getInstance().setScreen(new BindConsumerScreen(this::getPressedBind, control.option(), this, Minecraft.getInstance().screen));
        }

        @Override
        public boolean overrideControllerButtons(ScreenProcessor<?> screen, ControllerEntity controller) {
            if (controller != control.controller) return true;

            if (ControlifyBindings.GUI_PRESS.on(controller).justPressed()) {
                openConsumerScreen();
                return true;
            }

            return false;
        }

        @Override
        protected int getHoveredControlWidth() {
            return getUnhoveredControlWidth();
        }

        @Override
        protected int getUnhoveredControlWidth() {
            if (awaitingControllerInput)
                return textRenderer.width(awaitingText);

            Component text = Controlify.instance().inputFontMapper()
                    .getComponentFromBind(control.controller.info().type().namespace(), control.option().pendingValue());
            return textRenderer.width(text);
        }

        @Override
        protected int getValueColor() {
            return control.conflicting ? 0xFF5555 : super.getValueColor();
        }

        public Optional<Input> getPressedBind() {
            InputComponent input = control.controller.input().orElseThrow();
            ControllerStateView state = input.stateNow();
            ControllerStateView prevState = input.stateThen();

            for (ResourceLocation button : state.getButtons()) {
                if (state.isButtonDown(button) && !prevState.isButtonDown(button)) {
                    return Optional.of(new ButtonInput(button));
                }
            }

            for (ResourceLocation axis : state.getAxes()) {
                if (state.getAxisState(axis) > 0.5f && prevState.getAxisState(axis) <= 0.5f) {
                    return Optional.of(new AxisInput(axis));
                }
            }

            for (ResourceLocation hat : state.getHats()) {
                HatState hatState = state.getHatState(hat);
                if (hatState != HatState.CENTERED && prevState.getHatState(hat) == HatState.CENTERED) {
                    return Optional.of(new HatInput(hat, hatState));
                }
            }

            return Optional.empty();
        }
    }
}
