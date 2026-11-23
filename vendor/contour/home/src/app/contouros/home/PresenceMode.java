package app.contouros.home;

enum PresenceMode {
    HERE, ASKING, ALONE, WAIT_FOR_CHARGE, MAY_OFFER, BATTERY_OFFER;

    boolean isAway() {
        return this == ASKING || this == ALONE || this == WAIT_FOR_CHARGE || this == MAY_OFFER;
    }

    PresenceMode withBattery(int percent, boolean plugged, boolean savingPower) {
        if (percent < 0 || percent > 100) return this;
        boolean needsCharge = percent <= 15 && !plugged;
        if (this == WAIT_FOR_CHARGE && !needsCharge) return MAY_OFFER;
        if (this == MAY_OFFER && needsCharge) return savingPower ? WAIT_FOR_CHARGE : BATTERY_OFFER;
        if (this == BATTERY_OFFER && (!needsCharge || savingPower)) return HERE;
        return this;
    }
}
