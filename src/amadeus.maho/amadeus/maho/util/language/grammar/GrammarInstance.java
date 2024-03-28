package amadeus.maho.util.language.grammar;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.util.language.parsing.Parser;
import amadeus.maho.util.language.parsing.Tokenizer;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.util.runtime.UnsafeHelper;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GrammarInstance<T> implements Parser<T> {
    
    interface Fragment {
        
        @Getter
        @AllArgsConstructor
        @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
        class Whitespace implements Fragment {
            
            String text;
            
            IntPredicate isWhitespace;
            
            @Override
            public void parse(final Tokenizer.Context context, final Map<MethodHandle, Object> args) = context.skip(isWhitespace());
            
        }
        
        @Getter
        @AllArgsConstructor
        @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
        class ConstantText implements Fragment {
            
            String text;
            
            @Override
            public void parse(final Tokenizer.Context context, final Map<MethodHandle, Object> args) = context.skip(text());
            
        }
        
        @Getter
        @SneakyThrows
        @AllArgsConstructor
        @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
        class Arg implements Fragment {
            
            Class<?> owner;
            
            String name;
            
            Field field = owner().getDeclaredField(name);
            
            MethodHandle getter = MethodHandleHelper.lookup().unreflectGetter(field()), setter = MethodHandleHelper.lookup().unreflectSetter(field());
            
            @Override
            public void parse(final Tokenizer.Context context, final Map<MethodHandle, Object> args) = args.put(setter(), context.parserProvider().apply(field().getType()).parse(context.dup()));
            
        }
        
        void parse(Tokenizer.Context context, Map<MethodHandle, Object> args);
        
    }
    
    Class<T> type;
    
    Grammar grammar = type.getAnnotation(Grammar.class);
    
    ArrayList<Fragment> fragments = { };
    
    public IntPredicate isWhitespace() = Character::isWhitespace;
    
    {
        final Tokenizer tokenizer = Tokenizer.tokenization(grammar.value().strip());
        final Tokenizer.Context context = tokenizer.root();
        final IntPredicate isWhitespace = isWhitespace(), notWhitespace = isWhitespace.negate();
        while (context.hasNext()) {
            final String next = context.scanString(notWhitespace);
            if (next.charAt(0) == '\\')
                fragments() += next.charAt(1) != '\\' ? switch (next.charAt(1)) {
                    case 'A' -> new Fragment.Arg(type(), next.substring(2));
                    default  -> throw new UnsupportedOperationException(STR."Operation: \{next.charAt(1)}");
                } : new Fragment.ConstantText(next.substring(1));
            else
                fragments() += new Fragment.ConstantText(next);
            if (context.hasNext()) {
                final String whitespace = context.scanString(isWhitespace);
                if (!whitespace.isEmpty())
                    fragments() += new Fragment.Whitespace(whitespace, isWhitespace);
            }
        }
    }
    
    @Override
    @SneakyThrows
    public T parse(final Tokenizer.Context context) {
        final Map<MethodHandle, Object> args = new HashMap<>();
        fragments().forEach(fragment -> fragment.parse(context, args));
        final T result = UnsafeHelper.allocateInstanceOfType(type);
        args.forEach((handle, arg) -> handle.invoke(result, arg));
        return result;
    }
    
}
