package amadeus.maho.util.bytecode;

import jdk.internal.vm.annotation.Stable;

import org.objectweb.asm.Type;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.vm.transform.mark.HotSpotJIT;
import amadeus.maho.vm.transform.mark.HotSpotMethodFlags;

import static amadeus.maho.util.bytecode.Bytecodes.Flags.*;
import static amadeus.maho.vm.reflection.hotspot.KlassMethod.Flags._force_inline;

@HotSpotJIT
public interface Bytecodes {
    
    interface Flags {
        
        int
                STOP         = 0x00000001,
                FALL_THROUGH = 0x00000002,
                BRANCH       = 0x00000004,
                FIELD_READ   = 0x00000008,
                FIELD_WRITE  = 0x00000010,
                TRAP         = 0x00000080,
                COMMUTATIVE  = 0x00000100,
                ASSOCIATIVE  = 0x00000200,
                LOAD         = 0x00000400,
                STORE        = 0x00000800,
                INVOKE       = 0x00001000;
        
    }
    
    int
            // @formatter:off
            NOP                  =   0, // 0x00
            ACONST_NULL          =   1, // 0x01
            ICONST_M1            =   2, // 0x02
            ICONST_0             =   3, // 0x03
            ICONST_1             =   4, // 0x04
            ICONST_2             =   5, // 0x05
            ICONST_3             =   6, // 0x06
            ICONST_4             =   7, // 0x07
            ICONST_5             =   8, // 0x08
            LCONST_0             =   9, // 0x09
            LCONST_1             =  10, // 0x0A
            FCONST_0             =  11, // 0x0B
            FCONST_1             =  12, // 0x0C
            FCONST_2             =  13, // 0x0D
            DCONST_0             =  14, // 0x0E
            DCONST_1             =  15, // 0x0F
            BIPUSH               =  16, // 0x10
            SIPUSH               =  17, // 0x11
            LDC                  =  18, // 0x12
            LDC_W                =  19, // 0x13
            LDC2_W               =  20, // 0x14
            ILOAD                =  21, // 0x15
            LLOAD                =  22, // 0x16
            FLOAD                =  23, // 0x17
            DLOAD                =  24, // 0x18
            ALOAD                =  25, // 0x19
            ILOAD_0              =  26, // 0x1A
            ILOAD_1              =  27, // 0x1B
            ILOAD_2              =  28, // 0x1C
            ILOAD_3              =  29, // 0x1D
            LLOAD_0              =  30, // 0x1E
            LLOAD_1              =  31, // 0x1F
            LLOAD_2              =  32, // 0x20
            LLOAD_3              =  33, // 0x21
            FLOAD_0              =  34, // 0x22
            FLOAD_1              =  35, // 0x23
            FLOAD_2              =  36, // 0x24
            FLOAD_3              =  37, // 0x25
            DLOAD_0              =  38, // 0x26
            DLOAD_1              =  39, // 0x27
            DLOAD_2              =  40, // 0x28
            DLOAD_3              =  41, // 0x29
            ALOAD_0              =  42, // 0x2A
            ALOAD_1              =  43, // 0x2B
            ALOAD_2              =  44, // 0x2C
            ALOAD_3              =  45, // 0x2D
            IALOAD               =  46, // 0x2E
            LALOAD               =  47, // 0x2F
            FALOAD               =  48, // 0x30
            DALOAD               =  49, // 0x31
            AALOAD               =  50, // 0x32
            BALOAD               =  51, // 0x33
            CALOAD               =  52, // 0x34
            SALOAD               =  53, // 0x35
            ISTORE               =  54, // 0x36
            LSTORE               =  55, // 0x37
            FSTORE               =  56, // 0x38
            DSTORE               =  57, // 0x39
            ASTORE               =  58, // 0x3A
            ISTORE_0             =  59, // 0x3B
            ISTORE_1             =  60, // 0x3C
            ISTORE_2             =  61, // 0x3D
            ISTORE_3             =  62, // 0x3E
            LSTORE_0             =  63, // 0x3F
            LSTORE_1             =  64, // 0x40
            LSTORE_2             =  65, // 0x41
            LSTORE_3             =  66, // 0x42
            FSTORE_0             =  67, // 0x43
            FSTORE_1             =  68, // 0x44
            FSTORE_2             =  69, // 0x45
            FSTORE_3             =  70, // 0x46
            DSTORE_0             =  71, // 0x47
            DSTORE_1             =  72, // 0x48
            DSTORE_2             =  73, // 0x49
            DSTORE_3             =  74, // 0x4A
            ASTORE_0             =  75, // 0x4B
            ASTORE_1             =  76, // 0x4C
            ASTORE_2             =  77, // 0x4D
            ASTORE_3             =  78, // 0x4E
            IASTORE              =  79, // 0x4F
            LASTORE              =  80, // 0x50
            FASTORE              =  81, // 0x51
            DASTORE              =  82, // 0x52
            AASTORE              =  83, // 0x53
            BASTORE              =  84, // 0x54
            CASTORE              =  85, // 0x55
            SASTORE              =  86, // 0x56
            POP                  =  87, // 0x57
            POP2                 =  88, // 0x58
            DUP                  =  89, // 0x59
            DUP_X1               =  90, // 0x5A
            DUP_X2               =  91, // 0x5B
            DUP2                 =  92, // 0x5C
            DUP2_X1              =  93, // 0x5D
            DUP2_X2              =  94, // 0x5E
            SWAP                 =  95, // 0x5F
            IADD                 =  96, // 0x60
            LADD                 =  97, // 0x61
            FADD                 =  98, // 0x62
            DADD                 =  99, // 0x63
            ISUB                 = 100, // 0x64
            LSUB                 = 101, // 0x65
            FSUB                 = 102, // 0x66
            DSUB                 = 103, // 0x67
            IMUL                 = 104, // 0x68
            LMUL                 = 105, // 0x69
            FMUL                 = 106, // 0x6A
            DMUL                 = 107, // 0x6B
            IDIV                 = 108, // 0x6C
            LDIV                 = 109, // 0x6D
            FDIV                 = 110, // 0x6E
            DDIV                 = 111, // 0x6F
            IREM                 = 112, // 0x70
            LREM                 = 113, // 0x71
            FREM                 = 114, // 0x72
            DREM                 = 115, // 0x73
            INEG                 = 116, // 0x74
            LNEG                 = 117, // 0x75
            FNEG                 = 118, // 0x76
            DNEG                 = 119, // 0x77
            ISHL                 = 120, // 0x78
            LSHL                 = 121, // 0x79
            ISHR                 = 122, // 0x7A
            LSHR                 = 123, // 0x7B
            IUSHR                = 124, // 0x7C
            LUSHR                = 125, // 0x7D
            IAND                 = 126, // 0x7E
            LAND                 = 127, // 0x7F
            IOR                  = 128, // 0x80
            LOR                  = 129, // 0x81
            IXOR                 = 130, // 0x82
            LXOR                 = 131, // 0x83
            IINC                 = 132, // 0x84
            I2L                  = 133, // 0x85
            I2F                  = 134, // 0x86
            I2D                  = 135, // 0x87
            L2I                  = 136, // 0x88
            L2F                  = 137, // 0x89
            L2D                  = 138, // 0x8A
            F2I                  = 139, // 0x8B
            F2L                  = 140, // 0x8C
            F2D                  = 141, // 0x8D
            D2I                  = 142, // 0x8E
            D2L                  = 143, // 0x8F
            D2F                  = 144, // 0x90
            I2B                  = 145, // 0x91
            I2C                  = 146, // 0x92
            I2S                  = 147, // 0x93
            LCMP                 = 148, // 0x94
            FCMPL                = 149, // 0x95
            FCMPG                = 150, // 0x96
            DCMPL                = 151, // 0x97
            DCMPG                = 152, // 0x98
            IFEQ                 = 153, // 0x99
            IFNE                 = 154, // 0x9A
            IFLT                 = 155, // 0x9B
            IFGE                 = 156, // 0x9C
            IFGT                 = 157, // 0x9D
            IFLE                 = 158, // 0x9E
            IF_ICMPEQ            = 159, // 0x9F
            IF_ICMPNE            = 160, // 0xA0
            IF_ICMPLT            = 161, // 0xA1
            IF_ICMPGE            = 162, // 0xA2
            IF_ICMPGT            = 163, // 0xA3
            IF_ICMPLE            = 164, // 0xA4
            IF_ACMPEQ            = 165, // 0xA5
            IF_ACMPNE            = 166, // 0xA6
            GOTO                 = 167, // 0xA7
            JSR                  = 168, // 0xA8
            RET                  = 169, // 0xA9
            TABLESWITCH          = 170, // 0xAA
            LOOKUPSWITCH         = 171, // 0xAB
            IRETURN              = 172, // 0xAC
            LRETURN              = 173, // 0xAD
            FRETURN              = 174, // 0xAE
            DRETURN              = 175, // 0xAF
            ARETURN              = 176, // 0xB0
            RETURN               = 177, // 0xB1
            GETSTATIC            = 178, // 0xB2
            PUTSTATIC            = 179, // 0xB3
            GETFIELD             = 180, // 0xB4
            PUTFIELD             = 181, // 0xB5
            INVOKEVIRTUAL        = 182, // 0xB6
            INVOKESPECIAL        = 183, // 0xB7
            INVOKESTATIC         = 184, // 0xB8
            INVOKEINTERFACE      = 185, // 0xB9
            INVOKEDYNAMIC        = 186, // 0xBA
            NEW                  = 187, // 0xBB
            NEWARRAY             = 188, // 0xBC
            ANEWARRAY            = 189, // 0xBD
            ARRAYLENGTH          = 190, // 0xBE
            ATHROW               = 191, // 0xBF
            CHECKCAST            = 192, // 0xC0
            INSTANCEOF           = 193, // 0xC1
            MONITORENTER         = 194, // 0xC2
            MONITOREXIT          = 195, // 0xC3
            WIDE                 = 196, // 0xC4
            MULTIANEWARRAY       = 197, // 0xC5
            IFNULL               = 198, // 0xC6
            IFNONNULL            = 199, // 0xC7
            GOTO_W               = 200, // 0xC8
            JSR_W                = 201, // 0xC9
            BREAKPOINT           = 202; // 0xCA
    // @formatter:on
    
