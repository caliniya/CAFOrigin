package caliniya.caf.lagrange;

import com.sun.jna.*;
import org.junit.jupiter.api.*;

import java.nio.charset.*;

import static caliniya.caf.lagrange.LagrangeTypes.*;
import static org.junit.jupiter.api.Assertions.*;

class LagrangeTypesTest{
    @Test
    void matchesThe64BitLayouts(){
        LagrangeTypes.require64BitAbi();
        assertEquals(16, new Bytes().size());
        assertEquals(16, new Bytes.ByValue().size());
        assertEquals(48, new Config().size());
        assertEquals(16, new EventArray().size());
        assertEquals(32, new QrCode().size());
        assertEquals(1, new QrCodeQuery().size());
        assertEquals(4, new Online().size());
        assertEquals(40, new StatusEvent().size());
        assertEquals(88, new Group().size());
        assertEquals(160, new Message().size());
        assertEquals(160, new Message.ByValue().size());
    }

    @Test
    void configUsesBool32FieldsAndAnInlineByteArray(){
        try(NativeInput endpoint = NativeInput.utf8("https://example.invalid/sign")){
            Config config = new Config();
            config.signAddress = endpoint.value();
            config.write();
            Pointer pointer = config.getPointer();
            assertEquals(4, pointer.getByte(0));
            assertEquals(1, pointer.getInt(4));
            assertEquals(0, pointer.getInt(8));
            assertEquals(1, pointer.getInt(12));
            assertEquals(1024 * 1024, pointer.getInt(16));
            assertEquals(4, pointer.getInt(20));
            assertEquals(1, pointer.getInt(24));
            assertEquals(endpoint.value().length, pointer.getInt(32));
            assertEquals(endpoint.value().data, pointer.getPointer(40));
        }
    }

    @Test
    void messageUsesTheFullInlineGroupLayout(){
        Message message = new Message();
        message.group.uin = 1234567890123L;
        message.type = 2;
        message.entityLength = 7;
        message.sequence = -2L; // ulong bit pattern preserved.
        message.clientSequence = 1234567890124L;
        message.write();
        Pointer pointer = message.getPointer();
        assertEquals(message.group.uin, pointer.getLong(16));
        assertEquals(2, pointer.getInt(104));
        assertEquals(7, pointer.getInt(136));
        assertEquals(-2L, pointer.getLong(144));
        assertEquals(message.clientSequence, pointer.getLong(152));
        assertEquals(-2L, new Message(pointer).sequence);
    }

    @Test
    void readsQrAndStatusRecordsWithoutTakingOwnership(){
        try(NativeInput url = NativeInput.utf8("https://example.invalid/二维码");
            NativeInput image = NativeInput.bytes(new byte[]{0, 2, 3, -1})){
            QrCode event = new QrCode();
            event.url = url.value();
            event.image = image.value();
            event.write();
            QrCode decoded = new QrCode(event.getPointer());
            assertEquals("https://example.invalid/二维码", decoded.url.utf8());
            assertArrayEquals(new byte[]{0, 2, 3, -1}, decoded.image.copy());
            // copy/read must not free the borrowed input buffer.
            assertArrayEquals(image.value().copy(), decoded.image.copy());
        }
    }

    @Test
    void ownsInputSeparatelyAndPreservesEmbeddedZeroBytes(){
        String text = "你好\0CAF";
        NativeInput input = NativeInput.utf8(text);
        Bytes.ByValue borrowed = input.value();
        try{
            assertEquals(text.getBytes(StandardCharsets.UTF_8).length, borrowed.length);
            assertEquals(text, borrowed.utf8());
            assertTrue(borrowed instanceof Structure.ByValue);
        }finally{
            input.close();
        }
        input.close();
        assertThrows(IllegalStateException.class, input::value);
        assertEquals(0, borrowed.length);
        assertNull(borrowed.data);
    }

    @Test
    void supportsEmptyInputAndCopiesTheSourceArray(){
        try(NativeInput empty = NativeInput.bytes(new byte[0])){
            assertNull(empty.value().data);
            assertArrayEquals(new byte[0], empty.value().copy());
        }
        byte[] source = {1, 2, 3};
        try(NativeInput input = NativeInput.bytes(source)){
            source[0] = 9;
            assertArrayEquals(new byte[]{1, 2, 3}, input.value().copy());
        }
        assertThrows(NullPointerException.class, () -> NativeInput.bytes(null));
        assertThrows(NullPointerException.class, () -> NativeInput.utf8(null));
    }

    @Test
    void rejectsMalformedByteArrayMetadata(){
        Bytes bytes = new Bytes();
        bytes.length = -1;
        assertThrows(IllegalStateException.class, bytes::copy);
        bytes.length = 1;
        assertThrows(IllegalStateException.class, bytes::copy);
    }

    @Test
    void everyBindingUsesAnExplicitNativeWidth(){
        assertEquals(49, LagrangeApi.class.getDeclaredMethods().length);
        assertEquals(0, LagrangeApi.class.getDeclaredFields().length, "No static INSTANCE that loads Lagrange from core.");
        for(var method : LagrangeApi.class.getDeclaredMethods()){
            for(Class<?> parameter : method.getParameterTypes()){
                assertNotEquals(boolean.class, parameter, "Use byte 0/1, not JNA's four-byte boolean parameter.");
                assertNotEquals(NativeLong.class, parameter, "C# long/ulong is fixed-width, including on Windows.");
                if(Structure.class.isAssignableFrom(parameter)) assertTrue(Structure.ByValue.class.isAssignableFrom(parameter));
            }
        }
    }
}
