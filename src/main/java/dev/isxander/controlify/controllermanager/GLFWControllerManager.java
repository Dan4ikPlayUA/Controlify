package dev.isxander.controlify.controllermanager;

import com.google.common.io.ByteStreams;
import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.controller.Controller;
import dev.isxander.controlify.controller.composable.ComposableController;
import dev.isxander.controlify.controller.composable.gamepad.GamepadConfig;
import dev.isxander.controlify.controller.composable.impl.*;
import dev.isxander.controlify.controller.composable.joystick.JoystickConfig;
import dev.isxander.controlify.debug.DebugProperties;
import dev.isxander.controlify.driver.gamepad.GLFWGamepadDriver;
import dev.isxander.controlify.driver.gamepad.SDL3GamepadDriver;
import dev.isxander.controlify.driver.joystick.GLFWJoystickDriver;
import dev.isxander.controlify.driver.joystick.SDL3JoystickDriver;
import dev.isxander.controlify.hid.ControllerHIDService;
import dev.isxander.controlify.utils.CUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

public class GLFWControllerManager extends AbstractControllerManager {

    public GLFWControllerManager() {
        Minecraft.getInstance().getResourceManager()
                .getResource(Controlify.id("controllers/gamecontrollerdb.txt"))
                .ifPresent(this::loadGamepadMappings);

        this.setupCallbacks();
    }

    private void setupCallbacks() {
        GLFW.glfwSetJoystickCallback((jid, event) -> {
            try {
                GLFWUniqueControllerID ucid = new GLFWUniqueControllerID(jid);
                if (event == GLFW.GLFW_CONNECTED) {
                    createOrGet(ucid, controlify.controllerHIDService().fetchType(jid))
                            .ifPresent(controller -> onControllerConnected(controller, true));
                } else if (event == GLFW.GLFW_DISCONNECTED) {
                    getController(ucid).ifPresent(this::onControllerRemoved);
                }
            } catch (Throwable e) {
                CUtil.LOGGER.error("Failed to handle controller connect/disconnect event", e);
            }
        });
    }

    @Override
    public void discoverControllers() {
        for (int i = 0; i < GLFW.GLFW_JOYSTICK_LAST; i++) {
            if (!GLFW.glfwJoystickPresent(i))
                continue;

            UniqueControllerID ucid = new GLFWUniqueControllerID(i);

            Optional<Controller<?>> controllerOpt = createOrGet(ucid, controlify.controllerHIDService().fetchType(i));
            controllerOpt.ifPresent(controller -> onControllerConnected(controller, false));
        }
    }

    @Override
    protected Optional<Controller<?>> createController(UniqueControllerID ucid, ControllerHIDService.ControllerHIDInfo hidInfo) {
        int jid = ((GLFWUniqueControllerID) ucid).jid;

        boolean isGamepad = isControllerGamepad(ucid) && !DebugProperties.FORCE_JOYSTICK;
        if (isGamepad) {
            GLFWGamepadDriver driver = new GLFWGamepadDriver(jid);

            var deadzoneModifier = new DeadzoneControllerStateModifier();
            var stateProvider = new ComposableControllerStateProviderImpl(driver, deadzoneModifier);
            var configModule = new ComposableControllerConfigImpl<>(new GamepadConfig());
            var infoModule = new ComposableControllerInfoImpl(
                    hidInfo.createControllerUID().orElse("unknown-uid-" + jid),
                    ucid,
                    driver,
                    driver
            );

            return Optional.of(new ComposableController<>(
                    infoModule,
                    stateProvider,
                    configModule,
                    new NotRumbleCapableImpl(), // TODO
                    hidInfo.type(),
                    Set.of(driver)
            ));
        } else {
            GLFWJoystickDriver driver = new GLFWJoystickDriver(jid);

            var deadzoneModifier = new DeadzoneControllerStateModifier();
            var stateProvider = new ComposableControllerStateProviderImpl(driver, deadzoneModifier);
            var configModule = new ComposableControllerConfigImpl<>(new JoystickConfig());
            var infoModule = new ComposableControllerInfoImpl(
                    hidInfo.createControllerUID().orElse("unknown-uid-" + jid),
                    ucid,
                    driver,
                    driver
            );

            return Optional.of(new ComposableController<>(
                    infoModule,
                    stateProvider,
                    configModule,
                    new NotRumbleCapableImpl(), // TODO
                    hidInfo.type(),
                    Set.of(driver)
            ));
        }
    }

    @Override
    public boolean probeConnectedControllers() {
        return areControllersConnected();
    }

    @Override
    protected void loadGamepadMappings(Resource resource) {
        CUtil.LOGGER.debug("Loading gamepad mappings...");

        try (InputStream is = resource.open()) {
            byte[] bytes = ByteStreams.toByteArray(is);
            ByteBuffer buffer = MemoryUtil.memASCIISafe(new String(bytes));

            if (!GLFW.glfwUpdateGamepadMappings(buffer)) {
                CUtil.LOGGER.error("Failed to load gamepad mappings: {}", GLFW.glfwGetError(null));
            }
        } catch (Throwable e) {
            CUtil.LOGGER.error("Failed to load gamepad mappings: {}", e.getMessage());
        }
    }

    private Optional<Controller<?>> getController(GLFWUniqueControllerID joystickId) {
        return controllersByUid.values().stream().filter(controller -> controller.joystickId().equals(joystickId)).findAny();
    }

    @Override
    public boolean isControllerGamepad(UniqueControllerID ucid) {
        int joystickId = ((GLFWUniqueControllerID) ucid).jid;
        return GLFW.glfwJoystickIsGamepad(joystickId);
    }

    @Override
    protected String getControllerSystemName(UniqueControllerID ucid) {
        int joystickId = ((GLFWUniqueControllerID) ucid).jid;
        return isControllerGamepad(ucid) ? GLFW.glfwGetGamepadName(joystickId) : GLFW.glfwGetJoystickName(joystickId);
    }

    public static boolean areControllersConnected() {
        return IntStream.range(0, GLFW.GLFW_JOYSTICK_LAST + 1)
                .anyMatch(GLFW::glfwJoystickPresent);
    }

    public record GLFWUniqueControllerID(int jid) implements UniqueControllerID {
    }
}
