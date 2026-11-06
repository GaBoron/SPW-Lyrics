import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar

// Private provisioning stays outside the checkout and is never a cacheable task input.
val credentialFile = providers.gradleProperty("lrcShareCredentialFile")
    .map { file(it) }
    .orElse(File(gradle.gradleUserHomeDir, "spw-lyrics/lrcshare-access.properties"))
val generated = layout.buildDirectory.dir("generated/lrcshare/java")
val intermediate = layout.buildDirectory.dir("intermediates/lrcshare-access")
val mainSources = extensions.getByType<SourceSetContainer>().named("main")
mainSources.configure { java.srcDir(generated) }

val generateAccess = tasks.register("generateLrcShareAccess") {
    outputs.dir(generated)
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    notCompatibleWithConfigurationCache("Reads private credential provisioning only during execution")
    doLast {
        val random = SecureRandom()
        val sensitiveBuffers = mutableListOf<ByteArray>()
        fun randomBytes(size: Int) = ByteArray(size).also(random::nextBytes).also(sensitiveBuffers::add)
        val directory = generated.get().asFile.resolve("dev/gaboron/spwlyrics/provider/credential").apply { mkdirs() }
        val provisioning = credentialFile.get()
        val provisioningPath = if (provisioning.exists()) {
            provisioning.toPath().toRealPath()
        } else {
            provisioning.toPath().toAbsolutePath().normalize()
        }
        require(!provisioningPath.startsWith(rootProject.projectDir.toPath().toRealPath())) {
            "LrcShare credential provisioning must stay outside the repository"
        }
        val plain = if (provisioning.isFile) {
            val properties = Properties().apply { provisioning.inputStream().use(::load) }
            val decoder = Base64.getDecoder()
            val wrapping = decoder.decode(properties.getProperty("encryptionKey") ?: error("Missing credential wrapping key"))
            val payload = decoder.decode(properties.getProperty("encryptedAccess") ?: error("Missing encrypted credential"))
            try {
                Cipher.getInstance("AES/GCM/NoPadding").run {
                    init(Cipher.DECRYPT_MODE, SecretKeySpec(wrapping, "AES"), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
                    doFinal(payload, 12, payload.size - 12)
                }
            } finally {
                wrapping.fill(0)
                payload.fill(0)
            }
        } else ByteArray(0)
        try {
            val identity = MessageDigest.getInstance("SHA-256").digest(plain).joinToString("") { "%02x".format(it) }
            val key = randomBytes(32)
            val nonce = randomBytes(12)
            val aad = "spw-lyrics|api.lrcshare.com|v1".toByteArray(Charsets.UTF_8)
            val encrypted = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
                updateAAD(aad)
                doFinal(plain)
            }.also(sensitiveBuffers::add)
            val material = (key + nonce + encrypted).also(sensitiveBuffers::add)
            val size = material.size
            val scattered = randomBytes(512)
            require(size * 2 <= scattered.size) { "Credential exceeds the supported size" }
            val mask = randomBytes(size)
            for (index in material.indices) {
                scattered[index] = (material[index].toInt() xor mask[index].toInt()).toByte()
                scattered[index + size] = mask[index]
            }
            // Per-build masking/scattering obscures static constants; AES-GCM authenticates the payload.
            val positions = (scattered.indices).shuffled(random).chunked(128)
            positions.forEachIndexed { shard, slots ->
                val salt = random.nextInt(65536)
                val words = slots.map { slot ->
                    val noise = random.nextInt(256)
                    ((slot xor salt) shl 16) or (((scattered[slot].toInt() and 255) xor noise) shl 8) or noise
                }.joinToString(", ")
                directory.resolve("Shard$shard.java").writeText("""
                    package dev.gaboron.spwlyrics.provider.credential;
                    import java.util.Arrays;
                    final class Shard$shard {
                        static void fill(byte[] output) {
                            int[] words = new int[] { $words };
                            try {
                                for (int word : words) output[(word >>> 16) ^ $salt] = (byte) ((word >>> 8) ^ word);
                            } finally { Arrays.fill(words, 0); }
                        }
                    }
                """.trimIndent())
            }
            directory.resolve("GeneratedCredential.java").writeText("""
                package dev.gaboron.spwlyrics.provider.credential;
                import java.util.Arrays;
                import java.util.LinkedHashMap;
                import java.nio.charset.StandardCharsets;
                import dev.gaboron.spwlyrics.provider.ProviderHttpClient;
                import javax.crypto.Cipher;
                import javax.crypto.spec.GCMParameterSpec;
                import javax.crypto.spec.SecretKeySpec;
                public final class GeneratedCredential {
                    private GeneratedCredential() {}
                    public static boolean available() { return ${plain.isNotEmpty()}; }
                    public static String identity() { return "$identity"; }
                    public static String request(ProviderHttpClient http, String path, String session) {
                        if (!available()) throw new IllegalStateException("LrcShare credential unavailable");
                        byte[] scattered = new byte[512];
                        byte[] material = new byte[$size];
                        byte[] key = null;
                        byte[] nonce = null;
                        byte[] credential = null;
                        var headers = new LinkedHashMap<String, String>();
                        try {
                            try {
                                Shard0.fill(scattered);
                                Shard1.fill(scattered);
                                Shard2.fill(scattered);
                                Shard3.fill(scattered);
                                for (int i = 0; i < material.length; i++) material[i] = (byte) (scattered[i] ^ scattered[i + $size]);
                                key = Arrays.copyOfRange(material, 0, 32);
                                nonce = Arrays.copyOfRange(material, 32, 44);
                                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
                                cipher.updateAAD("spw-lyrics|api.lrcshare.com|v1".getBytes(StandardCharsets.UTF_8));
                                credential = cipher.doFinal(material, 44, material.length - 44);
                                headers.put("Accept", "application/json");
                                headers.put("X-API-Key", new String(credential, StandardCharsets.UTF_8));
                            } catch (java.security.GeneralSecurityException error) {
                                throw new IllegalStateException("LrcShare credential unavailable", error);
                            } finally {
                                Arrays.fill(scattered, (byte) 0);
                                Arrays.fill(material, (byte) 0);
                                if (key != null) Arrays.fill(key, (byte) 0);
                                if (nonce != null) Arrays.fill(nonce, (byte) 0);
                                if (credential != null) Arrays.fill(credential, (byte) 0);
                            }
                            if (session != null) headers.put("X-LrcShare-Session", session);
                            return http.getAuthenticated("https://api.lrcshare.com/v1" + path, headers);
                        } finally {
                            headers.clear();
                        }
                    }
                }
            """.trimIndent())
        } finally {
            plain.fill(0)
            sensitiveBuffers.forEach { it.fill(0) }
        }
    }
}

