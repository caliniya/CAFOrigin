package caliniya.caf;

import arc.files.*;
import caliniya.caf.lagrange.*;
import com.sun.jna.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DesktopPlatformTest{
    @TempDir
    Path temporary;

    @Test
    void platformOnlyPublishesReadyServices(){
        Fi data = new Fi(temporary.resolve("not-created").toFile());
        LagrangeApi api = fake((proxy, method, args) -> { throw new AssertionError("No native call during injection."); });
        DesktopPlatform platform = new DesktopPlatform(data, api);
        assertSame(data, platform.dataDirectory());
        assertSame(api, platform.lagrange());
        assertFalse(Files.exists(data.file().toPath()));
        assertThrows(NullPointerException.class, () -> new DesktopPlatform(data, null));
        assertThrows(NullPointerException.class, () -> new DesktopPlatform(null, api));
    }

    @Test
    void forwardsAll49DeclarationsAndReturnsTheMappedResult() throws Exception{
        List<Method> calls = new ArrayList<>();
        LagrangeApi mapped = fake((proxy, method, args) -> {
            calls.add(method);
            return result(method);
        });
        LagrangeApi api = DesktopPlatform.checked(mapped);
        for(Method method : LagrangeApi.class.getDeclaredMethods()){
            assertEquals(result(method), method.invoke(api, arguments(method, 7)), method.getName());
        }
        assertEquals(49, calls.size());
        assertEquals(49, new HashSet<>(calls).size());
    }

    @Test
    void negativeIndicesNeverReachNativeCode(){
        int[] calls = {0};
        LagrangeApi api = DesktopPlatform.checked(fake((proxy, method, args) -> { calls[0]++; return result(method); }));
        int tested = 0;
        for(Method method : LagrangeApi.class.getDeclaredMethods()){
            if(method.getParameterTypes()[0] == int.class){
                var error = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> method.invoke(api, arguments(method, -1)));
                assertInstanceOf(IllegalArgumentException.class, error.getCause(), method.getName());
                tested++;
            }
        }
        assertEquals(47, tested);
        assertEquals(0, calls[0]);
    }

    @Test
    void rejectsNullConfigAndInvalidBoolBeforeCallingNativeCode(){
        LagrangeApi api = DesktopPlatform.checked(fake((proxy, method, args) -> { throw new AssertionError("Invalid argument reached native code."); }));
        assertThrows(IllegalArgumentException.class, () -> api.Initialize(null, null, null));
        assertThrows(IllegalArgumentException.class, () -> api.Initialize(new Pointer(0), null, null));
        assertThrows(IllegalArgumentException.class, () -> api.GetGroupList(0, (byte)2));
        assertThrows(IllegalArgumentException.class, () -> api.SetGroupReaction(0, 1, 2, new LagrangeTypes.Bytes.ByValue(), (byte)-1));
    }

    @Test
    void keepsObjectMethodsLocalAndDoesNotWrapNativeFailures(){
        UnsupportedOperationException failure = new UnsupportedOperationException("test binding failure");
        LagrangeApi api = DesktopPlatform.checked(fake((proxy, method, args) -> { throw failure; }));
        assertEquals("LagrangeApi[desktop]", api.toString());
        assertTrue(api.equals(api));
        assertFalse(api.equals(new Object()));
        assertEquals(System.identityHashCode(api), api.hashCode());
        assertSame(failure, assertThrows(UnsupportedOperationException.class, () -> api.Start(0)));
    }

    @Test
    void failsBeforeInjectionIfAnyExportIsMissing(){
        assertThrows(UnsatisfiedLinkError.class, () -> DesktopPlatform.bind(NativeLibrary.getProcess()));
    }

    private static Object result(Method method){
        if(method.getReturnType() == int.class) return 17;
        if(method.getReturnType() == Pointer.class) return Pointer.createConstant(0x12345678L);
        return null;
    }

    private static Object[] arguments(Method method, int index) throws Exception{
        Class<?>[] types = method.getParameterTypes();
        Object[] args = new Object[types.length];
        for(int i = 0; i < types.length; i++){
            if(types[i] == int.class) args[i] = i == 0 ? index : 5;
            else if(types[i] == long.class) args[i] = 0xfedcba9876543210L;
            else if(types[i] == byte.class) args[i] = (byte)1;
            else if(types[i] == Pointer.class) args[i] = Pointer.createConstant(0x12345678L);
            else args[i] = types[i].getConstructor().newInstance();
        }
        return args;
    }

    private static LagrangeApi fake(InvocationHandler handler){
        return (LagrangeApi)Proxy.newProxyInstance(LagrangeApi.class.getClassLoader(), new Class<?>[]{LagrangeApi.class}, handler);
    }
}