    int MAX = 0xFF, LENGTH = 0x100;
    
    @Stable
    String nameArray[] = new String[LENGTH];
    
    @Stable
    int flagsArray[] = new int[LENGTH], lengthArray[] = new int[LENGTH], stackEffectArray[] = new int[LENGTH];
    
    {
        // @formatter:off
        def(NOP                 , "nop"             , "b"    ,  0);
        def(ACONST_NULL         , "aconst_null"     , "b"    ,  1);
        def(ICONST_M1           , "iconst_m1"       , "b"    ,  1);
        def(ICONST_0            , "iconst_0"        , "b"    ,  1);
        def(ICONST_1            , "iconst_1"        , "b"    ,  1);
        def(ICONST_2            , "iconst_2"        , "b"    ,  1);
        def(ICONST_3            , "iconst_3"        , "b"    ,  1);
        def(ICONST_4            , "iconst_4"        , "b"    ,  1);
        def(ICONST_5            , "iconst_5"        , "b"    ,  1);
        def(LCONST_0            , "lconst_0"        , "b"    ,  2);
        def(LCONST_1            , "lconst_1"        , "b"    ,  2);
        def(FCONST_0            , "fconst_0"        , "b"    ,  1);
        def(FCONST_1            , "fconst_1"        , "b"    ,  1);
        def(FCONST_2            , "fconst_2"        , "b"    ,  1);
        def(DCONST_0            , "dconst_0"        , "b"    ,  2);
        def(DCONST_1            , "dconst_1"        , "b"    ,  2);
        def(BIPUSH              , "bipush"          , "bc"   ,  1);
        def(SIPUSH              , "sipush"          , "bcc"  ,  1);
        def(LDC                 , "ldc"             , "bi"   ,  1, TRAP);
        def(LDC_W               , "ldc_w"           , "bii"  ,  1, TRAP);
        def(LDC2_W              , "ldc2_w"          , "bii"  ,  2, TRAP);
        def(ILOAD               , "iload"           , "bi"   ,  1, LOAD);
        def(LLOAD               , "lload"           , "bi"   ,  2, LOAD);
        def(FLOAD               , "fload"           , "bi"   ,  1, LOAD);
        def(DLOAD               , "dload"           , "bi"   ,  2, LOAD);
        def(ALOAD               , "aload"           , "bi"   ,  1, LOAD);
        def(ILOAD_0             , "iload_0"         , "b"    ,  1, LOAD);
        def(ILOAD_1             , "iload_1"         , "b"    ,  1, LOAD);
        def(ILOAD_2             , "iload_2"         , "b"    ,  1, LOAD);
        def(ILOAD_3             , "iload_3"         , "b"    ,  1, LOAD);
        def(LLOAD_0             , "lload_0"         , "b"    ,  2, LOAD);
        def(LLOAD_1             , "lload_1"         , "b"    ,  2, LOAD);
        def(LLOAD_2             , "lload_2"         , "b"    ,  2, LOAD);
        def(LLOAD_3             , "lload_3"         , "b"    ,  2, LOAD);
        def(FLOAD_0             , "fload_0"         , "b"    ,  1, LOAD);
        def(FLOAD_1             , "fload_1"         , "b"    ,  1, LOAD);
        def(FLOAD_2             , "fload_2"         , "b"    ,  1, LOAD);
        def(FLOAD_3             , "fload_3"         , "b"    ,  1, LOAD);
        def(DLOAD_0             , "dload_0"         , "b"    ,  2, LOAD);
        def(DLOAD_1             , "dload_1"         , "b"    ,  2, LOAD);
        def(DLOAD_2             , "dload_2"         , "b"    ,  2, LOAD);
        def(DLOAD_3             , "dload_3"         , "b"    ,  2, LOAD);
        def(ALOAD_0             , "aload_0"         , "b"    ,  1, LOAD);
        def(ALOAD_1             , "aload_1"         , "b"    ,  1, LOAD);
        def(ALOAD_2             , "aload_2"         , "b"    ,  1, LOAD);
        def(ALOAD_3             , "aload_3"         , "b"    ,  1, LOAD);
        def(IALOAD              , "iaload"          , "b"    , -1, TRAP);
        def(LALOAD              , "laload"          , "b"    ,  0, TRAP);
        def(FALOAD              , "faload"          , "b"    , -1, TRAP);
        def(DALOAD              , "daload"          , "b"    ,  0, TRAP);
        def(AALOAD              , "aaload"          , "b"    , -1, TRAP);
        def(BALOAD              , "baload"          , "b"    , -1, TRAP);
        def(CALOAD              , "caload"          , "b"    , -1, TRAP);
        def(SALOAD              , "saload"          , "b"    , -1, TRAP);
        def(ISTORE              , "istore"          , "bi"   , -1, STORE);
        def(LSTORE              , "lstore"          , "bi"   , -2, STORE);
        def(FSTORE              , "fstore"          , "bi"   , -1, STORE);
        def(DSTORE              , "dstore"          , "bi"   , -2, STORE);
        def(ASTORE              , "astore"          , "bi"   , -1, STORE);
        def(ISTORE_0            , "istore_0"        , "b"    , -1, STORE);
        def(ISTORE_1            , "istore_1"        , "b"    , -1, STORE);
        def(ISTORE_2            , "istore_2"        , "b"    , -1, STORE);
        def(ISTORE_3            , "istore_3"        , "b"    , -1, STORE);
        def(LSTORE_0            , "lstore_0"        , "b"    , -2, STORE);
        def(LSTORE_1            , "lstore_1"        , "b"    , -2, STORE);
        def(LSTORE_2            , "lstore_2"        , "b"    , -2, STORE);
        def(LSTORE_3            , "lstore_3"        , "b"    , -2, STORE);
        def(FSTORE_0            , "fstore_0"        , "b"    , -1, STORE);
        def(FSTORE_1            , "fstore_1"        , "b"    , -1, STORE);
        def(FSTORE_2            , "fstore_2"        , "b"    , -1, STORE);
        def(FSTORE_3            , "fstore_3"        , "b"    , -1, STORE);
        def(DSTORE_0            , "dstore_0"        , "b"    , -2, STORE);
        def(DSTORE_1            , "dstore_1"        , "b"    , -2, STORE);
        def(DSTORE_2            , "dstore_2"        , "b"    , -2, STORE);
        def(DSTORE_3            , "dstore_3"        , "b"    , -2, STORE);
        def(ASTORE_0            , "astore_0"        , "b"    , -1, STORE);
        def(ASTORE_1            , "astore_1"        , "b"    , -1, STORE);
        def(ASTORE_2            , "astore_2"        , "b"    , -1, STORE);
        def(ASTORE_3            , "astore_3"        , "b"    , -1, STORE);
        def(IASTORE             , "iastore"         , "b"    , -3, TRAP);
        def(LASTORE             , "lastore"         , "b"    , -4, TRAP);
        def(FASTORE             , "fastore"         , "b"    , -3, TRAP);
        def(DASTORE             , "dastore"         , "b"    , -4, TRAP);
        def(AASTORE             , "aastore"         , "b"    , -3, TRAP);
        def(BASTORE             , "bastore"         , "b"    , -3, TRAP);
        def(CASTORE             , "castore"         , "b"    , -3, TRAP);
        def(SASTORE             , "sastore"         , "b"    , -3, TRAP);
        def(POP                 , "pop"             , "b"    , -1);
        def(POP2                , "pop2"            , "b"    , -2);
        def(DUP                 , "dup"             , "b"    ,  1);
        def(DUP_X1              , "dup_x1"          , "b"    ,  1);
        def(DUP_X2              , "dup_x2"          , "b"    ,  1);
        def(DUP2                , "dup2"            , "b"    ,  2);
        def(DUP2_X1             , "dup2_x1"         , "b"    ,  2);
        def(DUP2_X2             , "dup2_x2"         , "b"    ,  2);
        def(SWAP                , "swap"            , "b"    ,  0);
        def(IADD                , "iadd"            , "b"    , -1, COMMUTATIVE | ASSOCIATIVE);
        def(LADD                , "ladd"            , "b"    , -2, COMMUTATIVE | ASSOCIATIVE);
        def(FADD                , "fadd"            , "b"    , -1, COMMUTATIVE | ASSOCIATIVE);
        def(DADD                , "dadd"            , "b"    , -2, COMMUTATIVE | ASSOCIATIVE);
        def(ISUB                , "isub"            , "b"    , -1);
        def(LSUB                , "lsub"            , "b"    , -2);
        def(FSUB                , "fsub"            , "b"    , -1);
        def(DSUB                , "dsub"            , "b"    , -2);
        def(IMUL                , "imul"            , "b"    , -1, COMMUTATIVE | ASSOCIATIVE);
        def(LMUL                , "lmul"            , "b"    , -2, COMMUTATIVE | ASSOCIATIVE);
        def(FMUL                , "fmul"            , "b"    , -1, COMMUTATIVE | ASSOCIATIVE);
        def(DMUL                , "dmul"            , "b"    , -2, COMMUTATIVE | ASSOCIATIVE);
        def(IDIV                , "idiv"            , "b"    , -1, TRAP);
        def(LDIV                , "ldiv"            , "b"    , -2, TRAP);
        def(FDIV                , "fdiv"            , "b"    , -1);
        def(DDIV                , "ddiv"            , "b"    , -2);
        def(IREM                , "irem"            , "b"    , -1, TRAP);
        def(LREM                , "lrem"            , "b"    , -2, TRAP);
        def(FREM                , "frem"            , "b"    , -1);
        def(DREM                , "drem"            , "b"    , -2);
        def(INEG                , "ineg"            , "b"    ,  0);
        def(LNEG                , "lneg"            , "b"    ,  0);
        def(FNEG                , "fneg"            , "b"    ,  0);
        def(DNEG                , "dneg"            , "b"    ,  0);
        def(ISHL                , "ishl"            , "b"    , -1);
        def(LSHL                , "lshl"            , "b"    , -1);
        def(ISHR                , "ishr"            , "b"    , -1);
        def(LSHR                , "lshr"            , "b"    , -1);
        def(IUSHR               , "iushr"           , "b"    , -1);
        def(LUSHR               , "lushr"           , "b"    , -1);
        def(IAND                , "iand"            , "b"    , -1, COMMUTATIVE | ASSOCIATIVE);
        def(LAND                , "land"            , "b"    , -2, COMMUTATIVE | ASSOCIATIVE);
        def(IOR                 , "ior"             , "b"    , -1, COMMUTATIVE | ASSOCIATIVE);
        def(LOR                 , "lor"             , "b"    , -2, COMMUTATIVE | ASSOCIATIVE);
        def(IXOR                , "ixor"            , "b"    , -1, COMMUTATIVE | ASSOCIATIVE);
        def(LXOR                , "lxor"            , "b"    , -2, COMMUTATIVE | ASSOCIATIVE);
        def(IINC                , "iinc"            , "bic"  ,  0, LOAD | STORE);
        def(I2L                 , "i2l"             , "b"    ,  1);
        def(I2F                 , "i2f"             , "b"    ,  0);
        def(I2D                 , "i2d"             , "b"    ,  1);
        def(L2I                 , "l2i"             , "b"    , -1);
        def(L2F                 , "l2f"             , "b"    , -1);
        def(L2D                 , "l2d"             , "b"    ,  0);
        def(F2I                 , "f2i"             , "b"    ,  0);
        def(F2L                 , "f2l"             , "b"    ,  1);
        def(F2D                 , "f2d"             , "b"    ,  1);
        def(D2I                 , "d2i"             , "b"    , -1);
        def(D2L                 , "d2l"             , "b"    ,  0);
        def(D2F                 , "d2f"             , "b"    , -1);
        def(I2B                 , "i2b"             , "b"    ,  0);
        def(I2C                 , "i2c"             , "b"    ,  0);
        def(I2S                 , "i2s"             , "b"    ,  0);
        def(LCMP                , "lcmp"            , "b"    , -3);
        def(FCMPL               , "fcmpl"           , "b"    , -1);
        def(FCMPG               , "fcmpg"           , "b"    , -1);
        def(DCMPL               , "dcmpl"           , "b"    , -3);
        def(DCMPG               , "dcmpg"           , "b"    , -3);
        def(IFEQ                , "ifeq"            , "boo"  , -1, FALL_THROUGH | BRANCH);
        def(IFNE                , "ifne"            , "boo"  , -1, FALL_THROUGH | BRANCH);
        def(IFLT                , "iflt"            , "boo"  , -1, FALL_THROUGH | BRANCH);
        def(IFGE                , "ifge"            , "boo"  , -1, FALL_THROUGH | BRANCH);
        def(IFGT                , "ifgt"            , "boo"  , -1, FALL_THROUGH | BRANCH);
        def(IFLE                , "ifle"            , "boo"  , -1, FALL_THROUGH | BRANCH);
        def(IF_ICMPEQ           , "if_icmpeq"       , "boo"  , -2, COMMUTATIVE | FALL_THROUGH | BRANCH);
        def(IF_ICMPNE           , "if_icmpne"       , "boo"  , -2, COMMUTATIVE | FALL_THROUGH | BRANCH);
        def(IF_ICMPLT           , "if_icmplt"       , "boo"  , -2, FALL_THROUGH | BRANCH);
        def(IF_ICMPGE           , "if_icmpge"       , "boo"  , -2, FALL_THROUGH | BRANCH);
        def(IF_ICMPGT           , "if_icmpgt"       , "boo"  , -2, FALL_THROUGH | BRANCH);
        def(IF_ICMPLE           , "if_icmple"       , "boo"  , -2, FALL_THROUGH | BRANCH);
        def(IF_ACMPEQ           , "if_acmpeq"       , "boo"  , -2, COMMUTATIVE | FALL_THROUGH | BRANCH);
        def(IF_ACMPNE           , "if_acmpne"       , "boo"  , -2, COMMUTATIVE | FALL_THROUGH | BRANCH);
        def(GOTO                , "goto"            , "boo"  ,  0, STOP | BRANCH);
        def(JSR                 , "jsr"             , "boo"  ,  0, STOP | BRANCH);
        def(RET                 , "ret"             , "bi"   ,  0, STOP);
        def(TABLESWITCH         , "tableswitch"     , ""     , -1, STOP);
        def(LOOKUPSWITCH        , "lookupswitch"    , ""     , -1, STOP);
        def(IRETURN             , "ireturn"         , "b"    , -1, TRAP | STOP);
        def(LRETURN             , "lreturn"         , "b"    , -2, TRAP | STOP);
        def(FRETURN             , "freturn"         , "b"    , -1, TRAP | STOP);
        def(DRETURN             , "dreturn"         , "b"    , -2, TRAP | STOP);
        def(ARETURN             , "areturn"         , "b"    , -1, TRAP | STOP);
        def(RETURN              , "return"          , "b"    ,  0, TRAP | STOP);
        def(GETSTATIC           , "getstatic"       , "bjj"  ,  0, TRAP | FIELD_READ);
        def(PUTSTATIC           , "putstatic"       , "bjj"  ,  0, TRAP | FIELD_WRITE);
        def(GETFIELD            , "getfield"        , "bjj"  , -1, TRAP | FIELD_READ);
        def(PUTFIELD            , "putfield"        , "bjj"  , -1, TRAP | FIELD_WRITE);
        def(INVOKEVIRTUAL       , "invokevirtual"   , "bjj"  , -1, TRAP | INVOKE);
        def(INVOKESPECIAL       , "invokespecial"   , "bjj"  , -1, TRAP | INVOKE);
        def(INVOKESTATIC        , "invokestatic"    , "bjj"  ,  0, TRAP | INVOKE);
        def(INVOKEINTERFACE     , "invokeinterface" , "bjja_", -1, TRAP | INVOKE);
        def(INVOKEDYNAMIC       , "invokedynamic"   , "bjjjj",  0, TRAP | INVOKE);
        def(NEW                 , "new"             , "bii"  ,  1, TRAP);
        def(NEWARRAY            , "newarray"        , "bc"   ,  0, TRAP);
        def(ANEWARRAY           , "anewarray"       , "bii"  ,  0, TRAP);
        def(ARRAYLENGTH         , "arraylength"     , "b"    ,  0, TRAP);
        def(ATHROW              , "athrow"          , "b"    , -1, TRAP | STOP);
        def(CHECKCAST           , "checkcast"       , "bii"  ,  0, TRAP);
        def(INSTANCEOF          , "instanceof"      , "bii"  ,  0, TRAP);
        def(MONITORENTER        , "monitorenter"    , "b"    , -1, TRAP);
        def(MONITOREXIT         , "monitorexit"     , "b"    , -1, TRAP);
        def(WIDE                , "wide"            , ""     ,  0);
        def(MULTIANEWARRAY      , "multianewarray"  , "biic" ,  1, TRAP);
        def(IFNULL              , "ifnull"          , "boo"  , -1, FALL_THROUGH | BRANCH);
        def(IFNONNULL           , "ifnonnull"       , "boo"  , -1, FALL_THROUGH | BRANCH);
        def(GOTO_W              , "goto_w"          , "boooo",  0, STOP | BRANCH);
        def(JSR_W               , "jsr_w"           , "boooo",  0, STOP | BRANCH);
        def(BREAKPOINT          , "breakpoint"      , "b"    ,  0, TRAP);
        // @formatter:on
    }
    
