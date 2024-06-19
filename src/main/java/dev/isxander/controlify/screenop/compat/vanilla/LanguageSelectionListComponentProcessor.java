package dev.isxander.controlify.screenop.compat.vanilla;

import dev.isxander.controlify.bindings.ControlifyBindings;
import dev.isxander.controlify.controller.ControllerEntity;
import dev.isxander.controlify.screenop.ScreenProcessor;
import dev.isxander.controlify.screenop.ComponentProcessor;
import dev.isxander.controlify.mixins.feature.screenop.vanilla.OptionsSubScreenAccessor;
import net.minecraft.client.Minecraft;

public class LanguageSelectionListComponentProcessor implements ComponentProcessor {
    private final String code;

    public LanguageSelectionListComponentProcessor(String code) {
        this.code = code;
    }

    @Override
    public boolean overrideControllerButtons(ScreenProcessor<?> screen, ControllerEntity controller) {
        if (ControlifyBindings.GUI_PRESS.on(controller).justPressed()) {
            var minecraft = Minecraft.getInstance();
            var languageManager = minecraft.getLanguageManager();
            if (!code.equals(languageManager.getSelected())) {
                languageManager.setSelected(code);
                minecraft.options.languageCode = code;
                minecraft.reloadResourcePacks();
                minecraft.options.save();
            }

            minecraft.setScreen(((OptionsSubScreenAccessor) screen.screen).getLastScreen());

            return true;
        }

        return false;
    }
}
