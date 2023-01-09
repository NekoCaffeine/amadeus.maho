package amadeus.maho.util.bytecode.type;

import java.util.function.Predicate;
import java.util.stream.IntStream;

import org.objectweb.asm.TypePath;

@FunctionalInterface
public interface TypePathFilter extends Predicate<TypePath> {
    
    static TypePathFilter of(final int... typePathContainer) {
        final boolean flag[] = { false };
        final int length = IntStream.of(typePathContainer)
                .map(path -> {
                    if (flag[0]) {
                        flag[0] = false;
                        return 0;
                    }
                    if (path == TypePath.TYPE_ARGUMENT)
                        flag[0] = true;
                    return 1;
                }).sum();
        return typePath -> {
            if (typePath == null)
                return length == 0;
            if (typePath.getLength() != length)
                return false;
            for (int i = 0, ci = 0; i < length; i++, ci++)
                if (typePath.getStep(i) != typePathContainer[ci])
                    return false;
                else if (typePathContainer[ci] == TypePath.TYPE_ARGUMENT && typePath.getStepArgument(i) != typePathContainer[++ci])
                    return false;
            return true;
        };
    }
    
}
