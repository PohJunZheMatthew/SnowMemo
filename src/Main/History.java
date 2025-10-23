package Main;

import java.util.List;

/*
* */
public interface History<t> extends List<t> {
    void go(int index);
    void forward();
    void back();
    t getCurrent();
    void home();
}
