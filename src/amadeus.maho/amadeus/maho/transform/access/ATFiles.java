package amadeus.maho.transform.access;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.container.MapTable;
import amadeus.maho.util.misc.ConstantLookup;
import amadeus.maho.util.runtime.ReflectionHelper;
import amadeus.maho.util.throwable.AbnormalFormatException;

public interface ATFiles {
    
    ConstantLookup lookup = new ConstantLookup().recording(ReflectionHelper.class);
    
    @SneakyThrows
    static MapTable<String, String, ATRule> parseATRules(final Path atFile) throws IOException {
        final MapTable<String, String, ATRule> result = MapTable.newHashMapTable();
        final int lineCounter[] = { -1 };
        Files.lines(atFile).forEach(line -> {
            lineCounter[0]++;
            final String array[] = line.split(" ");
            if (array.length < 2)
                throw new AbnormalFormatException(STR."Invalid number of parameters(\{array.length}).", atFile, lineCounter[0]);
            boolean flag = false;
            int accessModifier = 0, addModifier = 0, delModifier = 0;
            for (int i = 0, len = array.length - 1; i < len; i++) {
                final String string = array[i];
                if (!string.isEmpty()) {
                    final String name = switch (string.charAt(0)) {
                        case '+', '-' -> string.substring(1);
                        default -> string;
                    };
                    final @Nullable Integer access = (Integer) lookup.lookupValue(string);
                    if (access != null) {
                        if (access > ReflectionHelper.PROTECTED)
                            switch (string.charAt(0)) {
                                case '+' -> addModifier |= access;
                                case '-' -> delModifier |= access;
                                default -> throw new AbnormalFormatException(STR."Missing pre-marker '+' or '-': \{string}", atFile, lineCounter[0]);
                            }
                        else if (string != name)
                            throw new AbnormalFormatException(STR."The permission modifier cannot apply the marker '+' or '-': \{string}", atFile, lineCounter[0]);
                        else {
                            if (i != 0)
                                throw new AbnormalFormatException(STR."The permission modifier must be in the first one: \{string}", atFile, lineCounter[0]);
                            accessModifier = access;
                        }
                    }
                } else if (i != len - 1 || array.length < 3)
                    throw new AbnormalFormatException(STR."Invalid parameter: \{string}", atFile, lineCounter[0]);
                else
                    flag = true;
                final ATRule atRule = { accessModifier, addModifier, delModifier };
                atRule.debugSources().addLast(atFile.toRealPath().toString());
                if (flag)
                    result.put(array[array.length - 2], array[array.length - 1], atRule);
                else
                    result.put(array[array.length - 1], null, atRule);
            }
        });
        return result;
    }
    
}
