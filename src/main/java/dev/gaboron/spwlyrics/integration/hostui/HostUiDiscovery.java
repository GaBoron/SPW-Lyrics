package dev.gaboron.spwlyrics.integration.hostui;

import java.lang.reflect.Modifier;
import java.util.*;
import static dev.gaboron.spwlyrics.integration.hostui.HostBytecodeIndex.*;

/** Structural queries. An ambiguous query is an error, never a best-effort choice. */
public final class HostUiDiscovery {
    private static final String COMPOSE = "(Landroidx/compose/runtime/Composer;I)V";
    private static final String STACK = "Landroidx/navigation3/runtime/NavBackStack;";
    private static final String CAPTION = "(Lkotlin/jvm/functions/Function0;Ljava/lang/String;Landroidx/compose/ui/Modifier;ZLjava/lang/String;FLkotlin/jvm/functions/Function3;Landroidx/compose/runtime/Composer;II)V";
    public record Site(Method target, String caller) {}
    public record Targets(Site page, Site captionEntry, Method captionControl, Method back,
                          Method navigate, Method collapsePlayer, List<Method> navigation, Field backStack, List<Field> locals,
                          HostThemeDiscovery.Targets theme) {}

    public static Targets discover(HostBytecodeIndex index) {
        var bodies = index.methods().stream().toList();
        var content = unique(bodies.stream().filter(b -> b.method().descriptor().equals(COMPOSE)
            && b.calls().stream().anyMatch(c -> c.owner().startsWith("androidx/navigation3/ui/")
                && c.descriptor().contains("Landroidx/compose/runtime/Composer;"))).toList(), "navigation content");
        var root = unique(bodies.stream().filter(b -> b.method().owner().equals(content.method().owner())
            && b.method().descriptor().equals(COMPOSE) && b.calls().stream().anyMatch(c ->
                c.owner().equals("androidx/compose/animation/SharedTransitionScopeKt"))).toList(), "navigation root");
        var page = site(index, root.method());
        var caption = unique(bodies.stream().filter(b -> b.method().descriptor().equals(CAPTION)
            && Modifier.isStatic(b.flags()) && b.calls().stream().anyMatch(c ->
                c.descriptor().contains("Lkotlin/jvm/functions/Function0;Ljava/lang/String;Landroidx/compose/ui/Modifier;ZLjava/lang/String;Lkotlin/jvm/functions/Function3;"))).toList(), "caption control");
        var entry = unique(bodies.stream().filter(b -> b.method().descriptor().equals(COMPOSE)
            && b.calls().contains(caption.method())).toList(), "caption entry");
        var captionSite = site(index, entry.method());

        var stackRead = unique(content.calls().stream().filter(c -> c.descriptor().equals("()" + STACK)).distinct().toList(), "navigation stack getter");
        var navClass = index.type(stackRead.owner());
        if (navClass == null) throw new IllegalStateException("Navigation controller is not indexed");
        var stack = unique(navClass.fields().stream().filter(f -> f.descriptor().equals(STACK)).toList(), "navigation stack");
        var operations = bodies.stream().filter(b -> b.method().owner().equals(navClass.name())
            && !Modifier.isStatic(b.flags()) && Modifier.isPublic(b.flags())
            && !b.method().name().startsWith("<") && b.method().type().returnType().descriptorString().equals("V")
            && b.fields().contains(stack)).toList();
        var back = unique(operations.stream().filter(b -> b.method().descriptor().equals("()V")
            && b.calls().stream().anyMatch(c -> c.owner().equals("kotlin/collections/CollectionsKt") && c.name().equals("removeLastOrNull"))
            && b.calls().stream().noneMatch(c -> c.owner().equals("androidx/navigation3/runtime/NavBackStack") && c.name().equals("add"))).toList(), "back action");
        var navigate = unique(operations.stream().filter(b -> b.method().type().parameterCount() == 1
            && b.calls().stream().anyMatch(c -> c.owner().equals("kotlin/jvm/internal/Intrinsics") && c.name().equals("areEqual"))
            && b.calls().stream().anyMatch(c -> c.owner().equals("androidx/navigation3/runtime/NavBackStack") && c.name().equals("add"))).toList(), "navigate action");
        var route = navigate.method().descriptor();
        var navigation = operations.stream().filter(b -> b.method().descriptor().equals(route)
            || b.method().descriptor().equals("()V") && b.calls().stream().anyMatch(c ->
                c.owner().equals("androidx/navigation3/runtime/NavBackStack") && c.name().equals("add")))
            .map(Body::method).toList();
        var collapse = unique(bodies.stream().filter(b -> b.method().owner().equals(navClass.name())
            && Modifier.isPublic(b.flags()) && !Modifier.isStatic(b.flags()) && b.method().descriptor().equals("()V")
            && !b.fields().contains(stack)
            && b.fields().stream().anyMatch(f -> f.owner().equals(navClass.name() + "$PlayerSheetRequest") && f.name().equals("Collapse"))
            && b.calls().stream().anyMatch(c -> c.owner().equals("kotlinx/coroutines/channels/Channel") && c.name().startsWith("trySend")))
            .map(Body::method).toList(), "player collapse action");
        var locals = content.fields().stream().filter(f -> f.owner().equals(root.method().owner())
            && f.descriptor().equals("Landroidx/compose/runtime/ProvidableCompositionLocal;")).distinct().toList();
        if (locals.isEmpty()) throw new IllegalStateException("Navigation composition locals are missing");
        return new Targets(page, captionSite, caption.method(), back.method(), navigate.method(), collapse, navigation, stack, locals,
            HostThemeDiscovery.discover(index, entry.method().owner()));
    }

    private static Site site(HostBytecodeIndex index, Method target) {
        // A compiler restart callback calls its own owner; that is not a host insertion point.
        var callers = index.methods().stream().filter(b -> !b.method().owner().equals(target.owner())
            && b.calls().contains(target) && b.method().descriptor().contains("Landroidx/compose/runtime/Composer;"))
            .map(b -> b.method().owner()).distinct().toList();
        var caller = unique(callers, "UI caller");
        long count = index.methods().stream().filter(b -> b.method().owner().equals(caller))
            .flatMap(b -> b.calls().stream()).filter(target::equals).count();
        if (count != 1) throw new IllegalStateException("UI call site is not unique: " + count);
        return new Site(target, caller);
    }

    private static <T> T unique(List<T> candidates, String role) {
        if (candidates.size() != 1) throw new IllegalStateException(role + ": expected one match, found " + candidates.size());
        return candidates.getFirst();
    }
}
