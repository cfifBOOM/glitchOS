package app.contouros.home;

import java.util.Arrays;
import java.util.Random;

public final class TilePackingTest {
    public static void main(String[] args) {
        checkBatteryAndCalculator();
        Random random = new Random(42);
        for (int columns : new int[]{4, 6}) {
            for (int run = 0; run < 2000; run++) {
                int count = random.nextInt(160);
                int[] widths = new int[count];
                int[] heights = new int[count];
                for (int i = 0; i < count; i++) {
                    TileSpec tile = new TileSpec(TileSpec.APP, "pkg/" + i, "", 1 + random.nextInt(4));
                    widths[i] = tile.columns();
                    heights[i] = tile.rows();
                }
                checkFirstFit(widths, heights, columns);
            }
        }
        if (TilePacking.pack(new int[0], new int[0], 4).length != 0) throw new AssertionError("Empty");
        try {
            TilePacking.pack(new int[]{1}, new int[]{1}, 0);
            throw new AssertionError("Invalid columns accepted");
        } catch (IllegalArgumentException expected) { }
        TileSpec tile = new TileSpec(TileSpec.APP, "a/b", "A", 1);
        if (!tile.key().equals(tile.resized().key())) throw new AssertionError("Identity");
        System.out.println("PASS: battery and calculator side gaps; 4000 first-fit layouts; size, bounds, overlap, identity");
    }

    private static void checkBatteryAndCalculator() {
        // Six-column board as reported: portrait, two medium tiles, battery, browser,
        // two app icons, calculator, storage, and the remaining small apps.
        int[] widths = new int[25];
        int[] heights = new int[25];
        Arrays.fill(widths, 1);
        Arrays.fill(heights, 1);
        int[] topWidths = {4, 2, 2, 4, 4, 1, 1, 4, 4};
        int[] topHeights = {4, 2, 2, 2, 2, 1, 1, 4, 2};
        System.arraycopy(topWidths, 0, widths, 0, topWidths.length);
        System.arraycopy(topHeights, 0, heights, 0, topHeights.length);
        TilePacking.Cell[] cells = TilePacking.pack(widths, heights, 6);
        for (int index : new int[]{3, 7}) {
            TilePacking.Cell anchor = cells[index];
            for (int y = anchor.y; y < anchor.y + anchor.height; y++) {
                for (int x = anchor.x + anchor.width; x < 6; x++) {
                    boolean filled = false;
                    for (TilePacking.Cell cell : cells) {
                        if (cell.x == x && cell.y == y && cell.width == 1 && cell.height == 1) {
                            filled = true;
                            break;
                        }
                    }
                    if (!filled) throw new AssertionError("Unfilled side cell at " + x + "," + y);
                }
            }
        }
    }

    // Independent cell-by-cell reference: every saved tile must get the earliest
    // place available when it was appended. Later tiles must never displace it.
    private static void checkFirstFit(int[] widths, int[] heights, int columns) {
        TilePacking.Cell[] cells = TilePacking.pack(widths, heights, columns);
        boolean[][] occupied = new boolean[widths.length * 4][columns];
        for (int i = 0; i < widths.length; i++) {
            TilePacking.Cell cell = cells[i];
            int w = widths[i], h = heights[i];
            int p = 0;
            while (!fits(occupied, p % columns, p / columns, w, h, columns)) p++;
            if (cell.x != p % columns || cell.y != p / columns || cell.width != w || cell.height != h) {
                throw new AssertionError("Incorrect first fit for tile " + i + " on " + columns + " columns");
            }
            for (int y = cell.y; y < cell.y + h; y++) {
                Arrays.fill(occupied[y], cell.x, cell.x + w, true);
            }
        }
    }

    private static boolean fits(boolean[][] occupied, int x, int y, int width, int height, int columns) {
        if (x + width > columns) return false;
        for (int row = y; row < y + height; row++) {
            for (int col = x; col < x + width; col++) {
                if (occupied[row][col]) return false;
            }
        }
        return true;
    }
}
