package dev.gaboron.spwlyrics.integration.hostui;

import java.lang.reflect.Modifier;
import java.util.List;
import static dev.gaboron.spwlyrics.integration.hostui.HostBytecodeIndex.*;

/** Reuses the material chosen by the host's popup renderer, including its acrylic setting. */
public final class HostPopupMaterialDiscovery {
    public record Targets(Method modifier, Field material) {}

    public static Targets discover(HostBytecodeIndex index) {
        var modifier = unique(index.methods().stream().filter(b -> Modifier.isStatic(b.flags())
            && b.method().type().parameterCount() == 4
            && b.method().type().parameterType(0).descriptorString().equals("Landroidx/compose/ui/Modifier;")
            && b.method().type().parameterType(2).descriptorString().equals("Landroidx/compose/runtime/Composer;")
            && b.method().type().returnType().descriptorString().equals("Landroidx/compose/ui/Modifier;")
            && b.calls().stream().anyMatch(c -> c.owner().equals("dev/chrisbanes/haze/blur/HazeBlurKt")))
            .toList(), "host multilayer material");
        var popup = unique(index.methods().stream().filter(b -> b.calls().contains(modifier.method())
            && b.fields().stream().anyMatch(f -> f.owner().equals("androidx/compose/foundation/layout/IntrinsicSize")
                && f.name().equals("Max"))).toList(), "host popup material renderer");
        var descriptor = modifier.method().type().parameterType(1).descriptorString();
        var material = unique(popup.fields().stream().filter(f -> f.descriptor().equals(descriptor)
            && descriptor.equals("L" + f.owner() + ";")).distinct().toList(), "host popup material value");
        return new Targets(modifier.method(), material);
    }

    private static <T> T unique(List<T> choices, String role) {
        if (choices.size() != 1) throw new IllegalStateException(role + ": expected one match, found " + choices.size());
        return choices.getFirst();
    }
}
