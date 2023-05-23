package amadeus.maho.simulation.lookup;

import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.core.extension.DynamicLinkingContext;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.simulation.dynamic.DynamicSimulator;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Proxy;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.InvisibleType;
import amadeus.maho.util.annotation.mark.WIP;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.function.FunctionHelper;
import amadeus.maho.util.runtime.DebugHelper;

import static org.objectweb.asm.Opcodes.*;

@WIP
// @Preload(initialized = true)
// @TransformProvider
public interface LookupSimulator {
    
    @ToString
    @EqualsAndHashCode
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
    class LookupTarget {
        
        byte refKind;
        
        Class<?> clazz;
        String   name;
        // Field => Class, Method => MethodType
        Object   type;
        
        public static int index(final byte refKind) = refKindIsSetter(refKind) ? 1 : 0;
        
    }
    
    @ToString
    @EqualsAndHashCode
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
    class SimulationMember {
        
        int          access;
        String       name;
        // Field => Class, Method => MethodType
        Object       type;
        LookupTarget lookupTarget[];
        
    }
    
    String
            DirectMethodHandle = "java.lang.invoke.DirectMethodHandle",
            MemberName         = "java.lang.invoke.MemberName",
            MemberName$Factory = "java.lang.invoke.MemberName$Factory";
    
    // Constant pool reference-kind codes, of used by CONSTANT_MethodHandle CP entries.
    byte
            REF_NONE             = 0,  // null set
            REF_getField         = 1,
            REF_getStatic        = 2,
            REF_putField         = 3,
            REF_putStatic        = 4,
            REF_invokeVirtual    = 5,
            REF_invokeStatic     = 6,
            REF_invokeSpecial    = 7,
            REF_newInvokeSpecial = 8,
            REF_invokeInterface  = 9,
            REF_LIMIT            = 10;
    
    static boolean refKindIsValid(final int refKind) = refKind > REF_NONE && refKind < REF_LIMIT;
    
    static boolean refKindIsField(final byte refKind) = refKind > REF_NONE && refKind < REF_invokeVirtual;
    
    static boolean refKindIsGetter(final byte refKind) = refKind == REF_getField || refKind == REF_getStatic;
    
    static boolean refKindIsSetter(final byte refKind) = refKind == REF_putField || refKind == REF_putStatic;
    
    static boolean refKindIsMethod(final byte refKind) = refKind > REF_putStatic && refKind < REF_LIMIT;
    
    static boolean refKindIsConstructor(final byte refKind) = refKind == REF_newInvokeSpecial;
    
    static boolean refKindHasReceiver(final byte refKind) = (refKind & 1) != 0;
    
    static boolean refKindIsStatic(final byte refKind) = !refKindHasReceiver(refKind) && refKind != REF_newInvokeSpecial;
    
    static boolean refKindDoesDispatch(final byte refKind) = refKind == REF_invokeVirtual || refKind == REF_invokeInterface;
    
    @Getter
    WeakHashMap<Class<?>, List<String>> hiddenMembersMapping = { };
    
    @Getter
    WeakHashMap<Class<?>, List<SimulationMember>> injectMembersMapping = { };
    
    static void addHiddenMember(final Class<?> owner, final String memberName) = hiddenMembersMapping().computeIfAbsent(owner, FunctionHelper.abandon(ArrayList::new)) += memberName;
    
    static void addInjectMember(final Class<?> owner, final SimulationMember member) = injectMembersMapping().computeIfAbsent(owner, FunctionHelper.abandon(ArrayList::new)) += member;
    
    @SneakyThrows
    static void addInjectField(final Class<?> owner, final FieldNode fieldNode) {
        final boolean isStatic = ASMHelper.anyMatch(fieldNode.access, ACC_STATIC);
        final String targetName = DynamicSimulator.redirectTable().get(ASMHelper.className(owner), fieldNode.name);
        final Class<?> targetClass = Class.forName(targetName.replace('/', '.'), true, owner.getClassLoader());
        final Class<?> fieldType = ASMHelper.loadType(Type.getType(fieldNode.desc), false, owner.getClassLoader());
        final LookupTarget targets[] = {
                new LookupTarget(REF_invokeStatic, targetClass,
                        DynamicSimulator.GET_METHOD, isStatic ?
                        MethodType.methodType(fieldType) :
                        MethodType.methodType(fieldType, owner)),
                new LookupTarget(REF_invokeStatic, targetClass,
                        DynamicSimulator.SET_METHOD, isStatic ?
                        MethodType.methodType(void.class, fieldType) :
                        MethodType.methodType(void.class, owner, fieldType))
        };
        final SimulationMember inject = { fieldNode.access, fieldNode.name, fieldType, targets };
        addInjectMember(owner, inject);
    }
    
    @SneakyThrows
    static void addInjectMethod(final Class<?> owner, final MethodNode methodNode) {
        final boolean isStatic = ASMHelper.anyMatch(methodNode.access, ACC_STATIC);
        final boolean isConstructor = methodNode.name.equals(ASMHelper._INIT_);
        final String targetName = DynamicSimulator.redirectTable().get(ASMHelper.className(owner), methodNode.name + methodNode.desc);
        final Class<?> targetClass = Class.forName(targetName.replace('/', '.'), true, owner.getClassLoader());
        final MethodType methodType = ASMHelper.loadMethodType(methodNode.desc, false, owner.getClassLoader());
        final LookupTarget targets[] = {
                isStatic ?
                        new LookupTarget(REF_invokeStatic, targetClass, methodNode.name, methodType) :
                        isConstructor ?
                                new LookupTarget(REF_invokeStatic, targetClass, DynamicSimulator.CONSTRUCTOR_METHOD, methodType.changeReturnType(owner)) :
                                new LookupTarget(REF_invokeStatic, targetClass, methodNode.name, methodType.insertParameterTypes(0, owner))
        };
        final SimulationMember inject = { methodNode.access, methodNode.name, methodType, targets };
        addInjectMember(owner, inject);
    }
    