    // Possible values for the type operand of the NEWARRAY instruction. §6.5
    // @formatter:off
    int
            T_BOOLEAN = 4,
            T_CHAR    = 5,
            T_FLOAT   = 6,
            T_DOUBLE  = 7,
            T_BYTE    = 8,
            T_SHORT   = 9,
            T_INT     = 10,
            T_LONG    = 11;
    // @formatter:on
    
    @HotSpotMethodFlags(_force_inline)
    static int newArrayType(final Type type) = switch (type.getSort()) {
        case Type.BOOLEAN -> T_BOOLEAN;
        case Type.CHAR    -> T_CHAR;
        case Type.BYTE    -> T_BYTE;
        case Type.SHORT   -> T_SHORT;
        case Type.INT     -> T_INT;
        case Type.FLOAT   -> T_FLOAT;
        case Type.LONG    -> T_LONG;
        case Type.DOUBLE  -> T_DOUBLE;
        default           -> throw new IllegalArgumentException(type.getDescriptor());
    };
    
    @HotSpotMethodFlags(_force_inline)
    static Type newArrayType(final int type) = switch (type) {
        case T_BOOLEAN -> Type.BOOLEAN_TYPE;
        case T_CHAR    -> Type.CHAR_TYPE;
        case T_BYTE    -> Type.BYTE_TYPE;
        case T_SHORT   -> Type.SHORT_TYPE;
        case T_INT     -> Type.INT_TYPE;
        case T_FLOAT   -> Type.FLOAT_TYPE;
        case T_LONG    -> Type.LONG_TYPE;
        case T_DOUBLE  -> Type.DOUBLE_TYPE;
        default        -> throw new IllegalArgumentException(String.valueOf(type));
    };
    
