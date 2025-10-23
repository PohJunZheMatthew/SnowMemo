package Main.FileChooser;

import Main.History;
import com.mongodb.lang.NonNull;
import org.lwjgl.opengl.WGL;

import java.io.File;
import java.util.ArrayList;
public class FileHistory extends ArrayList<File> implements History<File> {
    int currentIndex = 0;
    File currentDirectory = null;
    @Override
    public void add(@NonNull int index,@NonNull File file){
        if (index >= size()) {
            super.add(file);
        } else {
            for (int i = size() - 1; i > index; i--) {
                remove(i);
            }
            set(index, file);
        }
        currentIndex = index;
    }
    @Override
    public void go(@NonNull int index) {
        currentIndex = index;
    }

    @Override
    public void forward() {
        if (this.size()-2>currentIndex) currentIndex++;
    }

    @Override
    public void back() {
        if (1<currentIndex) currentIndex--;
    }

    @Override
    public File getCurrent() {
        if (isEmpty()){
            return null;
        }
        currentDirectory = get(currentIndex);
        return currentDirectory;
    }

    @Override
    public void home() {
        currentIndex = 0;
    }
}
