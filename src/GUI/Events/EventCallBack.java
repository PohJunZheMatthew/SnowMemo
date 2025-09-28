package GUI.Events;

public interface EventCallBack<E extends Event> {
    void onEvent(E event);
}