    @WIP
    static void getMembers(final List<@InvisibleType(MemberName) Object> result,
            final Class<?> defc, final String matchName, final Object matchType,
            final int matchFlags, final Class<?> lookupClass) = Thread.dumpStack();
    
    static boolean resolve(final Class<?> clazz, final String name, final Object type, final boolean speculativeResolve, final Object pMemberName[]) {
        final @Nullable List<String> hiddenMembers = hiddenMembersMapping()[clazz];
        if (hiddenMembers != null && hiddenMembers.contains(name)) {
            pMemberName[0] = clone(pMemberName[0]);
            resolution(pMemberName[0], makeLinkageError(clazz, name, type));
            return true;
        }
        final @Nullable List<SimulationMember> injectMembers = injectMembersMapping()[clazz];
        if (injectMembers != null)
            for (final SimulationMember member : injectMembers)
                if (member.name.equals(name) && type.equals(member.type)) {
                    pMemberName[0] = clone(pMemberName[0]);
                    resolution(pMemberName[0], null);
                    return true;
                }
        return false;
    }
    
    static @InvisibleType(MemberName) Object assumeControl(@InvisibleType(MemberName) Object result,
            final byte refKind, final Class<?> receiver, final Class<?> callerClass, final Class<?> clazz, final String name, final Object type) {
        final @Nullable List<SimulationMember> injectMembers = injectMembersMapping()[clazz];
        if (injectMembers != null)
            for (final SimulationMember member : injectMembers)
                if (member.name.equals(name) && member.type.equals(type)) {
                    final LookupTarget target = member.lookupTarget[LookupTarget.index(refKind)];
                    result = newMemberName(target.refKind, target.clazz, target.name, target.type);
                    flags(result, flags(result) | member.access);
                    resolution(result, null);
                }
        return result;
    }
    
    private static LinkageError makeLinkageError(final Class<?> owner, final String name, final Object type) {
        final String msg = owner + "#" + name + " " + type;
        if (type instanceof Class)
            return new NoSuchFieldError(msg);
        if (type instanceof MethodType)
            return new NoSuchMethodError(msg);
        return { msg, new UnsupportedOperationException(type + " => " + type.getClass()) };
    }
    
    @Proxy(INVOKEVIRTUAL)
    private static @InvisibleType(MemberName) Object clone(final @InvisibleType(MemberName) Object $this) = DebugHelper.breakpointThenError();
    
    @Proxy(PUTFIELD)
    private static void resolution(final @InvisibleType(MemberName) Object $this, final Object resolution) = DebugHelper.breakpointThenError();
    
    @Proxy(PUTFIELD)
    private static void flags(final @InvisibleType(MemberName) Object $this, final int flags) = DebugHelper.breakpointThenError();
    
    @Proxy(GETFIELD)
    private static int flags(final @InvisibleType(MemberName) Object $this) = DebugHelper.breakpointThenError();
    
    @Proxy(NEW)
    private static @InvisibleType(MemberName) Object newMemberName(final byte refKind, final Class<?> defClass, final String name, final Object type) = DebugHelper.breakpointThenError();
    
    @Proxy(GETFIELD)
    private static Class<?> clazz(final @InvisibleType(MemberName) Object $this) = DebugHelper.breakpointThenError();
    
    @Proxy(GETFIELD)
    private static String name(final @InvisibleType(MemberName) Object $this) = DebugHelper.breakpointThenError();
    
    @Proxy(GETFIELD)
    private static Object type(final @InvisibleType(MemberName) Object $this) = DebugHelper.breakpointThenError();
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true, avoidRecursion = true)
    static void getMembers(final List<@InvisibleType(MemberName) Object> result, final @InvisibleType(MemberName$Factory) Object $this, final Class<?> defc, final String matchName,
            final Object matchType, final int matchFlags, final Class<?> lookupClass) {
        if (DynamicLinkingContext.shouldAvoidRecursion())
            return;
        getMembers(result, defc, matchName, matchType, matchFlags, lookupClass);
    }
    
    @Hook(avoidRecursion = true)
    static Hook.Result resolve(final @InvisibleType(MemberName$Factory) Object $this, final byte refKind, final @InvisibleType(MemberName) Object ref, final Class<?> lookupClass, final boolean speculativeResolve) {
        if (DynamicLinkingContext.shouldAvoidRecursion())
            return Hook.Result.VOID;
        final Object p_memberName[] = { ref };
        if (!resolve(clazz(ref), name(ref), type(ref), speculativeResolve, p_memberName))
            return Hook.Result.VOID;
        return { p_memberName[0] };
    }
    
    @Hook(target = DirectMethodHandle, isStatic = true, avoidRecursion = true)
    static Hook.Result make(final byte refKind, @Hook.Reference Class<?> receiver, @Hook.Reference @InvisibleType(MemberName) Object member, final Class<?> callerClass) {
        final @InvisibleType(MemberName) Object result = assumeControl(member, refKind, receiver, callerClass, clazz(member), name(member), type(member));
        if (member == result)
            return Hook.Result.VOID;
        receiver = clazz(result);
        member = result;
        return { };
    }
    
}
