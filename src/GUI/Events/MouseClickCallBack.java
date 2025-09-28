package GUI.Events;

public interface MouseClickCallBack extends EventCallBack<MouseClickEvent> {
    @Override
    void onEvent(MouseClickEvent e);
}
