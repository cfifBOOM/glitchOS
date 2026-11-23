package app.contouros.home;

public final class PresenceModeTest {
    private static void expect(PresenceMode actual, PresenceMode expected) {
        if (actual != expected) throw new AssertionError(actual + " != " + expected);
    }

    public static void main(String[] args) {
        for (PresenceMode mode : PresenceMode.values()) {
            expect(mode.withBattery(-1, false, false), mode);
        }
        for (PresenceMode mode : new PresenceMode[]{PresenceMode.HERE, PresenceMode.ASKING, PresenceMode.ALONE}) {
            expect(mode.withBattery(10, false, false), mode);
        }
        PresenceMode waiting = PresenceMode.WAIT_FOR_CHARGE;
        expect(waiting.withBattery(15, false, false), waiting);
        expect(waiting.withBattery(12, true, false), PresenceMode.MAY_OFFER);
        PresenceMode watching = waiting.withBattery(16, false, false);
        expect(watching, PresenceMode.MAY_OFFER);
        expect(watching.withBattery(15, false, true), waiting);
        expect(watching.withBattery(10, true, false), watching);
        PresenceMode offer = watching.withBattery(15, false, false);
        expect(offer, PresenceMode.BATTERY_OFFER);
        expect(offer.withBattery(8, false, false), offer);
        expect(offer.withBattery(15, true, false), PresenceMode.HERE);
        expect(offer.withBattery(12, false, true), PresenceMode.HERE);
        expect(offer.withBattery(16, false, false), PresenceMode.HERE);
        if (!watching.isAway() || !waiting.isAway() || !PresenceMode.ALONE.isAway()
                || !PresenceMode.ASKING.isAway() || offer.isAway() || PresenceMode.HERE.isAway())
            throw new AssertionError("Incorrect portrait");
        System.out.println("PASS: consent, seated pose, low battery, charging, power saver and stale offers");
    }
}
