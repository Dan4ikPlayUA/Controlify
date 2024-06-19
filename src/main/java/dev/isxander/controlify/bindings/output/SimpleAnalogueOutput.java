package dev.isxander.controlify.bindings.output;

import dev.isxander.controlify.api.bind.InputBinding;
import dev.isxander.controlify.bindings.StateAccess;

public class SimpleAnalogueOutput implements AnalogueOutput {
    private final StateAccess stateAccess;
    private final int history;

    public SimpleAnalogueOutput(InputBinding binding, int history) {
        this.stateAccess = binding.createStateAccess(history + 1);
        this.history = history;
    }

    @Override
    public float get() {
        if (stateAccess.isSuppressed())
            return 0f;

        return stateAccess.analogue(history);
    }
}
