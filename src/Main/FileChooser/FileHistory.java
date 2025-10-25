package Main.FileChooser;

import Main.History;
import com.mongodb.lang.NonNull;

import java.io.File;
import java.util.ArrayList;

public class FileHistory extends ArrayList<File> implements History<File> {
    private int currentIndex = -1; // Start at -1 (empty state)

    // ========================================
    // ISSUE #1: add() method logic is broken
    // ========================================

    @Override
    public void add(@NonNull int index, @NonNull File file) {
        // When navigating to a new directory, clear forward history
        // (like a web browser - if you go back and visit a new page,
        // you lose the "forward" history)

        if (currentIndex >= 0 && currentIndex < size() - 1) {
            // Remove all entries after currentIndex (clear forward history)
            for (int i = size() - 1; i > currentIndex; i--) {
                remove(i);
            }
        }

        // Add the new directory
        super.add(file);
        currentIndex = size() - 1; // Point to the newly added entry
    }

    // ========================================
    // ISSUE #2: go() doesn't validate bounds
    // ========================================

    @Override
    public void go(@NonNull int index) {
        if (index >= 0 && index < size()) {
            currentIndex = index;
        } else {
            System.err.println("Invalid history index: " + index + " (size: " + size() + ")");
        }
    }

    // ========================================
    // ISSUE #3: forward() condition is wrong
    // ========================================

    @Override
    public void forward() {
        // Original: if (this.size()-2>currentIndex)
        // This is wrong! Should be: if (currentIndex < size() - 1)

        if (currentIndex < size() - 1) {
            currentIndex++;
            System.out.println("Forward to index " + currentIndex + ": " + get(currentIndex).getName());
        } else {
            System.out.println("Already at newest history entry");
        }
    }

    // ========================================
    // ISSUE #4: back() condition is confusing
    // ========================================

    @Override
    public void back() {
        // Original: if (1<currentIndex)
        // This skips index 1! Should be: if (currentIndex > 0)

        if (currentIndex > 0) {
            currentIndex--;
            System.out.println("Back to index " + currentIndex + ": " + get(currentIndex).getName());
        } else {
            System.out.println("Already at oldest history entry");
        }
    }

    // ========================================
    // ISSUE #5: getCurrent() looks correct
    // ========================================

    @Override
    public File getCurrent() {
        if (isEmpty() || currentIndex < 0 || currentIndex >= size()) {
            return null;
        }
        System.out.println("currentIndex = " + currentIndex + " of " + size());
        return get(currentIndex);
    }

    // ========================================
    // ISSUE #6: home() doesn't navigate
    // ========================================

    @Override
    public void home() {
        // This just resets the index, but doesn't return the home directory!
        if (!isEmpty()) {
            currentIndex = 0;
            System.out.println("Home to index 0: " + get(0).getName());
        }
    }

    // ========================================
    // ADDITIONAL HELPER METHODS
    // ========================================

    public boolean canGoBack() {
        return currentIndex > 0;
    }

    public boolean canGoForward() {
        return currentIndex < size() - 1;
    }

    public void clear() {
        super.clear();
        currentIndex = -1;
    }

    // Override to maintain currentIndex properly
    @Override
    public boolean add(File file) {
        // Clear forward history if we're not at the end
        if (currentIndex >= 0 && currentIndex < size() - 1) {
            for (int i = size() - 1; i > currentIndex; i--) {
                remove(i);
            }
        }

        boolean result = super.add(file);
        if (result) {
            currentIndex = size() - 1;
        }
        return result;
    }
}
