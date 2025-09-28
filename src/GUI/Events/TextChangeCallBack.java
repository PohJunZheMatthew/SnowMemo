package GUI.Events;

public interface TextChangeCallBack extends EventCallBack<TextChangeEvent> {
    void onEvent(TextChangeEvent event);
}