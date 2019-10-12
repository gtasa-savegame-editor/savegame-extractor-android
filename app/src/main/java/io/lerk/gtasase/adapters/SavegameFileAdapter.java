package io.lerk.gtasase.adapters;

import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;

import io.lerk.gtasase.R;
import io.lerk.gtasase.SavegameActivity;
import io.lerk.gtasase.tasks.SavegameUploadTask;

/**
 * {@link android.widget.ListAdapter} to show the locally found savegames.
 *
 * @author Lukas Fülling (lukas@k40s.net)
 */
public class SavegameFileAdapter extends ArrayAdapter<Pair<Uri, File>> {

    private static final String TAG = SavegameFileAdapter.class.getCanonicalName();
    private final String serviceAddress;
    private final int servicePort;

    /**
     * Constructor.
     *
     * @param activity       the activity
     * @param layoutId       the layout to use for list items
     * @param serviceAddress the address of the savegame editor
     * @param servicePort    the port of the savegame editor
     */
    public SavegameFileAdapter(SavegameActivity activity, @LayoutRes int layoutId, String serviceAddress, int servicePort) {
        super(activity, layoutId);

        this.serviceAddress = serviceAddress;
        this.servicePort = servicePort;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Pair<Uri, File> item = getItem(position);
        if (item != null) {
            if (convertView != null) {
                return initView(convertView, item.second, item.first);
            } else {
                return initView(LayoutInflater.from(getContext()).inflate(R.layout.layout_savegame_item, parent, false), item.second, item.first);
            }
        }
        return convertView;
    }


    private View initView(View view, File item, Uri uri) {
        TextView savegameName = view.findViewById(R.id.savegame_name);
        TextView savegamePath = view.findViewById(R.id.savegame_path);
        Button uploadButton = view.findViewById(R.id.upload_button);

        savegameName.setText(item.getName());
        savegamePath.setText(item.getParent());
        uploadButton.setOnClickListener(v -> {
            Snackbar uploadingSnackbar = Snackbar.make(((SavegameActivity) getContext()).findViewById(R.id.serviceContent),
                    R.string.uploading, Snackbar.LENGTH_INDEFINITE);

            new SavegameUploadTask(uploadingSnackbar, item, serviceAddress, new SavegameUploadTask.Callback() {
                @Override
                public void call(@Nullable Throwable ex) {
                    if (ex == null) {
                        new AlertDialog.Builder(getContext()).setNeutralButton(R.string.okay, (d, i) -> d.dismiss())
                                .setTitle(R.string.upload_success_title)
                                .setMessage(R.string.upload_success_message).show();
                    } else {
                        new AlertDialog.Builder(getContext()).setNeutralButton(R.string.okay, (d, i) -> d.dismiss())
                                .setTitle(R.string.upload_error_title)
                                .setMessage(ex.getMessage()).show();
                    }
                }

                @Override
                @Nullable
                public InputStream getInputStream() {
                    try {
                        return getContext().getContentResolver().openInputStream(uri);
                    } catch (FileNotFoundException e) {
                        Log.e(TAG, "Unable to open savegame!", e);
                    }
                    return null;
                }
            }).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        });

        return view;
    }
}
