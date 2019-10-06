package io.lerk.gtasase;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileSearch {

    private static final String TAG = FileSearch.class.getCanonicalName();

    private List<String> result = new ArrayList<>();

    private List<String> getResult() {
        return result;
    }

    public static ArrayList<File> search(File searchDirectory) {
        ArrayList<File> results = new ArrayList<>();
        FileSearch fileSearch = new FileSearch();
        fileSearch.searchDirectory(searchDirectory);
        List<String> result = fileSearch.getResult();
        result.forEach(s -> results.add(new File(s)));
        return results;
    }

    private void searchDirectory(File directory) {
        while (!directory.getName().equals("data")) {
            File parentFile = directory.getParentFile();
            if (parentFile == null) {
                break;
            }
            directory = parentFile;
        }
        if (directory.isDirectory()) {
            searchInternal(directory);
        } else {
            Log.e(TAG, "Not a directory: " + directory.getAbsoluteFile());
        }
    }

    private void searchInternal(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File temp : files) {
                    if (temp.isDirectory()) {
                        searchInternal(temp);
                    } else {
                        if (temp.getAbsolutePath().toLowerCase().contains("gtasa") && // make sure it's a savegame
                                temp.getAbsolutePath().toLowerCase().contains("com.rockstar")) { // make sure it's in the app folder (package id is localized)
                            if (temp.getName().endsWith(".b")) {
                                result.add(temp.getAbsoluteFile().toString());
                            }
                        }
                    }
                }
            } else {
                Log.w(TAG, "Directory contains no files: " + file.getAbsolutePath());
            }
        }
    }
}