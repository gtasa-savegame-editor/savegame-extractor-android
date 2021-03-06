package io.lerk.gtasase.tasks;

import android.net.Uri;
import android.os.AsyncTask;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;

import io.lerk.gtasase.search.CommandLineSearch;
import io.lerk.gtasase.search.JavaFileSearch;

public class FileSearchTask extends AsyncTask<Void, Void, ArrayList<Pair<Uri, File>>> {

    private final File searchPath;
    private final boolean useFileApi;
    private Callback callback;

    public FileSearchTask(@NonNull File searchPath, boolean useFileApi, @Nullable Callback callback) {
        this.searchPath = searchPath;
        this.useFileApi = useFileApi;
        this.callback = callback;
    }

    @Override
    protected ArrayList<Pair<Uri, File>> doInBackground(Void... voids) {
        if(useFileApi) {
            return JavaFileSearch.execute(searchPath);
        } else {
            return CommandLineSearch.execute(searchPath);
        }
    }

    @Override
    protected void onPostExecute(ArrayList<Pair<Uri, File>> files) {
        super.onPostExecute(files);
        if (callback != null) {
            callback.call(files);
        }
    }

    public interface Callback {
        void call(ArrayList<Pair<Uri, File>> result);
    }
}
