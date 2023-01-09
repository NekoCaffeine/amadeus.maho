package amadeus.maho.util.runtime;

import java.util.function.Consumer;

import amadeus.maho.lang.Extension;
import amadeus.maho.lang.inspection.Nullable;

@Extension
public interface RunnableHelper {
    
    @Extension.Operator(">>")
    static Runnable before(final @Nullable Runnable $this, final @Nullable Runnable before) {
        if ($this == null)
            return before;
        if (before == null)
            return $this;
        return () -> {
            before.run();
            $this.run();
        };
    }
    
    @Extension.Operator("<<")
    static Runnable after(final @Nullable Runnable $this, final @Nullable Runnable after) {
        if ($this == null)
            return after;
        if (after == null)
            return $this;
        return () -> {
            $this.run();
            after.run();
        };
    }
    
    @Extension.Operator("~")
    static void safeRun(final @Nullable Runnable $this) {
        if ($this != null)
            $this.run();
    }
    
    @Extension.Operator("^")
    static void safeRun(final @Nullable Runnable $this, final Consumer<Throwable> handler) {
        try {
            if ($this != null)
                $this.run();
        } catch (final Throwable t) { handler.accept(t); }
    }
    
}
