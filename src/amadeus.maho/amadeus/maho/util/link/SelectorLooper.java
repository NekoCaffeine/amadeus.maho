package amadeus.maho.util.link;

import java.nio.channels.Selector;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.util.concurrent.Looper;

@Getter
@SneakyThrows
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public record SelectorLooper(Selector selector = Selector.open(), Looper looper = Looper.builder().build()) {

    // { new Thread(this, looper(), getName() + "SelectorLooper", -1, false).start(); }


}
