package caliniya.caf.lagrange;

import com.sun.jna.*;

import java.nio.charset.*;

/** Common ABI types, not a platform loader or a complete message/event decoder. */
public final class LagrangeTypes{
    public static final int success = 0;
    public static final int invalidIndex = 4;
    public static final byte no = 0, yes = 1;

    private LagrangeTypes(){}

    /** Fail before loading a library with an incompatible pointer size/layout. */
    public static void require64BitAbi(){
        if(Native.POINTER_SIZE != 8 || new Bytes().size() != 16 || new Config().size() != 48 ||
            new Group().size() != 88 || new Message().size() != 160){
            throw new UnsupportedOperationException("CAF currently binds only the 64-bit Lagrange NativeAPI ABI.");
        }
    }

    /** ByteArrayNative. Strings are UTF-8 bytes plus a length, not NUL-terminated char*. */
    @Structure.FieldOrder({"length", "data"})
    public static class Bytes extends Structure{
        public int length;
        public Pointer data;

        public byte[] copy(){
            if(length < 0 || (length > 0 && data == null)) throw new IllegalStateException("Invalid native byte array.");
            return length == 0 ? new byte[0] : data.getByteArray(0, length);
        }

        public String utf8(){
            return new String(copy(), StandardCharsets.UTF_8);
        }

        public static class ByValue extends Bytes implements Structure.ByValue{}
    }

    /** Initialize takes Config*, so call write() and pass getPointer(). */
    @Structure.FieldOrder({"protocol", "autoReconnect", "useIPv6Network", "getOptimumServer", "highwayChunkSize", "highwayConcurrent", "autoReLogin", "signAddress"})
    public static class Config extends Structure{
        // QQ protocol is NOT the host OS. Windows/Mac protocol conversion throws in this revision.
        public byte protocol = 4; // Linux QQ protocol, also when hosted on Windows.
        // These bool fields go through .NET Marshal.PtrToStructure: four-byte BOOLs.
        public int autoReconnect = 1;
        public int useIPv6Network;
        public int getOptimumServer = 1;
        public int highwayChunkSize = 1024 * 1024;
        public int highwayConcurrent = 4;
        public int autoReLogin = 1;
        public Bytes signAddress = new Bytes();
    }

    @Structure.FieldOrder({"events", "count"})
    public static class EventArray extends Structure{
        public Pointer events;
        public int count;

        public EventArray(){}

        public EventArray(Pointer pointer){
            super(pointer);
            read();
        }
    }

    @Structure.FieldOrder({"url", "image"})
    public static class QrCode extends Structure{
        public Bytes url = new Bytes();
        public Bytes image = new Bytes();

        public QrCode(){}

        public QrCode(Pointer pointer){
            super(pointer);
            read();
        }
    }

    @Structure.FieldOrder({"state"})
    public static class QrCodeQuery extends Structure{
        public byte state;

        public QrCodeQuery(){}

        public QrCodeQuery(Pointer pointer){
            super(pointer);
            read();
        }
    }

    @Structure.FieldOrder({"reason"})
    public static class Online extends Structure{
        public int reason;

        public Online(){}

        public Online(Pointer pointer){
            super(pointer);
            read();
        }
    }

    /** Shared layout of Login(State), Log(Level), and Offline(Reason) event records. */
    @Structure.FieldOrder({"code", "tag", "message"})
    public static class StatusEvent extends Structure{
        public int code;
        public Bytes tag = new Bytes();
        public Bytes message = new Bytes();

        public StatusEvent(){}

        public StatusEvent(Pointer pointer){
            super(pointer);
            read();
        }
    }

    @Structure.FieldOrder({"uin", "name", "memberCount", "maxMember", "createTime", "description", "question", "announcement"})
    public static class Group extends Structure{
        public long uin;
        public Bytes name = new Bytes();
        public int memberCount;
        public int maxMember;
        public long createTime;
        public Bytes description = new Bytes();
        public Bytes question = new Bytes();
        public Bytes announcement = new Bytes();
    }

    /** BotMessageStruct. Nested contact/receiver/entity pointers still require type-specific decoding/freeing. */
    @Structure.FieldOrder({"contact", "receiver", "group", "type", "time", "entities", "entityLength", "sequence", "clientSequence"})
    public static class Message extends Structure{
        public Pointer contact;
        public Pointer receiver;
        public Group group = new Group();
        public int type;
        public Bytes time = new Bytes();
        public Pointer entities;
        public int entityLength;
        public long sequence;
        public long clientSequence;

        public Message(){}

        public Message(Pointer pointer){
            super(pointer);
            read();
        }

        public static class ByValue extends Message implements Structure.ByValue{
            public ByValue(){}

            public ByValue(Pointer pointer){
                super(pointer);
            }
        }
    }
}
