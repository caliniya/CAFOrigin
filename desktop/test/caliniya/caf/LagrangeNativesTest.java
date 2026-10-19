package caliniya.caf;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.*;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class LagrangeNativesTest{
    @TempDir
    Path temporary;

    @Test
    void selectsTheJvmPlatform(){
        assertEquals("win-x64", LagrangeNatives.platform("Windows 11", "amd64"));
        assertEquals("win-x64", LagrangeNatives.platform("Windows 10", "x86_64"));
        assertEquals("win-arm64", LagrangeNatives.platform("Windows 11", "aarch64"));
        assertEquals("linux-x64", LagrangeNatives.platform("Linux", "amd64"));
        assertEquals("linux-arm64", LagrangeNatives.platform("Linux", "aarch64"));
        assertEquals("osx-arm64", LagrangeNatives.platform("Mac OS X", "aarch64"));
        assertEquals("osx-x64", LagrangeNatives.platform("Darwin", "x86_64"));
        assertThrows(UnsupportedOperationException.class, () -> LagrangeNatives.platform("Windows 11", "x86"));
        assertThrows(UnsupportedOperationException.class, () -> LagrangeNatives.platform("Solaris", "amd64"));
    }

    @Test
    void extractsReusesAndRepairsTheBundledDll() throws Exception{
        byte[] expected = bundledDll();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(expected));
        Path data = temporary.resolve("CAF 测试 with spaces");
        Path extracted = LagrangeNatives.extract(data, "win-x64");
        assertEquals(data.resolve("natives/win-x64").resolve(hash).resolve("Lagrange.Core.NativeAPI.dll"), extracted);
        assertArrayEquals(expected, Files.readAllBytes(extracted));

        FileTime time = FileTime.fromMillis(1000L);
        Files.setLastModifiedTime(extracted, time);
        assertEquals(extracted, LagrangeNatives.extract(data, "win-x64"));
        assertEquals(time, Files.getLastModifiedTime(extracted), "An unchanged DLL should not be overwritten.");

        Files.writeString(extracted, "corrupted cache");
        assertEquals(extracted, LagrangeNatives.extract(data, "win-x64"));
        assertArrayEquals(expected, Files.readAllBytes(extracted));
        assertNoTemporaryFiles(data);
    }

    @Test
    void concurrentExtractionProducesOneCompleteDll() throws Exception{
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try{
            List<Callable<Path>> tasks = new ArrayList<>();
            for(int i = 0; i < 4; i++) tasks.add(() -> LagrangeNatives.extract(temporary, "win-x64"));
            List<Future<Path>> results = executor.invokeAll(tasks);
            Path extracted = results.get(0).get();
            for(Future<Path> result : results) assertEquals(extracted, result.get());
            assertArrayEquals(bundledDll(), Files.readAllBytes(extracted));
            assertNoTemporaryFiles(temporary);
        }finally{
            executor.shutdownNow();
        }
    }

    @Test
    void reportsMissingPlatformWithoutCreatingACache(){
        Path data = temporary.resolve("not-created");
        FileNotFoundException error = assertThrows(FileNotFoundException.class, () -> LagrangeNatives.extract(data, "win-missing"));
        assertTrue(error.getMessage().contains("/natives/win-missing/Lagrange.Core.NativeAPI.dll"));
        assertFalse(Files.exists(data));
    }

    @Test
    void failsCleanlyWhenTheDataDirectoryIsAFile() throws IOException{
        Path data = temporary.resolve("not-a-directory");
        Files.writeString(data, "leave intact");
        assertThrows(IOException.class, () -> LagrangeNatives.extract(data, "win-x64"));
        assertEquals("leave intact", Files.readString(data));
    }

    private static byte[] bundledDll() throws IOException{
        try(InputStream input = LagrangeNativesTest.class.getResourceAsStream("/natives/win-x64/Lagrange.Core.NativeAPI.dll")){
            assertNotNull(input, "The Windows DLL must be included in processResources.");
            byte[] bytes = input.readAllBytes();
            assertTrue(bytes.length > 2 && bytes[0] == 'M' && bytes[1] == 'Z', "Expected a real PE binary, not an LFS pointer.");
            return bytes;
        }
    }

    private static void assertNoTemporaryFiles(Path data) throws IOException{
        try(var files = Files.walk(data)){
            assertFalse(files.anyMatch(file -> file.toString().endsWith(".tmp")));
        }
    }
}
