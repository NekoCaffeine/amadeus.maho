package amadeus.maho.util.shell;

import java.util.Map;

import jdk.jshell.execution.DirectExecutionControl;
import jdk.jshell.spi.ExecutionControlProvider;
import jdk.jshell.spi.ExecutionEnv;

public class MahoExecutionControlProvider implements ExecutionControlProvider {
    
    @Override
    public String name() = "maho";
    
    @Override
    public DirectExecutionControl generate(final ExecutionEnv env, final Map<String, String> parameters) throws Throwable = { new ClassLoaderDelegate() };
    
}
