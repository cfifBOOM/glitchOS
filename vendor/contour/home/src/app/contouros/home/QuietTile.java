package app.contouros.home;

import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class QuietTile extends TileService {
    static final String PREFERENCES = "contour";
    static final String QUIET = "quiet";
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
        if (QUIET.equals(key)) updateTile();
    };

    @Override
    public void onStartListening() {
        super.onStartListening();
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(listener);
        updateTile();
    }

    @Override
    public void onStopListening() {
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(listener);
        super.onStopListening();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (isLocked()) {
            unlockAndRun(this::toggle);
            return;
        }
        toggle();
    }

    private void toggle() {
        SharedPreferences prefs = getSharedPreferences(PREFERENCES, MODE_PRIVATE);
        prefs.edit().putBoolean(QUIET, !prefs.getBoolean(QUIET, false)).apply();
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean quiet = getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean(QUIET, false);
        tile.setState(quiet ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setSubtitle(quiet ? "Движение остановлено" : "Движение разрешено");
        tile.setContentDescription(quiet ? "Анимация на паузе" : "Анимация включена");
        tile.updateTile();
    }
}