    @HotSpotMethodFlags(_force_inline)
    static int baseType(final int opcode) {
        if (isLoad(opcode))
            return ILOAD;
        if (isStore(opcode))
            return ISTORE;
        if (isReturn(opcode))
            return IRETURN;
        return opcode;
    }
    
    @HotSpotMethodFlags(_force_inline)
    static int lengthOf(final int opcode) = lengthArray[opcode];
    
    @HotSpotMethodFlags(_force_inline)
    static int stackEffectOf(final int opcode) = stackEffectArray[opcode];
    
    static String nameOf(final int opcode) throws IllegalArgumentException {
        final @Nullable String name = nameArray[opcode];
        return name == null ? "<illegal opcode: " + opcode + ">" : "<" + name + ">";
    }
    
    static int valueOf(final String name) {
        for (int opcode = 0; opcode < nameArray.length; opcode++)
            if (name.equalsIgnoreCase(nameArray[opcode]))
                return opcode;
        throw new IllegalArgumentException("No opcode for " + name);
    }
    
    static boolean valid(final int opcode) = opcode > -1 && opcode < BREAKPOINT;
    
    static boolean isCommutative(final int opcode) = valid(opcode) && (flagsArray[opcode] & COMMUTATIVE) != 0;
    
    static boolean canTrap(final int opcode) = valid(opcode) && (flagsArray[opcode] & TRAP) != 0;
    
