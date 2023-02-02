package amadeus.maho.transform.handler.base.marker;

import amadeus.maho.transform.TransformerManager;

public interface Marker {
    
    void onMark(TransformerManager.Context context);
    
    default boolean advance() = true;
    
}