tasks.named("compileKotlin") { dependsOn(generateAccess) }
tasks.named("compileJava") { dependsOn(generateAccess) }

val obfuscator = configurations.create("lrcShareObfuscator")
dependencies.add(obfuscator.name, "com.guardsquare:proguard-base:7.10.0")
val inputJar = tasks.register<Jar>("collectLrcShareAccess") {
    dependsOn(tasks.named("classes"))
    destinationDirectory.set(intermediate)
    archiveFileName.set("input.jar")
    from(mainSources.map { it.output.classesDirs }) {
        include("dev/gaboron/spwlyrics/provider/LrcShareAccess*.class", "dev/gaboron/spwlyrics/provider/credential/**")
    }
}
val hardenedJar = intermediate.map { it.file("hardened.jar") }
val hardenAccess = tasks.register<JavaExec>("hardenLrcShareAccess") {
    dependsOn(inputJar)
    classpath = obfuscator
    mainClass.set("proguard.ProGuard")
    outputs.file(hardenedJar)
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    notCompatibleWithConfigurationCache("Creates per-build private obfuscation configuration")
    doFirst {
        val directory = intermediate.get().asFile.apply { mkdirs() }
        val random = SecureRandom()
        val dictionary = directory.resolve("dictionary.txt")
        dictionary.writeText((1..256).joinToString("\n") {
            (1..12).map { ('a'.code + random.nextInt(26)).toChar() }.joinToString("")
        })
        fun File.optionPath() = "'${invariantSeparatorsPath}'"
        val libraries = mainSources.get().output.classesDirs.files.map { it.optionPath() +
            "(!dev/gaboron/spwlyrics/provider/LrcShareAccess*.class,!dev/gaboron/spwlyrics/provider/credential/**)" } +
            configurations.getByName("compileClasspath").files.map { it.optionPath() } +
            (File(System.getProperty("java.home"), "jmods/java.base.jmod").optionPath() + "(!**.jar;!module-info.class)")
        val configuration = directory.resolve("proguard.pro")
        configuration.writeText("""
            -injars ${inputJar.get().archiveFile.get().asFile.optionPath()}
            -outjars ${hardenedJar.get().asFile.optionPath()}
            ${libraries.joinToString("\n") { "-libraryjars $it" }}
            -dontoptimize
            -dontusemixedcaseclassnames
            -dontnote
            -obfuscationdictionary ${dictionary.optionPath()}
            -classobfuscationdictionary ${dictionary.optionPath()}
            -repackageclasses dev.gaboron.spwlyrics.provider.credential
            -printmapping ${directory.resolve("mapping.txt").optionPath()}
            -keep class dev.gaboron.spwlyrics.provider.LrcShareAccess {
                public static dev.gaboron.spwlyrics.provider.LrcShareAccess INSTANCE;
                public boolean getAvailable();
                public java.lang.String getIdentity();
                public java.lang.String request(dev.gaboron.spwlyrics.provider.ProviderHttpClient, java.lang.String, java.lang.String);
            }
        """.trimIndent())
        args("@${configuration.absolutePath}")
    }
}

// Only the credential boundary is transformed; SPW entry points and reflective UI stay intact.
tasks.named<Jar>("jar") {
    dependsOn(hardenAccess)
    from({ zipTree(hardenedJar.get().asFile) }) {
        include("**/*.class")
    }
    eachFile {
        val entry = relativePath.pathString.removePrefix("classes/")
        val original = entry.startsWith("dev/gaboron/spwlyrics/provider/credential/") ||
            entry.startsWith("dev/gaboron/spwlyrics/provider/LrcShareAccess")
        if (original && mainSources.get().output.classesDirs.files.any { file.toPath().startsWith(it.toPath()) }) exclude()
    }
}
