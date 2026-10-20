package caliniya.caf;

import arc.files.*;
import caliniya.caf.lagrange.*;
import caliniya.caf.platform.Platform;
import com.sun.jna.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

public final class DesktopPlatform implements Platform{
    private final Fi dataDirectory;
    private final LagrangeApi lagrange;
    // Native AOT libraries and their mappings must remain alive for the process lifetime.
    private static LagrangeApi sharedApi;

    /** Accepts an already prepared mapping; also useful for tests and alternative backends. */
    public DesktopPlatform(Fi dataDirectory, LagrangeApi lagrange){
        this.dataDirectory = Objects.requireNonNull(dataDirectory);
        this.lagrange = Objects.requireNonNull(lagrange);
    }

    /** Desktop bootstrap only. Extracts, loads and maps functions, but never logs in. */
    public static synchronized DesktopPlatform load(Fi dataDirectory) throws IOException{
        Objects.requireNonNull(dataDirectory);
        if(sharedApi == null){
            LagrangeTypes.require64BitAbi();
            sharedApi = bind(LagrangeNatives.load(dataDirectory.file().toPath()));
        }
        return new DesktopPlatform(dataDirectory, sharedApi);
    }

    @Override
    public Fi dataDirectory(){
        return dataDirectory;
    }

    @Override
    public LagrangeApi lagrange(){
        return lagrange;
    }

    static LagrangeApi bind(NativeLibrary library){
        LagrangeTypes.require64BitAbi();
        // Resolve every export now, instead of discovering an incompatible DLL mid-session.
        for(Method method : LagrangeApi.class.getDeclaredMethods()){
            library.getFunction(method.getName());
        }
        LagrangeApi mapped = Native.load(library.getFile().getAbsolutePath(), LagrangeApi.class,
            Map.of(Library.OPTION_STRING_ENCODING, "UTF-8"));
        return checked(mapped);
    }

    static LagrangeApi checked(LagrangeApi mapped){
        Objects.requireNonNull(mapped);
        return (LagrangeApi)Proxy.newProxyInstance(LagrangeApi.class.getClassLoader(), new Class<?>[]{LagrangeApi.class}, (proxy, method, args) -> {
            if(method.getDeclaringClass() == Object.class){
                return switch(method.getName()){
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "LagrangeApi[desktop]";
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            }
            // Every first int parameter in this ABI is a context index. The native
            // revision checks the upper bound but crashes on negative indices.
            if(method.getParameterTypes()[0] == int.class && (int)args[0] < 0){
                throw new IllegalArgumentException("Native context index must be nonnegative.");
            }
            if(method.getName().equals("Initialize") && Pointer.nativeValue((Pointer)args[0]) == 0){
                throw new IllegalArgumentException("Initialize requires a config pointer.");
            }
            for(int i = 0; i < args.length; i++){
                if(method.getParameterTypes()[i] == byte.class && (byte)args[i] != 0 && (byte)args[i] != 1){
                    throw new IllegalArgumentException("Native boolean arguments must be byte 0 or 1.");
                }
            }
            try{
                return method.invoke(mapped, args);
            }catch(InvocationTargetException failure){
                throw failure.getCause();
            }
        });
    }
}
