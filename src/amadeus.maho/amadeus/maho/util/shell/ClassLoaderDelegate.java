package amadeus.maho.util.shell;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.CodeSource;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jdk.jshell.execution.LoaderDelegate;
import jdk.jshell.spi.ExecutionControl;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.TransformerManager;

@ToString
@EqualsAndHashCode
public record ClassLoaderDelegate(ShellClassLoader loader = { Thread.currentThread().getContextClassLoader() }, Map<String, Class<?>> classes = new HashMap<>()) implements LoaderDelegate {
    
    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class ShellClassLoader extends URLClassLoader {
    
        @ToString
        @EqualsAndHashCode
        private record ClassFile(byte data[], long timestamp) { }
        
        Map<String, ShellClassLoader.ClassFile> classFiles = new HashMap<>();
        
        public ShellClassLoader(final @Nullable ClassLoader parent = null, final String name = "shell") = super(name, new URL[0], parent);
    
        @Getter
        @AllArgsConstructor
        @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
        public class ResourceURLStreamHandler extends URLStreamHandler {
            
            @NoArgsConstructor
            @FieldDefaults(level = AccessLevel.PRIVATE)
            public class Connection extends URLConnection {
                
                InputStream in;
                Map<String, List<String>> fields;
                List<String> fieldNames;
                
                @Override
                public void connect() {
                    if (connected)
                        return;
                    connected = true;
                    final ShellClassLoader.ClassFile file = classFiles[name];
                    in = new ByteArrayInputStream(file.data());
                    fields = new LinkedHashMap<>();
                    fields.put("content-length", List.of(Integer.toString(file.data().length)));
                    final String timeStamp = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(file.timestamp()), ZoneId.of("GMT")));
                    fields.put("date", List.of(timeStamp));
                    fields.put("last-modified", List.of(timeStamp));
                    fieldNames = new ArrayList<>(fields.keySet());
                }
                
                @Override
                public InputStream getInputStream() throws IOException {
                    connect();
                    return in;
                }
                
                @Override
                public String getHeaderField(final String name) {
                    connect();
                    return fields.getOrDefault(name, List.of())
                            .stream()
                            .findFirst()
                            .orElse(null);
                }
                
                @Override
                public Map<String, List<String>> getHeaderFields() {
                    connect();
                    return fields;
                }
                
                @Override
                public String getHeaderFieldKey(final int n) = n < fieldNames.size() ? fieldNames.get(n) : null;
                
                @Override
                public String getHeaderField(final int n) {
                    final String name = getHeaderFieldKey(n);
                    return name != null ? getHeaderField(name) : null;
                }
                
            }
            
            String name;
            
            @Override
            protected URLConnection openConnection(final URL u) throws IOException = new Connection(u);
        
        }
        
        public void declare(final String name, final byte bytes[]) {
            classFiles[toResourceString(name)] = { bytes, System.currentTimeMillis() };
            TransformerManager.DebugDumper.dumpBytecode(name, bytes, TransformerManager.DebugDumper.dump_shell);
        }
    
        @Override
        protected Class<?> findClass(final String name) throws ClassNotFoundException {
            final ShellClassLoader.ClassFile file = classFiles[toResourceString(name)];
            if (file == null)
                return super.findClass(name);
            return defineClass(name, file.data(), 0, file.data().length, (CodeSource) null);
        }
        
        @Override
        public URL findResource(final String name) {
            final URL u = doFindResource(name);
            return u != null ? u : super.findResource(name);
        }
        
        @Override
        public Enumeration<URL> findResources(final String name) throws IOException {
            final URL u = doFindResource(name);
            final Enumeration<URL> sup = super.findResources(name);
            if (u == null)
                return sup;
            final List<URL> result = new ArrayList<>();
            while (sup.hasMoreElements())
                result.add(sup.nextElement());
            result.add(u);
            return Collections.enumeration(result);
        }
        
        private URL doFindResource(final String name) {
            if (classFiles.containsKey(name))
                try {
                    return { null, new URI("jshell", null, "/" + name, null).toString(), new ResourceURLStreamHandler(name) };
                } catch (final MalformedURLException | URISyntaxException ex) { throw new InternalError(ex); }
            return null;
        }
        
        private String toResourceString(final String className) = className.replace('.', '/') + ".class";
        
        @Override
        public void addURL(final URL url) = super.addURL(url);
    
    }
    
    @Override
    public void load(final ExecutionControl.ClassBytecodes bytecodes[]) throws ExecutionControl.ClassInstallException, ExecutionControl.EngineTerminationException {
        final boolean loaded[] = new boolean[bytecodes.length];
        try {
            for (final ExecutionControl.ClassBytecodes cbc : bytecodes)
                loader.declare(cbc.name(), cbc.bytecodes());
            for (int i = 0; i < bytecodes.length; i++) {
                final ExecutionControl.ClassBytecodes cbc = bytecodes[i];
                final Class<?> klass = loader.loadClass(cbc.name());
                classes.put(cbc.name(), klass);
                loaded[i] = true;
                // Get class loaded to the point of, at least, preparation
                klass.getDeclaredMethods();
            }
        } catch (final Throwable ex) { throw new ExecutionControl.ClassInstallException("load: " + ex.getMessage(), loaded); }
    }
    
    @Override
    public void classesRedefined(final ExecutionControl.ClassBytecodes bytecodes[]) {
        for (final ExecutionControl.ClassBytecodes cbc : bytecodes)
            loader.declare(cbc.name(), cbc.bytecodes());
    }
    
    @Override
    public void addToClasspath(final String cp) throws ExecutionControl.EngineTerminationException, ExecutionControl.InternalException {
        try {
            for (final String path : cp.split(File.pathSeparator))
                loader.addURL(new File(path).toURI().toURL());
        } catch (final Exception ex) { throw new ExecutionControl.InternalException(ex.toString()); }
    }
    
    @Override
    public Class<?> findClass(final String name) throws ClassNotFoundException {
        final Class<?> klass = classes[name];
        if (klass == null)
            throw new ClassNotFoundException(name + " not found");
        else
            return klass;
    }
    
}
