package amadeus.maho.util.bytecode.traverser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FrameNode;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.bytecode.FrameNodeMergeException;
import amadeus.maho.util.bytecode.traverser.exception.ComputeException;
import amadeus.maho.util.bytecode.traverser.exception.FrameMergeException;

import static amadeus.maho.util.bytecode.FrameHelper.*;
import static amadeus.maho.util.math.MathHelper.*;

@Setter
@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Frame {
    
    @EqualsAndHashCode
    public record Snapshot(AbstractInsnNode insn, List<Object> locals, List<Object> stack) {
        
        public FrameNode node() = full(locals().toArray(), stack().toArray());
        
        public FrameNode diff(final Frame.Snapshot next) {
            if (locals().equals(next.locals())) {
                final List<Object> nextStack = next.stack();
                if (nextStack.isEmpty())
                    return same();
                if (nextStack.size() == 1)
                    return same1(nextStack[0]);
            } else if (next.stack().isEmpty()) {
                final List<Object> prevLocals = locals(), nextLocals = next.locals();
                final ListIterator<Object> prevIterator = prevLocals.listIterator(), iterator = nextLocals.listIterator();
                final int min = min(nextLocals.size(), prevLocals.size());
                for (int i = 0; i < min; i++)
                    if (!iterator.next().equals(prevIterator.next()))
                        return next.node();
                final int size = nextLocals.size(), prevSize = prevLocals.size(), absDiff = abs(size - prevSize);
                if (absDiff == 0 || absDiff > 3)
                    return next.node();
                if (size > prevSize)
                    return append(nextLocals.stream().skip(min).toArray());
                else // if (size < prevSize)
                    return chop(absDiff);
            }
            return next.node();
        }
        
        @Override
        public String toString() = "Snapshot{locals=%s, stack=%s}".formatted(FrameNodeMergeException.objects(locals()), FrameNodeMergeException.objects(stack()));
        
    }
    
    @Default
    @Nullable AbstractInsnNode insn = null;
    
    public <T extends AbstractInsnNode> T insn() = (T) insn;
    
    final LinkedList<TypeOwner> locals = { }, stack = { };
    
    public final self markInsn(final AbstractInsnNode insn) = insn(insn);
    
    public void locals(final TypeOwner owner) = locals().let(Collection::clear) += owner;
    
    public void locals(final TypeOwner... owners) = locals().let(Collection::clear) *= List.of(owners);
    
    public void stack(final TypeOwner owner) = stack().let(Collection::clear) += owner;
    
    public void stack(final TypeOwner... owners) = stack().let(Collection::clear) *= List.of(owners);
    
    public Frame dup(final AbstractInsnNode insn = insn()) = new Frame(insn).let(result -> {
        locals().forEach(result.locals()::add);
        stack().forEach(result.stack()::add);
    });
    
    public Frame dup(final AbstractInsnNode insn = insn(), final TypeOwner... owners) = new Frame(insn).let(result -> {
        locals().forEach(result.locals()::add);
        final Deque<TypeOwner> stack = result.stack();
        for (final TypeOwner owner : owners)
            stack << owner;
    });
    
    public final void merge(final Frame target, final BinaryOperator<String> getCommonSuperClass) throws FrameMergeException {
        if (stack().size() != target.stack().size())
            throw new FrameMergeException("Different lengths: " + stack().size() + " - " + target.stack().size(), this, target);
        merge(getCommonSuperClass, stack(), target.stack());
        merge(getCommonSuperClass, locals(), target.locals());
    }
    
    /*
        §4.10.1.2. Verification Type System
            The type checker enforces a type system based upon a hierarchy of verification types, illustrated below.
            Verification type hierarchy:
            
                                             top
                                 ____________/\____________
                                /                          \
                               /                            \
                            oneWord                       twoWord
                           /   |   \                     /       \
                          /    |    \                   /         \
                        int  float  reference        long        double
                                     /     \
                                    /       \_____________
                                   /                      \
                                  /                        \
                           uninitialized                    +------------------+
                            /         \                     |  Java reference  |
                           /           \                    |  type hierarchy  |
                uninitializedThis  uninitialized(Offset)    +------------------+
                                                                     |
                                                                     |
                                                                    null
     */
    public static void merge(final BinaryOperator<String> getCommonSuperClass, final List<TypeOwner> aList, final List<TypeOwner> bList) {
        final LinkedList<TypeOwner> result = { };
        for (final Iterator<TypeOwner> aIterator = aList.iterator(), bIterator = bList.iterator(); aIterator.hasNext() && bIterator.hasNext(); ) {
            final TypeOwner a = aIterator.next(), b = bIterator.next();
            if (a == TypeOwner.INVALID || b == TypeOwner.INVALID)
                result += TypeOwner.INVALID;
            else if (a.equals(b))
                result += a;
            else if (a.isReferenceType() && b.isReferenceType()) {
                if (a == TypeOwner.NULL)
                    result += b;
                else if (b == TypeOwner.NULL)
                    result += a;
                else if (a.type().equals(b.type()))
                    result += a.flag() == null ? a : b;
                else
                    result += TypeOwner.of(Type.getObjectType(getCommonSuperClass.apply(a.type().getInternalName(), b.type().getInternalName())));
            } else {
                int aSize = a.type().getSize(), bSize = b.type().getSize();
                while (true) {
                    if (aSize == bSize) {
                        for (int i = 0; i < aSize; i++)
                            result += TypeOwner.INVALID;
                        break;
                    }
                    if (aSize < bSize) {
                        if (aIterator.hasNext())
                            aSize += aIterator.next().type().getSize();
                        else
                            bSize = aSize;
                    } else /* if (aSize > bSize) */ {
                        if (bIterator.hasNext())
                            bSize += bIterator.next().type().getSize();
                        else
                            aSize = bSize;
                    }
                }
            }
        }
        aList >>>= result;
    }
    
    public int localsSize() = locals().stream().map(TypeOwner::type).mapToInt(Type::getSize).sum();
    
    public int stackSize() = stack().stream().map(TypeOwner::type).mapToInt(Type::getSize).sum();
    
    protected Object frameType(final TypeOwner owner) {
        if (owner == TypeOwner.INVALID)
            return Opcodes.TOP;
        if (owner == TypeOwner.NULL)
            return Opcodes.NULL;
        if (owner.flag() != null)
            return owner.flag();
        return info(owner.type());
    }
    
    protected List<Object> snapshot(final List<TypeOwner> owners) {
        final ArrayList<Object> result = { owners.size() };
        for (final TypeOwner owner : owners)
            result += frameType(owner);
        return result;
    }
    
    public Snapshot snapshot(final AbstractInsnNode insn = insn()) = { insn, snapshot(locals()), snapshot(stack()) };
    
    public void clear() = stack.clear();
    
    public void map(final UnaryOperator<TypeOwner> mapper) {
        locals().replaceAll(mapper);
        stack().replaceAll(mapper);
    }
    
    public void erase(final Object flag) {
        final TypeOwner p_owner[] = { null };
        map(owner -> owner.flag() == flag ? p_owner[0] == null ? p_owner[0] = owner.erase() : p_owner[0] : owner);
    }
    
    public void push(final TypeOwner value) = stack().addLast(value);
    
    public TypeOwner pop() {
        try {
            return stack().removeLast();
        } catch (final NoSuchElementException e) { throw new ComputeException.Pop(this); }
    }
    
    public TypeOwner fetch() {
        try {
            return stack().getLast();
        } catch (final NoSuchElementException e) { throw new ComputeException.Fetch(this, 0); }
    }
    
    public TypeOwner fetch(final int offset) {
        try {
            final LinkedList<TypeOwner> stack = stack();
            return offset == 0 ? stack.getLast() : stack.get(stack.size() - offset - 1);
        } catch (final NoSuchElementException e) { throw new ComputeException.Fetch(this, offset); }
    }
    
    public void insert(final int offset, final TypeOwner value) {
        final LinkedList<TypeOwner> stack = stack();
        if (offset == 0)
            stack.addLast(value);
        else
            stack.add(stack.size() - offset - 1, value);
    }
    
    public TypeOwner load(final int local) {
        int context = 0;
        for (final TypeOwner owner : locals()) {
            if (context > local)
                throw new ComputeException.Load(this, local);
            if (context == local) {
                if (owner == TypeOwner.INVALID)
                    throw new ComputeException.Load(this, local);
                return owner;
            }
            context += owner.type().getSize();
        }
        throw new ComputeException.Load(this, local);
    }
    
    public TypeOwner store(final int local, final TypeOwner value) {
        int index = 0, mark;
        for (final ListIterator<TypeOwner> iterator = locals().listIterator(); iterator.hasNext(); ) {
            final TypeOwner owner = iterator.next();
            if (index == local) {
                final int sourceSize = owner.type().getSize(), valueSize = value.type().getSize();
                iterator.set(value);
                store(iterator, sourceSize, valueSize);
                return value;
            } else {
                mark = index;
                index += owner.type().getSize();
                if (index > local) {
                    final int sourceSize = index - local, valueSize = value.type().getSize();
                    iterator.set(TypeOwner.INVALID);
                    for (int i = local - mark - 1; i > 0; i--)
                        iterator.add(TypeOwner.INVALID);
                    iterator.add(value);
                    store(iterator, sourceSize, valueSize);
                    return value;
                }
            }
        }
        for (int i = index; i < local; i++)
            locals() += TypeOwner.INVALID;
        return value.let(locals()::add);
    }
    
    private static void store(final ListIterator<TypeOwner> iterator, final int sourceSize, final int valueSize) {
        if (sourceSize > valueSize) {
            for (int i = sourceSize - valueSize; i > 0; i--)
                iterator.add(TypeOwner.INVALID);
        } else if (sourceSize < valueSize)
            for (int i = valueSize - sourceSize; i > 0 && iterator.hasNext(); ) {
                final TypeOwner next = iterator.next();
                final int nextSize = next.type().getSize();
                i -= nextSize;
                iterator.remove();
                if (i < 0)
                    for (int k = i; k < 0; k++)
                        iterator.add(TypeOwner.INVALID);
            }
    }
    
}
