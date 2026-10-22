package dev.gaboron.spwlyrics.integration.hostui;

import java.util.List;
import static dev.gaboron.spwlyrics.integration.hostui.HostBytecodeIndex.*;

/** Find the player menu's normal builder, leaving host rendering and existing items intact. */
public final class HostPlayerMenuDiscovery {
    private static final String POPUP = "(Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function2;Lkotlin/jvm/functions/Function1;Landroidx/compose/runtime/Composer;II)V";
    private static final String LABEL = "(Ljava/lang/String;Landroidx/compose/ui/Modifier;Lkotlin/jvm/functions/Function2;ZLandroidx/compose/runtime/Composer;II)V";
    public record Targets(Method builder, String scope, int scopeParameter, int popupParameter, Method dismiss, Method label) {}

    public static Targets discover(HostBytecodeIndex index) {
        var owner = unique(index.methods().stream().filter(b -> b.calls().stream().anyMatch(c ->
            c.owner().equals("com/xuncorp/voxzen/service/PlaybackController") && c.name().equals("releaseAudioEngine")))
            .map(b -> b.method().owner()).distinct().toList(), "player menu owner");
        var bodies = index.methods().stream().filter(b -> b.method().owner().equals(owner)).toList();
        var popup = unique(bodies.stream().flatMap(b -> b.calls().stream()).filter(c -> c.descriptor().equals(POPUP))
            .toList(), "player popup call");
        var entry = unique(bodies.stream().filter(b -> b.calls().contains(popup)).toList(), "player menu entry");
        var popupType = unique(entry.method().type().parameterList().stream().map(t -> t.descriptorString())
            .filter(t -> t.startsWith("L") && !t.equals("Landroidx/compose/runtime/Composer;")).toList(), "player popup state");
        var items = bodies.stream().flatMap(b -> b.calls().stream()).filter(c -> c.type().parameterCount() == 3
            && c.type().parameterType(1).descriptorString().equals("Lkotlin/jvm/functions/Function0;")
            && c.type().parameterType(2).descriptorString().equals("Lkotlin/jvm/functions/Function2;")
            && c.type().parameterType(0).descriptorString().equals("L" + c.owner() + ";")
            && c.type().returnType().descriptorString().equals("V")).distinct().toList();
        var item = unique(items, "player menu builder item");
        var builder = unique(bodies.stream().filter(b -> (b.flags() & 0x0008) != 0 && b.calls().contains(item)
            && b.method().type().returnType().descriptorString().equals("Lkotlin/Unit;")
            && b.method().type().parameterList().stream().noneMatch(t -> t.descriptorString().equals("Landroidx/compose/runtime/Composer;")))
            .map(Body::method).toList(), "ordinary player menu builder");
        var dismiss = unique(bodies.stream().filter(b -> b.method().type().parameterCount() == 1
            && b.method().type().parameterType(0).descriptorString().equals(popupType)
            && b.method().type().returnType().descriptorString().equals("Lkotlin/Unit;"))
            .flatMap(b -> b.calls().stream()).filter(c -> ("L" + c.owner() + ";").equals(popupType)
                && c.descriptor().equals("()V")).distinct().toList(), "player popup dismiss");
        var label = unique(bodies.stream().flatMap(b -> b.calls().stream()).filter(c -> c.descriptor().equals(LABEL))
            .distinct().toList(), "player menu label");
        return new Targets(builder, item.owner(), parameter(builder, "L" + item.owner() + ";"), parameter(builder, popupType), dismiss, label);
    }

    private static int parameter(Method method, String descriptor) {
        var matches = java.util.stream.IntStream.range(0, method.type().parameterCount())
            .filter(i -> method.type().parameterType(i).descriptorString().equals(descriptor)).boxed().toList();
        return unique(matches, "player menu parameter " + descriptor);
    }

    private static <T> T unique(List<T> choices, String role) {
        if (choices.size() != 1) throw new IllegalStateException(role + ": expected one match, found " + choices.size());
        return choices.getFirst();
    }
}
