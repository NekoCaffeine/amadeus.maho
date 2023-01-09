package amadeus.maho.util.bytecode.remap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import amadeus.maho.util.container.MapTable;
import amadeus.maho.util.throwable.AbnormalFormatException;

public interface SrgRemapHandler extends MapTableRemapHandler {
    
    static SrgRemapHandler of(final Path path) throws IOException, AbnormalFormatException {
        final MapTable<String, String, String> mapperTable = MapTable.newHashMapTable();
        final int lineCounter[] = { -1 };
        Files.lines(path).forEach(line -> {
            line = line.strip();
            lineCounter[0]++;
            final String array[] = line.split(" ");
            if (line.startsWith("PK:")) {
                if (array.length != 3)
                    throw new AbnormalFormatException("The elements of the PK map can only be three", path, lineCounter[0], 0);
                final String a = array[1];
                final String b = array[2];
                mapperTable.put(a, null, b);
                mapperTable.put(b, null, a);
            } else if (line.startsWith("CL:")) {
                if (array.length != 3)
                    throw new AbnormalFormatException("The elements of the CL map can only be three", path, lineCounter[0], 0);
                final String a = array[1];
                final String b = array[2];
                mapperTable.put(a, null, b);
                mapperTable.put(b, null, a);
            } else if (line.startsWith("MD:")) {
                if (array.length != 5)
                    throw new AbnormalFormatException("The elements of the MD map can only be five", path, lineCounter[0], 0);
                final String a = array[1];
                final String b = array[3];
                final int indexA = a.lastIndexOf('/');
                if (indexA == -1 || indexA + 1 == line.length())
                    throw new AbnormalFormatException(line, path, lineCounter[0], 1);
                final int indexB = b.lastIndexOf('/');
                if (indexB == -1 || indexB + 1 == line.length())
                    throw new AbnormalFormatException(line, path, lineCounter[0], 3);
                final String ownerA = a.substring(0, indexA), nameA = a.substring(indexA + 1), descA = array[2];
                final String ownerB = b.substring(0, indexB), nameB = b.substring(indexB + 1), descB = array[4];
                mapperTable.put(ownerA, nameA, nameB);
                mapperTable.put(ownerB, nameB, nameA);
                mapperTable.put(ownerA, nameA + descA, nameB);
                mapperTable.put(ownerB, nameB + descB, nameA);
            } else if (line.startsWith("FD:")) {
                if (array.length != 3)
                    throw new AbnormalFormatException("The elements of the FD map can only be three", path, lineCounter[0], 0);
                final String a = array[1];
                final String b = array[2];
                final int indexA = a.lastIndexOf('/');
                if (indexA == -1 || indexA + 1 == line.length())
                    throw new AbnormalFormatException(line, path, lineCounter[0], 1);
                final int indexB = b.lastIndexOf('/');
                if (indexB == -1 || indexB + 1 == line.length())
                    throw new AbnormalFormatException(line, path, lineCounter[0], 2);
                final String ownerA = a.substring(0, indexA), nameA = a.substring(indexA + 1);
                final String ownerB = b.substring(0, indexB), nameB = b.substring(indexB + 1);
                mapperTable.put(ownerA, nameA, nameB);
                mapperTable.put(ownerB, nameB, nameA);
            } else if (!line.startsWith("#"))
                throw new AbnormalFormatException(line, path, lineCounter[0]);
        });
        return () -> mapperTable;
    }
    
}
