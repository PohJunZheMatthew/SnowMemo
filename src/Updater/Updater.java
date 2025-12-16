package Updater;

import Main.SnowMemo;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;

public class Updater extends JFrame {
    private JProgressBar progressBar = new JProgressBar();
    public Updater(){
        super("Snowmemo Updater");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setVisible(true);
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
                desktop.setQuitHandler((e, response) -> {
                    response.cancelQuit();
                });
            }
        }
        add(progressBar);
    }
    static void main(String[] args){
        new Updater();
    }
    public void checkForUpdates(){

    }
    public static void update(){
        ProcessBuilder processBuilder = new ProcessBuilder("java","Updater.java");
        processBuilder.inheritIO();
        try {
            Process process = processBuilder.start();
            System.out.println("exitCode = " + process.waitFor());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    private static class Task {
        private double percentage;
        private final String name;
        private static final HashMap<String, Task> tasks = new HashMap<>();

        public Task(String name) {
            this.name = name;
            this.percentage = 0;
            tasks.put(name, this);
        }

        public Task addPercentage(double value) {
            percentage += value;
            if (percentage > 100) {
                percentage = 100;
            }
            return this;
        }

        public boolean isFinished() {
            return percentage >= 100;
        }

        public double getPercentage() {
            return percentage;
        }

        public static Task getTaskByName(String name) {
            return tasks.get(name);
        }
    }
    public static String getAppVersion() {
        Package pkg = SnowMemo.class.getPackage();
        if (pkg != null) {
            String v = pkg.getImplementationVersion();
            if (v != null) return v;
        }
        return "dev";
    }
    public static String getCurrentOnlineVersion(){
        try{
            URI uri =new URI("");

        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
