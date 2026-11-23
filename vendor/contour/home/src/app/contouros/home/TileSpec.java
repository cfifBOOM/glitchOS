package app.contouros.home;

/** Persistent identity and size in small square grid cells. */
final class TileSpec {
    static final String PRESENCE="presence", BATTERY="battery", ALARM="alarm", STORAGE="storage", NETWORK="network", APP="app";
    static final int SMALL=1, MEDIUM=2, WIDE=3, LARGE=4;
    final String kind, component, label;
    final int span;
    TileSpec(String kind,String component,String label,int size) {
        this.kind=kind;this.component=component==null?"":component;this.label=label==null?"":label;
        span=size>=SMALL&&size<=LARGE?size:MEDIUM;
    }
    int columns(){return span==SMALL?1:span==MEDIUM?2:4;}
    int rows(){return span==SMALL?1:span==LARGE?4:2;}
    String key(){return APP.equals(kind)?APP+":"+component:kind;}
    TileSpec resized(){return new TileSpec(kind,component,label,span==LARGE?SMALL:span+1);}
    static boolean knownKind(String kind){return PRESENCE.equals(kind)||BATTERY.equals(kind)||ALARM.equals(kind)||STORAGE.equals(kind)||NETWORK.equals(kind)||APP.equals(kind);}
}
