package io.lerk.gtasase.search;

import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import java.io.File;
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

    public static ArrayList<Pair<Uri, File>> execute(File searchDirectory) {
        if (instance == null) {
            instance = new JavaFileSearch();
        }
        return instance.search(searchDirectory);
    }


    @Override
    public ArrayList<Pair<Uri, File>> search(File searchDirectory) {
        ArrayList<Pair<Uri, File>> result = new ArrayList<>();
        searchInternal(searchDirectory, result);
        return result;
    }

    private void searchInternal(File searchItem, ArrayList<Pair<Uri, File>> result) {
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
                result.add(new Pair<>(Uri.fromFile(searchItem), searchItem));
            }
        }
    }
}
