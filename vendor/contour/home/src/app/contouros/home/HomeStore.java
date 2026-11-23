package app.contouros.home;

import android.content.ComponentName;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class HomeStore {
    static final int MAX_TILES = 2048;
    private static final String LAYOUT = "layout_v1";
    private static final String PREVIOUS = "previous_layout_v1";
    private final SharedPreferences prefs;

    HomeStore(SharedPreferences prefs) { this.prefs = prefs; }
    boolean hasLayout() { return prefs.contains(LAYOUT); }
    boolean canUndo() { return prefs.contains(PREVIOUS); }

    List<TileSpec> read() {
        String raw = prefs.getString(LAYOUT, null);
        if (raw == null) return defaults();
        try {
            JSONObject root = new JSONObject(raw);
            int schema = root.getInt("schema");
            if (schema != 1 && schema != 2) return defaults();
            JSONArray data = root.getJSONArray("tiles");
            List<TileSpec> result = new ArrayList<>();
            Set<String> keys = new HashSet<>();
            for (int i = 0; i < data.length() && result.size() < MAX_TILES; i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                String kind = item.optString("kind", "");
                String component = item.optString("component", "");
                if (!TileSpec.knownKind(kind)) continue;
                if (TileSpec.APP.equals(kind) && ComponentName.unflattenFromString(component) == null) continue;
                int size = item.optInt("span", TileSpec.MEDIUM);
                if (schema == 1) size = size == 2 ? TileSpec.WIDE : TileSpec.MEDIUM;
                TileSpec tile = new TileSpec(kind, component, item.optString("label", ""), size);
                if (keys.add(tile.key())) result.add(tile);
            }
            return result; // An intentionally empty board is a valid choice.
        } catch (JSONException | ClassCastException e) {
            return defaults(); // Do not overwrite the original preference on a read failure.
        }
    }

    void write(List<TileSpec> tiles) {
        if (tiles.size() > MAX_TILES) throw new IllegalArgumentException("Too many tiles");
        String previous = prefs.getString(LAYOUT, encode(defaults()));
        prefs.edit().putString(PREVIOUS, previous).putString(LAYOUT, encode(tiles)).apply();
    }

    void initialize(List<TileSpec> tiles) {
        if (!hasLayout()) prefs.edit().putString(LAYOUT, encode(tiles)).apply();
    }

    int columns() { return prefs.getInt("grid_columns", 4) == 6 ? 6 : 4; }
    void setColumns(int columns) { prefs.edit().putInt("grid_columns", columns == 6 ? 6 : 4).apply(); }

    // A removed tile stays removed on subsequent scans. Only previously unseen apps are appended.
    List<TileSpec> syncApps(List<TileSpec> installed) {
        List<TileSpec> result = read();
        Set<String> seen = new HashSet<>(prefs.getStringSet("seen_apps_v2", new HashSet<>()));
        Set<String> pinned = new HashSet<>();
        for (TileSpec tile : result) pinned.add(tile.key());
        for (TileSpec app : installed) {
            if (!seen.contains(app.component) && !pinned.contains(app.key()) && result.size() < MAX_TILES) {
                result.add(app); pinned.add(app.key());
            }
            seen.add(app.component);
        }
        prefs.edit().putString(LAYOUT, encode(result)).putStringSet("seen_apps_v2", seen).apply();
        return result;
    }

    void undo() {
        String previous = prefs.getString(PREVIOUS, null);
        if (previous != null) prefs.edit().putString(LAYOUT, previous).remove(PREVIOUS).apply();
    }

    static List<TileSpec> defaults() {
        List<TileSpec> tiles = new ArrayList<>();
        tiles.add(new TileSpec(TileSpec.PRESENCE, "", "Помощница", TileSpec.MEDIUM));
        tiles.add(new TileSpec(TileSpec.BATTERY, "", "Заряд", TileSpec.MEDIUM));
        tiles.add(new TileSpec(TileSpec.ALARM, "", "Будильник", TileSpec.MEDIUM));
        tiles.add(new TileSpec(TileSpec.STORAGE, "", "Память", TileSpec.SMALL));
        tiles.add(new TileSpec(TileSpec.NETWORK, "", "Связь", TileSpec.SMALL));
        return tiles;
    }

    private static String encode(List<TileSpec> tiles) {
        try {
            JSONArray data = new JSONArray();
            for (TileSpec tile : tiles) {
                data.put(new JSONObject().put("kind", tile.kind).put("component", tile.component)
                        .put("label", tile.label).put("span", tile.span));
            }
            return new JSONObject().put("schema", 2).put("tiles", data).toString();
        } catch (JSONException e) {
            throw new IllegalStateException("Could not encode local tile configuration", e);
        }
    }
}