    static boolean isLoad(final int opcode) = valid(opcode) && (flagsArray[opcode] & LOAD) != 0;
    
    static boolean isStop(final int opcode) = valid(opcode) && (flagsArray[opcode] & STOP) != 0;
    
    static boolean isInvoke(final int opcode) = valid(opcode) && (flagsArray[opcode] & INVOKE) != 0;
    
    static boolean isStore(final int opcode) = valid(opcode) && (flagsArray[opcode] & STORE) != 0;
    
    static boolean isBlockEnd(final int opcode) = valid(opcode) && (flagsArray[opcode] & (STOP | FALL_THROUGH)) != 0;
    
    static boolean isBranch(final int opcode) = valid(opcode) && (flagsArray[opcode] & BRANCH) != 0;
    
    static boolean isSwitch(final int opcode) = opcode == TABLESWITCH || opcode == LOOKUPSWITCH;
    
    static boolean isJump(final int opcode) = isBranch(opcode) || isSwitch(opcode);
    
    static boolean isConditionalBranch(final int opcode) = valid(opcode) && (flagsArray[opcode] & FALL_THROUGH) != 0;
    
    static boolean isReturn(final int opcode) = opcode >= IRETURN && opcode <= RETURN;
    
    static boolean isThrow(final int opcode) = opcode == ATHROW;
    
