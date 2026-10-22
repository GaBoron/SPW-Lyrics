package dev.gaboron.spwlyrics.integration.hostui;

import java.lang.reflect.Modifier;
import java.util.*;
import static dev.gaboron.spwlyrics.integration.hostui.HostBytecodeIndex.*;

/** Follow the native caption's palette selection to its reactive dark-mode property. */
public final class HostThemeDiscovery {
    public record Targets(Method configs, Method dark) {}
    private static final String READ = "(Landroidx/compose/runtime/Composer;)L";

    public static Targets discover(HostBytecodeIndex index, String captionOwner) {
        var accessors = index.methods().stream().filter(b -> b.method().owner().equals(captionOwner))
            .flatMap(b -> b.calls().stream()).distinct()
            .filter(m -> m.descriptor().startsWith(READ) && isPalette(index, m)).toList();
        var palette = index.body(unique(accessors, "caption theme palette"));
        var configs = palette.calls().stream().distinct().filter(m -> m.descriptor().startsWith(READ))
            .filter(m -> index.body(m) != null && Modifier.isStatic(index.body(m).flags()))
            .filter(m -> index.body(m).calls().stream().anyMatch(c -> c.owner().equals("androidx/compose/runtime/Composer")
                && c.name().equals("consume"))).toList();
        var matches = new ArrayList<Targets>();
        for (var config : configs) {
            var owner = objectType(config);
            for (var dark : palette.calls().stream().distinct().filter(m -> m.owner().equals(owner)
                && m.descriptor().equals("()Z")).toList()) {
                var body = index.body(dark);
                if (body != null && !Modifier.isStatic(body.flags())
                    && body.fields().stream().anyMatch(f -> f.descriptor().equals("Landroidx/compose/runtime/MutableState;"))
                    && body.calls().stream().anyMatch(c -> c.owner().equals("java/lang/Boolean") && c.name().equals("booleanValue")))
                    matches.add(new Targets(config, dark));
            }
        }
        return unique(matches, "host theme state");
    }

    private static boolean isPalette(HostBytecodeIndex index, Method method) {
        var body = index.body(method);
        if (body == null || !Modifier.isStatic(body.flags())) return false;
        var owner = objectType(method);
        long colors = index.methods().stream().filter(b -> b.method().owner().equals(owner)
            && b.method().descriptor().equals("()J") && b.calls().stream().anyMatch(c ->
                c.owner().equals("androidx/compose/ui/graphics/Color") && c.descriptor().equals("()J"))).count();
        // A palette has several Color properties and separate light/dark instances of the same type.
        return colors >= 3 && body.calls().stream().filter(c -> c.descriptor().equals("()L" + owner + ";"))
            .distinct().count() == 2;
    }

    private static String objectType(Method method) {
        var type = method.type().returnType().descriptorString();
        return type.substring(1, type.length() - 1);
    }

    private static <T> T unique(List<T> candidates, String role) {
        if (candidates.size() != 1) throw new IllegalStateException(role + ": expected one match, found " + candidates.size());
        return candidates.getFirst();
    }
}
