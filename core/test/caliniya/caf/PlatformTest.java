package caliniya.caf;

import arc.*;
import arc.files.*;
import caliniya.caf.lagrange.*;
import caliniya.caf.platform.Platform;
import com.sun.jna.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.*;

import java.lang.reflect.Proxy;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

class PlatformTest{
    @TempDir
    Path temporary;

    private Settings previousSettings;

    @BeforeEach
    void installSettings(){
        previousSettings = Core.settings;
        Core.settings = new Settings();
    }

    @AfterEach
    void restoreSettings(){
        Core.settings = previousSettings;
    }

    @Test
    void coreUsesTheInjectedPlatformWithoutAWindow(){
        Fi data = new Fi(temporary.toFile());
        int[] calls = {0};
        LagrangeApi mapped = fake((proxy, method, args) -> {
            assertEquals("Start", method.getName());
            assertEquals(7, args[0]);
            calls[0]++;
            return LagrangeTypes.success;
        });
        CAF caf = new CAF(platform(data, mapped));
        caf.setup();
        assertSame(data, Core.settings.getDataDirectory());
        assertEquals(0, calls[0], "Core setup must not initialize, load, log in or call native code.");
        assertSame(mapped, caf.lagrange);
        assertEquals(LagrangeTypes.success, caf.lagrange.Start(7));
        assertEquals(1, calls[0]);
    }

    @Test
    void requiresAReadyMappingInsteadOfALoadHook(){
        Fi data = new Fi(temporary.toFile());
        assertThrows(NullPointerException.class, () -> new CAF(platform(data, null)));
        for(var method : Platform.class.getDeclaredMethods()){
            assertFalse(method.getName().toLowerCase().contains("load"));
        }
    }

    @Test
    void requiresAPlatform(){
        assertThrows(NullPointerException.class, () -> new CAF(null));
    }

    @Test
    void coreHasSharedJnaDeclarationsButNoDesktopBackend(){
        ClassLoader loader = getClass().getClassLoader();
        for(String name : new String[]{"caliniya.caf.DesktopPlatform", "caliniya.caf.LagrangeNatives", "arc.backend.sdl.SdlApplication"}){
            assertThrows(ClassNotFoundException.class, () -> Class.forName(name, false, loader), name);
        }
        assertTrue(Library.class.isAssignableFrom(LagrangeApi.class));
    }

    private static Platform platform(Fi data, LagrangeApi api){
        return new Platform(){
            public Fi dataDirectory(){ return data; }
            public LagrangeApi lagrange(){ return api; }
        };
    }

    private static LagrangeApi fake(java.lang.reflect.InvocationHandler handler){
        return (LagrangeApi)Proxy.newProxyInstance(LagrangeApi.class.getClassLoader(), new Class<?>[]{LagrangeApi.class}, handler);
    }
}