    static boolean isOver(final int opcode) = isReturn(opcode) || isThrow(opcode);
    
    static int smooth(final int opcode) = switch (opcode) {
        case ILOAD_0,
                ILOAD_1,
                ILOAD_2,
                ILOAD_3  -> ILOAD;
        case LLOAD_0,
                LLOAD_1,
                LLOAD_2,
                LLOAD_3  -> LLOAD;
        case FLOAD_0,
                FLOAD_1,
                FLOAD_2,
                FLOAD_3  -> FLOAD;
        case DLOAD_0,
                DLOAD_1,
                DLOAD_2,
                DLOAD_3  -> DLOAD;
        case ALOAD_0,
                ALOAD_1,
                ALOAD_2,
                ALOAD_3  -> ALOAD;
        case ISTORE_0,
                ISTORE_1,
                ISTORE_2,
                ISTORE_3 -> ISTORE;
        case LSTORE_0,
                LSTORE_1,
                LSTORE_2,
                LSTORE_3 -> LSTORE;
        case FSTORE_0,
                FSTORE_1,
                FSTORE_2,
                FSTORE_3 -> FSTORE;
        case DSTORE_0,
                DSTORE_1,
                DSTORE_2,
                DSTORE_3 -> DSTORE;
        case ASTORE_0,
                ASTORE_1,
                ASTORE_2,
                ASTORE_3 -> ASTORE;
        case LDC_W,
                LDC2_W   -> LDC;
        case GOTO_W   -> GOTO;
        default       -> opcode;
    };
    
