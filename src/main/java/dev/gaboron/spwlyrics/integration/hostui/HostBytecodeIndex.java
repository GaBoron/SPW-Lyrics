package dev.gaboron.spwlyrics.integration.hostui;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.MethodTypeDesc;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

/** Reads host bytes without initializing host classes. No names of host UI methods are assumed. */
public final class HostBytecodeIndex {
    public record Method(String owner, String name, String descriptor) {
        public MethodTypeDesc type() { return MethodTypeDesc.ofDescriptor(descriptor); }
    }
    public record Field(String owner, String name, String descriptor) {}
    public record Body(Method method, int flags, List<Method> calls, List<Field> fields) {}
    public record Type(String name, List<Field> fields) {}

    private final Map<Method, Body> methods;
    private final Map<String, Type> types;

    public HostBytecodeIndex(Collection<Body> bodies, Collection<Type> classes) {
        var m = new LinkedHashMap<Method, Body>();
        for (var body : bodies) m.put(body.method, body);
        methods = Collections.unmodifiableMap(m);
        var t = new LinkedHashMap<String, Type>();
        for (var type : classes) t.put(type.name, type);
        types = Collections.unmodifiableMap(t);
    }
    public Collection<Body> methods() { return methods.values(); }
    public Type type(String name) { return types.get(name); }
    public Body body(Method method) { return methods.get(method); }

    public static HostBytecodeIndex read(Path source) throws IOException {
        var bodies = new ArrayList<Body>();
        var types = new ArrayList<Type>();
        if (Files.isDirectory(source)) {
            try (var entries = Files.walk(source)) {
                for (var path : entries.filter(p -> p.toString().endsWith(".class")).toList())
                    parse(Files.readAllBytes(path), bodies, types);
            }
        } else try (var jar = new JarFile(source.toFile())) {
            for (var entry : Collections.list(jar.entries())) {
                if (!entry.getName().endsWith(".class")) continue;
                if (Thread.currentThread().isInterrupted()) throw new IOException("Host UI discovery cancelled");
                try (var input = jar.getInputStream(entry)) { parse(input.readAllBytes(), bodies, types); }
            }
        }
        return new HostBytecodeIndex(bodies, types);
    }

    private static void parse(byte[] bytes, List<Body> bodies, List<Type> types) {
        // Restrict indexing to UI/navigation code, including host classes relocated into Compose.
        var pool = new String(bytes, StandardCharsets.ISO_8859_1);
        if (!pool.contains("androidx/compose/runtime/Composer") && !pool.contains("androidx/navigation3/runtime/NavBackStack")) return;
        var model = ClassFile.of().parse(bytes);
        var owner = model.thisClass().asInternalName();
        var ownFields = model.fields().stream().map(f -> new Field(owner, f.fieldName().stringValue(), f.fieldType().stringValue())).toList();
        types.add(new Type(owner, ownFields));
        for (var method : model.methods()) {
            if (method.code().isEmpty()) continue;
            var calls = new ArrayList<Method>();
            var fields = new ArrayList<Field>();
            for (var element : method.code().get()) {
                if (element instanceof InvokeInstruction call)
                    calls.add(new Method(call.owner().asInternalName(), call.name().stringValue(), call.type().stringValue()));
                if (element instanceof FieldInstruction field)
                    fields.add(new Field(field.owner().asInternalName(), field.name().stringValue(), field.type().stringValue()));
            }
            bodies.add(new Body(new Method(owner, method.methodName().stringValue(), method.methodType().stringValue()),
                method.flags().flagsMask(), List.copyOf(calls), List.copyOf(fields)));
        }
    }
}
