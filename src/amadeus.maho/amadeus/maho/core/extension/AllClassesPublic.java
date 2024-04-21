package amadeus.maho.core.extension;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.inspection.Nullable;

import static org.objectweb.asm.Opcodes.*;

public enum AllClassesPublic implements ClassFileTransformer {
    
    @Getter
    instance;
    
    @Override
    public @Nullable byte[] transform(final @Nullable ClassLoader loader, final @Nullable String name, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain, final @Nullable byte bytecode[]) {
        if (clazz == null && bytecode != null) {
            final ClassReader reader = { bytecode };
            bytecode[reader.header + 1] = (byte) (bytecode[reader.header + 1] & ~0b110 | 0b1);
            final ClassWriter writer = { 0 };
            reader.accept(new ClassVisitor(ASM9, writer) {
                
                @Override
                public void visitInnerClass(final String name, final String outerName, final String innerName, final int access)
                        = super.visitInnerClass(name, outerName, innerName, access & ~0b110 | 0b1);
                
                @Override
                public MethodVisitor visitMethod(final int access, final String name, final String descriptor, final String signature, final String[] exceptions)
                        = super.visitMethod((access & (ACC_PUBLIC | ACC_PROTECTED | ACC_PRIVATE)) == 0 ? access | ACC_PROTECTED : access, name, descriptor, signature, exceptions);
    
                @Override
                public FieldVisitor visitField(final int access, final String name, final String descriptor, final String signature, final Object value)
                        = super.visitField((access & (ACC_PUBLIC | ACC_PROTECTED | ACC_PRIVATE)) == 0 ? access | ACC_PROTECTED : access, name, descriptor, signature, value);
                
            }, 0);
            return writer.toByteArray();
        }
        return null;
    }
    
}