    static Type targetType(final int opcode) = switch (opcode) {
        case ILOAD,
                ISTORE,
                IALOAD,
                IASTORE,
                IRETURN,
                IADD,
                ISUB,
                IMUL,
                IDIV,
                IREM,
                ISHL,
                ISHR,
                IUSHR,
                IAND,
                IOR,
                IXOR,
                INEG,
                I2L,
                I2F,
                I2D,
                I2B,
                I2C,
                I2S,
                IINC,
                IFEQ,
                IFNE,
                IFLT,
                IFGE,
                IFGT,
                IFLE,
                IF_ICMPEQ,
                IF_ICMPNE,
                IF_ICMPLT,
                IF_ICMPGE,
                IF_ICMPGT,
                IF_ICMPLE -> Type.INT_TYPE;
        case LLOAD,
                LSTORE,
                LALOAD,
                LASTORE,
                LRETURN,
                LADD,
                LSUB,
                LMUL,
                LDIV,
                LREM,
                LSHL,
                LSHR,
                LUSHR,
                LAND,
                LOR,
                LXOR,
                LNEG,
                L2I,
                L2F,
                L2D,
                LCMP      -> Type.LONG_TYPE;
        case FLOAD,
                FSTORE,
                FALOAD,
                FASTORE,
                FRETURN,
                FADD,
                FSUB,
                FMUL,
                FDIV,
                FREM,
                FNEG,
                F2I,
                F2L,
                F2D,
                FCMPL,
                FCMPG     -> Type.FLOAT_TYPE;
        case DLOAD,
                DSTORE,
                DALOAD,
                DASTORE,
                DRETURN,
                DADD,
                DSUB,
                DMUL,
                DDIV,
                DREM,
                DNEG,
                D2I,
                D2L,
                D2F,
                DCMPL,
                DCMPG     -> Type.DOUBLE_TYPE;
        case ALOAD,
                ASTORE,
                AALOAD,
                AASTORE,
                ARETURN,
                CHECKCAST,
                IF_ACMPEQ,
                IF_ACMPNE,
                IFNULL,
                IFNONNULL -> ASMHelper.TYPE_OBJECT;
        case BALOAD,
                BASTORE   -> Type.BYTE_TYPE;
        case CALOAD,
                CASTORE   -> Type.CHAR_TYPE;
        case SALOAD,
                SASTORE   -> Type.SHORT_TYPE;
        default        -> Type.VOID_TYPE;
    };
    
