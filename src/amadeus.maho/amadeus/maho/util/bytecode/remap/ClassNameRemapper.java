package amadeus.maho.util.bytecode.remap;

import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.ClassWriter;

public class ClassNameRemapper extends Remapper {
    
    protected final String srcName, newName;
    
    public ClassNameRemapper(final String srcName, final String newName) {
        this.srcName = ASMHelper.className(srcName);
        this.newName = ASMHelper.className(newName);
    }
    
    @Override
    public String map(final String typeName) = srcName.equals(typeName) ? newName : super.map(typeName);
    
    public static byte[] changeName(final byte data[], final String srcName, final String newName)
            = ClassWriter.toBytecode(visitor -> ASMHelper.newClassReader(data).accept(new ClassRemapper(visitor, new ClassNameRemapper(srcName, newName)), 0));
    
    public static ClassNode changeName(final ClassNode node, final String srcName, final String newName) = new ClassNode().let(it -> node.accept(new ClassRemapper(it, new ClassNameRemapper(srcName, newName))));
    
}
