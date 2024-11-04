package amadeus.maho.util.exec;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.ValueBased;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.runtime.DebugHelper;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProcessPrototype {
    
    public sealed interface Argument {
        
        @ToString
        @EqualsAndHashCode
        record Variable(String name = UNNAMED, @Nullable String defaultValue = null) implements Argument {
            
            public static final String UNNAMED = "<unnamed>";
            
            public String value(final Map<Variable, Object> variables) = switch (variables[this]) {
                case null         -> defaultValue() ?? missing(this);
                case Object value -> value.toString();
            };
            
            private static String missing(final Variable variable) { throw DebugHelper.breakpointBeforeThrow(new IllegalArgumentException(STR."Missing variable: \{variable}")); }
            
        }
        
        @ValueBased
        @ToString
        @EqualsAndHashCode
        record Constant(String value, @EqualsAndHashCode.Mark boolean avoidDuplication = false) implements Argument {
            
            @Override
            public String value(final Map<Variable, Object> variables) = value();
            
        }
        
        @ValueBased
        @ToString
        @EqualsAndHashCode
        record Dynamic(List<Argument> values, @EqualsAndHashCode.Mark boolean avoidDuplication = false) implements Argument {
            
            public Dynamic(final Argument value) = this(List.of(value));
            
            public Dynamic(final Argument... values) = this(List.of(values));
            
            @Override
            public String value(final Map<Variable, Object> variables) = values().stream().map(Objects::toString).collect(Collectors.joining());
            
        }
        
        @ValueBased
        @ToString
        @EqualsAndHashCode
        record Flag(@Nullable String shortName = null, String fullName, @EqualsAndHashCode.Mark boolean avoidDuplication = true) implements Argument {
            
            @Override
            public String value(final Map<Variable, Object> variables) = switch (shortName()) {
                case null             -> STR."--\{fullName()}";
                case String shortName -> STR."-\{shortName}";
            };
            
        }
        
        @ValueBased
        @ToString
        @EqualsAndHashCode
        record SlashFlag(String name, @EqualsAndHashCode.Mark boolean avoidDuplication = true) implements Argument {
            
            @Override
            public String value(final Map<Variable, Object> variables) = STR."/\{name()}";
            
        }
        
        @ValueBased
        @ToString
        @EqualsAndHashCode
        record Prefix(String prefix) {
            
            @Extension.Operator("+")
            public Key key(final String key) = { prefix() + key };
            
        }
        
        @ValueBased
        @ToString
        @EqualsAndHashCode
        record Key(String key) {
            
            @Extension.Operator("+")
            public Option option(final Argument value) = { key(), value };
            
            @Extension.Operator("+")
            public Option option(final String value) = option(new Constant(value));
            
        }
        
        @ValueBased
        @ToString
        @EqualsAndHashCode
        record Option(String key, Argument value, @EqualsAndHashCode.Mark boolean avoidDuplication = true) implements Argument {
            
            @Override
            public String value(final Map<Variable, Object> variables) = STR."\{key()}=\{value().value(variables)}";
            
        }
        
        StringTemplate.Processor<Dynamic, RuntimeException> DYN = stringTemplate -> {
            final ArrayList<Argument> arguments = { };
            final Iterator<String> fragments = stringTemplate.fragments().iterator();
            final Iterator<Object> values = stringTemplate.values().iterator();
            if (fragments.hasNext()) {
                arguments += new Constant(fragments.next());
                while (values.hasNext()) {
                    arguments += switch (values.next()) {
                        case Argument argument -> argument;
                        case Object value      -> new Constant(value.toString());
                    };
                    arguments += new Constant(fragments.next());
                }
            }
            return new Dynamic(arguments);
        };
        
        default boolean avoidDuplication() = false;
        
        String value(Map<Variable, Object> variables);
        
    }
    
    @NoArgsConstructor
    public static class Modifiable extends ProcessPrototype {
        
        @Extension.Operator("*")
        public self add(final Argument argument) = arguments() += argument;
        
        @Extension.Operator("*")
        public self add(final String constant) = add(new Argument.Constant(constant));
        
        @Extension.Operator("*")
        public self addAll(final List<Argument> arguments) = arguments() *= arguments;
        
        @Extension.Operator("/")
        public self remove(final Argument argument) = arguments() -= argument;
        
        @Extension.Operator("/")
        public self removeIf(final Predicate<? super Argument> filter) = arguments().removeIf(filter);
        
    }
    
    @Getter
    @NoArgsConstructor
    public static class Anonymous extends ProcessPrototype {
        
        List<Argument.Variable> variables = new ArrayList<>();
        
        @Extension.Operator("~")
        public Argument.Variable nextVariable() {
            final Argument.Variable variable = { STR."\{Argument.Variable.UNNAMED}:\{variables().size()}" };
            variables() += variable;
            return variable;
        }
        
    }
    
    Path executable;
    
    @Default
    List<Argument> arguments = new ArrayList<>();
    
    public ProcessPrototype(final String executable) = this(Path.of(executable));
    
    @Extension.Operator("!")
    public ProcessPrototype.Modifiable copy() = { executable(), new ArrayList<>(arguments()) };
    
    public List<String> command(final Map<Argument.Variable, Object> variables = Map.of())
        = Stream.concat(Stream.of(executable().toString()), arguments().stream().map(argument -> argument.value(variables))).collect(Collectors.toList());
    
}
