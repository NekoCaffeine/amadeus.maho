import jdk.jshell.spi.*;

import amadeus.maho.lang.*;
import amadeus.maho.transform.mark.base.*;
import amadeus.maho.util.shell.*;

@TransformProvider
@Extension.Provider
open module amadeus.maho {
    
    requires transitive java.instrument;
    requires transitive java.management;
    requires transitive java.net.http;
    
    requires transitive jdk.compiler;
    requires transitive jdk.jlink;
    requires transitive jdk.management;
    requires transitive jdk.jshell;
    requires transitive jdk.zipfs;
    
    requires transitive jdk.incubator.foreign;
    requires transitive jdk.incubator.vector;
    
    requires transitive org.objectweb.asm;
    requires transitive org.objectweb.asm.tree;
    requires transitive org.objectweb.asm.commons;
    requires transitive org.objectweb.asm.util;
    
    requires transitive com.sun.jna;
    requires transitive com.sun.jna.platform;
    
    requires static jdk.unsupported;
    
    provides ExecutionControlProvider with MahoExecutionControlProvider;
    
}
