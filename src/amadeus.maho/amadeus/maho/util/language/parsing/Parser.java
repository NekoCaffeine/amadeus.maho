package amadeus.maho.util.language.parsing;

public interface Parser<R> {
    
    R parse(Tokenizer.Context context);
    
}
