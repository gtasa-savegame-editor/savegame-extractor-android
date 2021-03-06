package io.lerk.gtasase.tasks;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import io.lerk.gtasase.MultipartUtility;

import static io.lerk.gtasase.SavegameActivity.PROTO_VERSION;

/**
 * {@link AsyncTask} used to upload a savegame to the editor.
 *
 * @author Lukas Fülling (lukas@k40s.net)
 */
public class SavegameUploadTask extends AsyncTask<Void, Void, Throwable> {
    private static final String TAG = SavegameUploadTask.class.getCanonicalName();

    private final File item;
    private final String serviceAddress;
    private final Callback callback;

    /**
     * Constructor.
     *
     * @param item           the item to upload
     * @param serviceAddress the address (host) of the editor
     * @param callback       the {@link Callback}
     */
    public SavegameUploadTask(@NonNull File item,
                              @NonNull String serviceAddress,
                              @NonNull Callback callback) {
        this.item = item;
        this.serviceAddress = serviceAddress;
        this.callback = callback;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        callback.setLoading(true);
    }

    @Override
    @SuppressLint("WrongThread") // wat
    protected Throwable doInBackground(Void... voids) {
        try {

            String requestUrl = "http://" + serviceAddress + "/add";
            Log.d(TAG, "Using address: '" + requestUrl);
            MultipartUtility multipart = new MultipartUtility(requestUrl, "UTF-8");

            multipart.addHeaderField("User-Agent", "GTA:SA Savegame Extractor (Android)");
            multipart.addFormField("version", PROTO_VERSION);

            InputStream inputStream = callback.getInputStream();
            if (inputStream == null) {
                inputStream = new InputStream() {
                    @Override
                    public int read() throws IOException {
                        return -1;
                    }
                };
            }
            multipart.addFilePart("savegame", item, inputStream);

            StringBuilder responseBuilder = new StringBuilder();
            multipart.finish().forEach(responseBuilder::append);
            Log.d(TAG, "Server Response:\n" + responseBuilder.toString());
        } catch (Throwable ex) {
            Log.e(TAG, "Unable to upload savegame!", ex);
            return ex;
        }
        return null;
    }

    @Override
    protected void onPostExecute(Throwable ex) {
        callback.setLoading(false);
        callback.call(ex);
    }

    /**
     * Simple callback class.
     */
    public interface Callback {
        /**
         * Callback method called when the task is done.
         *
         * @param ex null if no error
         */
        void call(@Nullable Throwable ex);

        /**
         * Callback method to get the input stream when uploading.
         *
         * @return the input stream of the file to upload
         */
        @Nullable
        InputStream getInputStream();

        /**
         * Callback method to change the loading state of the
         * {@link androidx.swiperefreshlayout.widget.SwipeRefreshLayout}.
         *
         * @param loading if true the spinner shows
         */
        void setLoading(boolean loading);
    }
}
