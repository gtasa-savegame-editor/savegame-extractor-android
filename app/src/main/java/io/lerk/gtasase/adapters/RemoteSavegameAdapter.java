package io.lerk.gtasase.adapters;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.io.File;

import io.lerk.gtasase.R;

public class RemoteSavegameAdapter extends ArrayAdapter<Pair<Uri, File>> {
    private final String serviceAddress;

    public RemoteSavegameAdapter(@NonNull Context context, int resource, @NonNull String serviceAddress) {
        super(context, resource);
        this.serviceAddress = serviceAddress;
    }

    @Override
    @NonNull
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        Pair<Uri, File> item = getItem(position);
        if (item != null) {
            if (convertView != null) {
                return initView(convertView, item.second);
            } else {
                return initView(LayoutInflater.from(getContext()).inflate(R.layout.layout_savegame_item, parent, false), item.second);
            }
        }
        return convertView;
    }

    @SuppressLint("StaticFieldLeak")
    private View initView(View view, File item) {
        TextView savegameName = view.findViewById(R.id.savegame_name);
        Button downloadButton = view.findViewById(R.id.upload_button);
        downloadButton.setText(R.string.download);

        savegameName.setText(item.getName());
        downloadButton.setOnClickListener(v -> new AsyncTask<Void, Void, Throwable>() {
            @Override
            protected Throwable doInBackground(Void... voids) {
                DownloadManager downloadManager = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
                Uri uri = Uri.parse("http://" + serviceAddress + "/get/" + item.getName());
                DownloadManager.Request request = new DownloadManager.Request(uri);
                request.setTitle(item.getName());
                request.setDescription(getContext().getString(R.string.downloading_message));
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, item.getName());
                if (downloadManager != null) {
                    downloadManager.enqueue(request);
                }
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR));

        return view;
    }
}
