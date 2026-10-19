package caliniya.caf;

import com.sun.jna.*;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Extracts the bundled native library before loading it through JNA. */
public final class LagrangeNatives{
    // Keep a strong reference for the process lifetime: Native AOT must not be unloaded.
    private static NativeLibrary library;

    private LagrangeNatives(){}

    /** Returns the shared library. Do not close/dispose the returned instance. */
    public static synchronized NativeLibrary load(Path dataDirectory) throws IOException{
        if(library == null){
            String platform = platform(System.getProperty("os.name"), System.getProperty("os.arch"));
            Path file = extract(dataDirectory, platform);
            library = NativeLibrary.getInstance(file.toString(), Map.of(Library.OPTION_STRING_ENCODING, "UTF-8"));
        }
        return library;
    }

    static String platform(String osName, String architecture){
        String os = osName.toLowerCase(Locale.ROOT);
        String system;
        if(os.startsWith("windows")) system = "win";
        else if(os.startsWith("linux")) system = "linux";
        else if(os.startsWith("mac") || os.startsWith("darwin")) system = "osx";
        else throw new UnsupportedOperationException("Unsupported operating system: " + osName);

        String cpu = switch(architecture.toLowerCase(Locale.ROOT)){
            case "amd64", "x86_64", "x64" -> "x64";
            case "aarch64", "arm64" -> "arm64";
            default -> throw new UnsupportedOperationException("Unsupported JVM architecture: " + architecture);
        };
        return system + "-" + cpu;
    }

    static Path extract(Path dataDirectory, String platform) throws IOException{
        String extension = switch(platform.substring(0, platform.indexOf('-'))){
            case "win" -> ".dll";
            case "linux" -> ".so";
            case "osx" -> ".dylib";
            default -> throw new UnsupportedOperationException("Unsupported native platform: " + platform);
        };
        String fileName = "Lagrange.Core.NativeAPI" + extension;
        String resource = "/natives/" + platform + "/" + fileName;
        try(InputStream input = LagrangeNatives.class.getResourceAsStream(resource)){
            if(input == null){
                throw new FileNotFoundException("Missing bundled native library: " + resource + ". Build the native library for this platform and rebuild CAF.");
            }
            Path directory = dataDirectory.toAbsolutePath().normalize().resolve("natives").resolve(platform);
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, "lagrange-", ".tmp");
            try{
                MessageDigest digest = sha256();
                try(InputStream stream = new DigestInputStream(input, digest)){
                    Files.copy(stream, temporary, StandardCopyOption.REPLACE_EXISTING);
                }
                // Content-addressed paths avoid replacing a DLL locked by an older running version.
                Path target = directory.resolve(HexFormat.of().formatHex(digest.digest())).resolve(fileName);
                Files.createDirectories(target.getParent());
                if(!matches(temporary, target)){
                    try{
                        try{
                            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                        }catch(AtomicMoveNotSupportedException unsupported){
                            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }catch(IOException failure){
                        // Another process may already have installed and loaded the same DLL.
                        if(!matches(temporary, target)) throw failure;
                    }
                }
                return target;
            }finally{
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static boolean matches(Path source, Path target) throws IOException{
        return Files.isRegularFile(target) && Files.mismatch(source, target) == -1L;
    }

    private static MessageDigest sha256(){
        try{
            return MessageDigest.getInstance("SHA-256");
        }catch(NoSuchAlgorithmException impossible){
            throw new AssertionError(impossible);
        }
    }
}