    static Type rightType(final int opcode) = switch (opcode) {
        case LSHL,
                LSHR,
                LUSHR -> Type.INT_TYPE;
        default    -> targetType(opcode);
    };
    
    static Type resultType(final int opcode) = switch (opcode) {
        case ILOAD,
                IALOAD,
                IADD,
                ISUB,
                IMUL,
                IDIV,
                IREM,
                ISHL,
                ISHR,
                IUSHR,
                IAND,
                IOR,
                IXOR,
                INEG,
                L2I,
                F2I,
                D2I,
                IINC,
                LCMP,
                FCMPL,
                FCMPG,
                DCMPL,
                DCMPG   -> Type.INT_TYPE;
        case LLOAD,
                LALOAD,
                LADD,
                LSUB,
                LMUL,
                LDIV,
                LREM,
                LSHL,
                LSHR,
                LUSHR,
                LAND,
                LOR,
                LXOR,
                LNEG,
                I2L,
                F2L,
                D2L     -> Type.LONG_TYPE;
        case FLOAD,
                FALOAD,
                FADD,
                FSUB,
                FMUL,
                FDIV,
                FREM,
                FNEG,
                I2F,
                L2F,
                D2F     -> Type.FLOAT_TYPE;
        case DLOAD,
                DALOAD,
                DADD,
                DSUB,
                DMUL,
                DDIV,
                DREM,
                DNEG,
                I2D,
                L2D,
                F2D     -> Type.DOUBLE_TYPE;
        case ALOAD,
                AALOAD,
                AASTORE -> ASMHelper.TYPE_OBJECT;
        case BALOAD,
                I2B,
                ICONST_M1,
                ICONST_0,
                ICONST_1,
                ICONST_2,
                ICONST_3,
                ICONST_4,
                ICONST_5,
                BIPUSH  -> Type.BYTE_TYPE;
        case CALOAD,
                I2C     -> Type.CHAR_TYPE;
        case SALOAD,
                I2S,
                SIPUSH  -> Type.SHORT_TYPE;
        default      -> Type.VOID_TYPE;
    };
    
    static Type arrayType(final int opcode) = switch (opcode) {
        case IALOAD,
                IASTORE -> ASMHelper.TYPE_INT_ARRAY;
        case LALOAD,
                LASTORE -> ASMHelper.TYPE_LONG_ARRAY;
        case FALOAD,
                FASTORE -> ASMHelper.TYPE_FLOAT_ARRAY;
        case DALOAD,
                DASTORE -> ASMHelper.TYPE_DOUBLE_ARRAY;
        case AALOAD,
                AASTORE -> ASMHelper.TYPE_OBJECT_ARRAY;
        case BALOAD,
                BASTORE -> ASMHelper.TYPE_BYTE_ARRAY;
        case CALOAD,
                CASTORE -> ASMHelper.TYPE_CHAR_ARRAY;
        case SALOAD,
                SASTORE -> ASMHelper.TYPE_SHORT_ARRAY;
        default      -> Type.VOID_TYPE;
    };
    
    static String operator(final int opcode) = switch (opcode) {
        case IADD,
                LADD,
                FADD,
                DADD  -> "+";
        case ISUB,
                LSUB,
                FSUB,
                DSUB  -> "-";
        case IMUL,
                LMUL,
                FMUL,
                DMUL  -> "*";
        case IDIV,
                LDIV,
                FDIV,
                DDIV  -> "/";
        case IREM,
                LREM,
                FREM,
                DREM  -> "%";
        case ISHL,
                LSHL  -> "<<";
        case ISHR,
                LSHR  -> ">>";
        case IUSHR,
                LUSHR -> ">>>";
        case IAND,
                LAND  -> "&";
        case IOR,
                LOR   -> "|";
        case IXOR,
                LXOR  -> "^";
        default    -> nameOf(opcode);
    };
    
    private static void def(final int opcode, final String name, final String format, final int stackEffect, final int flags = 0) {
        assert nameArray[opcode] == null : "opcode " + opcode + " is already bound to name " + nameArray[opcode];
        nameArray[opcode] = name;
        lengthArray[opcode] = format.length();
        stackEffectArray[opcode] = stackEffect;
        flagsArray[opcode] = flags;
        assert !isConditionalBranch(opcode) || isBranch(opcode) : "a conditional branch must also be a branch";
    }
    
    static boolean isIfBytecode(final int bytecode) = switch (bytecode) {
        case IFEQ,
                IFNE,
                IFLT,
                IFGE,
                IFGT,
                IFLE,
                IF_ICMPEQ,
                IF_ICMPNE,
                IF_ICMPLT,
                IF_ICMPGE,
                IF_ICMPGT,
                IF_ICMPLE,
                IF_ACMPEQ,
                IF_ACMPNE,
                IFNULL,
                IFNONNULL -> true;
        default        -> false;
    };
    
}
