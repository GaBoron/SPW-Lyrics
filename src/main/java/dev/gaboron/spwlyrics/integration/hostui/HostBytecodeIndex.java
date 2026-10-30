package dev.gaboron.spwlyrics.integration.hostui;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.MethodTypeDesc;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
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

    public static HostBytecodeIndex read(Class<?> host) throws IOException {
        return read(source(host));
    }

    public static Path source(Class<?> host) throws IOException {
        var domain = host.getProtectionDomain();
        var codeSource = domain == null ? null : domain.getCodeSource();
        var location = codeSource == null ? null : codeSource.getLocation();
        var name = host.getName().replace('.', '/') + ".class";
        try {
            if (location != null && location.getProtocol().equals("file")) return Path.of(location.toURI());
            // Class resources remain usable when the host omits its CodeSource location.
            var resource = host.getResource("/" + name);
            if (resource != null && resource.getProtocol().equals("jar")) {
                var archive = ((JarURLConnection) resource.openConnection()).getJarFileURL();
                if (archive.getProtocol().equals("file")) return Path.of(archive.toURI());
            }
            if (resource != null && resource.getProtocol().equals("file")) {
                var root = Path.of(resource.toURI());
                for (var segment : name.split("/")) {
                    root = root.getParent();
                    if (root == null) break;
                }
                if (root != null) return root;
            }
            throw new IOException("无法定位宿主代码来源：" + host.getName() + " (source=" +
                (location == null ? "none" : location.getProtocol()) + ", resource=" +
                (resource == null ? "none" : resource.getProtocol()) + ")");
        } catch (URISyntaxException error) {
            throw new IOException("宿主代码来源 URL 无法转换为本地路径：" + host.getName(), error);
        }
    }

    public static HostBytecodeIndex read(Path source) throws IOException {
        var bodies = new ArrayList<Body>();
        var types = new ArrayList<Type>();
        if (Files.isDirectory(source)) {
            try (var entries = Files.walk(source)) {
                var paths = entries.filter(p -> p.toString().endsWith(".class")).iterator();
                while (paths.hasNext()) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Host UI discovery cancelled");
                    var path = paths.next();
                    parse(Files.readAllBytes(path), bodies, types);
                }
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
        if (!pool.contains("androidx/compose/runtime/Composer") && !pool.contains("androidx/navigation3/runtime/NavBackStack")
            && !pool.contains("androidx/compose/runtime/MutableState")
            && !pool.contains("androidx/compose/runtime/ProvidableCompositionLocal")) return;
        var model = ClassFile.of().parse(bytes);
        var owner = model.thisClass().asInternalName();
        var ownFields = model.fields().stream().map(f -> new Field(owner, f.fieldName().stringValue(), f.fieldType().stringValue())).toList();
        types.add(new Type(owner, ownFields));
        for (var method : model.methods()) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Host UI discovery cancelled");
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
