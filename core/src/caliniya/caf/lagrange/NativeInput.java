package caliniya.caf.lagrange;

import com.sun.jna.*;

import java.nio.charset.*;
import java.util.*;

/** JNA-owned input bytes; keep open through the call, then close. Never native-FreeMemory these. */
public final class NativeInput implements AutoCloseable{
    private final LagrangeTypes.Bytes.ByValue value = new LagrangeTypes.Bytes.ByValue();
    private final Memory memory;
    private boolean closed;

    private NativeInput(byte[] bytes){
        Objects.requireNonNull(bytes);
        memory = bytes.length == 0 ? null : new Memory(bytes.length);
        if(memory != null) memory.write(0, bytes, 0, bytes.length);
        value.length = bytes.length;
        value.data = memory;
        value.write();
    }

    public static NativeInput bytes(byte[] bytes){
        return new NativeInput(bytes);
    }

    public static NativeInput utf8(String text){
        return bytes(Objects.requireNonNull(text).getBytes(StandardCharsets.UTF_8));
    }

    /** A borrowed struct-by-value input. Do not retain or use it after this owner closes. */
    public LagrangeTypes.Bytes.ByValue value(){
        if(closed) throw new IllegalStateException("Native input is closed.");
        return value;
    }

    @Override
    public void close(){
        if(!closed){
            closed = true;
            value.length = 0;
            value.data = null;
            value.write();
            if(memory != null) memory.close();
        }
    }
}
