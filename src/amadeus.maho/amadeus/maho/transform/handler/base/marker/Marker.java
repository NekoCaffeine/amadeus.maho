package amadeus.maho.transform.handler.base.marker;

public interface Marker {
    
    void onMark();
    
    default boolean advance() = true;
    
}
