package io.lerk.gtasase.search;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * {@link FileSearch} implementation that uses {@link java.io.File} objects to search for savegames.
 *
 * @author Lukas Fülling (lukas@k40s.net)
 */
public class JavaFileSearch implements FileSearch {

    private static final String TAG = JavaFileSearch.class.getCanonicalName();

    private static JavaFileSearch instance = null;

    private JavaFileSearch() {
    }

    public static ArrayList<File> execute(File searchDirectory) {
        if (instance == null) {
            instance = new JavaFileSearch();
        }
        return instance.search(searchDirectory);
    }


    @Override
    public ArrayList<File> search(File searchDirectory) {
        ArrayList<File> result = new ArrayList<>();
        searchInternal(searchDirectory, result);
        return result;
    }

    private void searchInternal(File searchItem, ArrayList<File> result) {
        if (searchItem.isDirectory()) {
            if (searchItem.canRead()) {
                searchInternal(searchItem, result);
            } else {
                Log.w(TAG, "Directory not readable: '" + searchItem.getAbsolutePath() + "'!");
            }
        } else {
            Log.d(TAG, "Not a directory: '" + searchItem.getAbsolutePath() + "'!");
            String[] pathSplit = searchItem.getAbsolutePath().split(File.separator);
            if (pathSplit[pathSplit.length - 1].startsWith("GTASAsf")) {
                result.add(searchItem);
            }
        }
    }
}
