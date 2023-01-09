package amadeus.maho.util.misc;

public interface BoxDrawingHelper {
    
    @FunctionalInterface
    interface Drawer {
        
        char draw(char map[][], int x, int y);
        
        byte
                UP    = 1 << 3,
                DOWN  = UP >>> 1,
                LEFT  = DOWN >>> 1,
                RIGHT = LEFT >>> 1,
                FULL  = UP | DOWN | LEFT | RIGHT;
        
        static Drawer box(final char mark) = (map, x, y) -> {
            final char context = map[y][x];
            if (context != mark)
                return context;
            byte bitMark = 0b0000;
            {
                final char xAxis[] = map[y];
                if (x + 1 < xAxis.length)
                    if (xAxis[x + 1] == mark)
                        bitMark |= RIGHT;
                if (x - 1 > -1)
                    if (xAxis[x - 1] == mark)
                        bitMark |= LEFT;
            }
            if (y - 1 > -1) {
                final char xAxis[] = map[y - 1];
                if (xAxis[x] == mark)
                    bitMark |= UP;
            }
            if (y + 1 < map.length) {
                final char xAxis[] = map[y + 1];
                if (xAxis[x] == mark)
                    bitMark |= DOWN;
            }
            return box(bitMark);
        };
        
        static char box(final byte mark) = switch (mark) {
            case FULL                -> '┼';
            case UP | DOWN           -> '│';
            case LEFT | RIGHT        -> '─';
            case UP | LEFT           -> '┘';
            case UP | RIGHT          -> '└';
            case DOWN | LEFT         -> '┐';
            case DOWN | RIGHT        -> '┌';
            case UP | DOWN | LEFT    -> '┤';
            case UP | DOWN | RIGHT   -> '├';
            case LEFT | RIGHT | UP   -> '┴';
            case LEFT | RIGHT | DOWN -> '┬';
            default                  -> '╳';
        };
        
    }
    
    static char[][] draw(final char map[][], final Drawer drawer) {
        final char copy[][] = map.clone();
        for (int i = 0; i < copy.length; i++)
            copy[i] = copy[i].clone();
        for (int y = 0; y < copy.length; y++) {
            final char xAxis[] = copy[y];
            for (int x = 0; x < xAxis.length; x++)
                xAxis[x] = drawer.draw(map, x, y);
        }
        return copy;
    }
    
}
