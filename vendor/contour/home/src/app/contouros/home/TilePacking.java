package app.contouros.home;

/** Place each saved tile in the first rectangle it fits, scanning rows from the top. */
final class TilePacking {
    private static final int MAX_HEIGHT = 4;

    static final class Cell {
        final int x, y, width, height;

        Cell(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    static Cell[] pack(int[] widths, int[] heights, int columns) {
        if (columns < 1 || columns > 12 || widths.length != heights.length) {
            throw new IllegalArgumentException("grid");
        }
        Cell[] cells = new Cell[widths.length];
        int[] occupied = new int[cells.length * MAX_HEIGHT];
        // A gap skipped by a wide tile may still fit a smaller tile. For two tiles of
        // the same shape, all previously rejected positions stay blocked.
        int[][] nextPosition = new int[columns + 1][MAX_HEIGHT + 1];
        for (int i = 0; i < cells.length; i++) {
            int width = Math.max(1, Math.min(columns, widths[i]));
            int height = Math.max(1, Math.min(MAX_HEIGHT, heights[i]));
            for (int pos = nextPosition[width][height]; ; pos++) {
                int x = pos % columns;
                int y = pos / columns;
                if (x + width > columns) continue;
                int mask = ((1 << width) - 1) << x;
                if (!isFree(occupied, y, height, mask)) continue;

                cells[i] = new Cell(x, y, width, height);
                for (int row = y; row < y + height; row++) occupied[row] |= mask;
                nextPosition[width][height] = pos + width;
                break;
            }
        }
        return cells;
    }

    private static boolean isFree(int[] occupied, int y, int height, int mask) {
        for (int row = y; row < y + height; row++) {
            if ((occupied[row] & mask) != 0) return false;
        }
        return true;
    }
}
