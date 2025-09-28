package GUI.Events;

import GUI.GUIComponent;

public abstract class Event {
    private final GUIComponent source;
    private final long timestamp;

    public Event(GUIComponent source) {
        this.source = source;
        this.timestamp = System.currentTimeMillis();
    }

    public GUIComponent getSource() {
        return source;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
