package io.lerk.gtasase.adapters;

import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;

import io.lerk.gtasase.R;
import io.lerk.gtasase.ServiceActivity;
import io.lerk.gtasase.tasks.SavegameUploadTask;

/**
 * {@link android.widget.ListAdapter} to show the locally found savegames.
 *
 * @author Lukas Fülling (lukas@k40s.net)
 */
public class SavegameFileAdapter extends ArrayAdapter<File> {

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
    public SavegameFileAdapter(ServiceActivity activity, @LayoutRes int layoutId, String serviceAddress, int servicePort) {
        super(activity, layoutId);

        this.serviceAddress = serviceAddress;
        this.servicePort = servicePort;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        File item = getItem(position);
        if (item != null) {
            if (convertView != null) {
                return initView(convertView, item);
            } else {
                return initView(LayoutInflater.from(getContext()).inflate(R.layout.layout_savegame, parent, false), item);
            }
        }
        return convertView;
    }


    private View initView(View view, File item) {
        TextView savegameName = view.findViewById(R.id.savegame_name);
        TextView savegamePath = view.findViewById(R.id.savegame_path);
        Button uploadButton = view.findViewById(R.id.upload_button);

        savegameName.setText(item.getName());
        savegamePath.setText(item.getParent());
        uploadButton.setOnClickListener(v -> {
            Snackbar uploadingSnackbar = Snackbar.make(((ServiceActivity) getContext()).findViewById(R.id.serviceContent),
                    R.string.uploading, Snackbar.LENGTH_INDEFINITE);

            new SavegameUploadTask(uploadingSnackbar, item, serviceAddress, servicePort, ex -> {
                if (ex == null) {
                    new AlertDialog.Builder(getContext()).setNeutralButton(R.string.okay, (d, i) -> d.dismiss())
                            .setTitle(R.string.upload_success_title)
                            .setMessage(R.string.upload_success_message).show();
                } else {
                    new AlertDialog.Builder(getContext()).setNeutralButton(R.string.okay, (d, i) -> d.dismiss())
                            .setTitle(R.string.upload_error_title)
                            .setMessage(ex.getMessage()).show();
                }
            }).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        });

        return view;
    }
}
