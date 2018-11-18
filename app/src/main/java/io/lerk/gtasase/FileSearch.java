package io.lerk.gtasase;

import android.os.Environment;
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

    public static ArrayList<File> search() {
        ArrayList<File> results = new ArrayList<>();
        FileSearch fileSearch = new FileSearch();
        fileSearch.searchDirectory(Environment.getExternalStorageDirectory());
        List<String> result = fileSearch.getResult();
        result.forEach(s -> results.add(new File(s)));
        return results;
    }

    private void searchDirectory(File directory) {
        if (directory.isDirectory()) {
            search(directory);
        } else {
            Log.e(TAG, "Not a directory: " + directory.getAbsoluteFile());
        }
    }

    private void search(File file) {
        if (file.isDirectory()) {
            if (file.canRead()) {
                for (File temp : file.listFiles()) {
                    if (temp.isDirectory()) {
                        search(temp);
                    } else {
                        if (temp.getAbsolutePath().toLowerCase().contains("gta") && temp.getAbsolutePath().toLowerCase().contains("com.rockstar")) {
                            if (temp.getName().endsWith(".b")) {
                                result.add(temp.getAbsoluteFile().toString());
                            }
                        }
                    }
                }
            } else {
                Log.e(TAG, "Permission Denied: " + file.getAbsoluteFile());
            }
        }
    }
}