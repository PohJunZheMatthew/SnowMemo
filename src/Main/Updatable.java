package Main;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
@FunctionalInterface
public interface Updatable {
    void update();
    static final HashMap<Window,List<Updatable>> updatables = new HashMap<>();
    static void updateAll(Window window){
        if (!updatables.containsKey(window)) updatables.put(window,new ArrayList<Updatable>());
        List<Updatable> snapshot;
        synchronized (updatables.get(window)) {
            snapshot = new ArrayList<>(updatables.get(window));
        }

        for (Updatable updatable : snapshot) {
            synchronized (updatable) {
                updatable.update();
            }
        }
    }
    static void register(Window window, Updatable updatable) {
        updatables.computeIfAbsent(window, w -> new ArrayList<>()).add(updatable);
    }
    static void unregister(Window window,Updatable updatable){
        updatables.computeIfAbsent(window, w -> new ArrayList<>()).remove(updatable);
    }
}
