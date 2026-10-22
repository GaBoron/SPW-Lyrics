package dev.gaboron.spwlyrics.integration.hostui;

import java.util.List;
import static dev.gaboron.spwlyrics.integration.hostui.HostBytecodeIndex.*;

/** Find the player menu's normal builder, leaving host rendering and existing items intact. */
public final class HostPlayerMenuDiscovery {
    private static final String POPUP = "(Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function2;Lkotlin/jvm/functions/Function1;Landroidx/compose/runtime/Composer;II)V";
    private static final String LABEL = "(Ljava/lang/String;Landroidx/compose/ui/Modifier;Lkotlin/jvm/functions/Function2;ZLandroidx/compose/runtime/Composer;II)V";
    public record Targets(Method popup, String caller, String scope, Method label) {}

    public static Targets discover(HostBytecodeIndex index) {
        var owner = unique(index.methods().stream().filter(b -> b.calls().stream().anyMatch(c ->
            c.owner().equals("com/xuncorp/voxzen/service/PlaybackController") && c.name().equals("releaseAudioEngine")))
            .map(b -> b.method().owner()).distinct().toList(), "player menu owner");
        var bodies = index.methods().stream().filter(b -> b.method().owner().equals(owner)).toList();
        var popup = unique(bodies.stream().flatMap(b -> b.calls().stream()).filter(c -> c.descriptor().equals(POPUP))
            .toList(), "player popup call");
        var items = bodies.stream().flatMap(b -> b.calls().stream()).filter(c -> c.type().parameterCount() == 3
            && c.type().parameterType(1).descriptorString().equals("Lkotlin/jvm/functions/Function0;")
            && c.type().parameterType(2).descriptorString().equals("Lkotlin/jvm/functions/Function2;")
            && c.type().parameterType(0).descriptorString().equals("L" + c.owner() + ";")
            && c.type().returnType().descriptorString().equals("V")).distinct().toList();
        var item = unique(items, "player menu builder item");
        var label = unique(bodies.stream().flatMap(b -> b.calls().stream()).filter(c -> c.descriptor().equals(LABEL))
            .distinct().toList(), "player menu label");
        return new Targets(popup, owner, item.owner(), label);
    }

    private static <T> T unique(List<T> choices, String role) {
        if (choices.size() != 1) throw new IllegalStateException(role + ": expected one match, found " + choices.size());
        return choices.getFirst();
    }
}
