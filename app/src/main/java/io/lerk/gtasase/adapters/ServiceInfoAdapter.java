package io.lerk.gtasase.adapters;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;

import javax.jmdns.ServiceInfo;
import javax.jmdns.impl.ServiceInfoImpl;

import io.lerk.gtasase.R;
import io.lerk.gtasase.SavegameActivity;

public class ServiceInfoAdapter extends ArrayAdapter<ServiceInfo> {

    private static final String TAG = ServiceInfoAdapter.class.getCanonicalName();

    public ServiceInfoAdapter(@NonNull Context context, int resource) {
        super(context, resource);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        ServiceInfo serviceInfo = getItem(position);
        if (serviceInfo != null) {
            if (convertView != null) {
                return initView(convertView, serviceInfo);
            } else {
                return initView(LayoutInflater.from(getContext()).inflate(R.layout.layout_service_item, parent, false), serviceInfo);
            }
        } else {
            return convertView;
        }
    }

    private View initView(View view, ServiceInfo serviceInfo) {
        String serviceAddressString;
        String hostname = serviceInfo.getPropertyString("hostname");
        String propertyIp = serviceInfo.getPropertyString("ip");
        TextView serviceName = view.findViewById(R.id.service_name);
        TextView serviceAddress = view.findViewById(R.id.service_address);
        Button connectButton = view.findViewById(R.id.connect_button);

        try {
            serviceAddressString = getFullServiceAddressString(serviceInfo, propertyIp);
        } catch (Exception e) {
            Log.e(TAG, "Unable to determine address for service.", e);
            serviceAddressString = getContext().getString(R.string.address_error_message);
            connectButton.setEnabled(false);
            serviceAddress.setTextColor(getContext().getColor(R.color.errorTextColor));
        }
        String serviceNameString = serviceInfo.getName() + ((hostname == null || hostname.isEmpty()) ? "" : " (" + hostname + ")");

        serviceName.setText(serviceNameString);
        serviceAddress.setText(serviceAddressString);
        final String finalServiceAddressString = serviceAddressString;
        connectButton.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), SavegameActivity.class);
            ArrayList<String> strings = new ArrayList<>();
            Arrays.asList(serviceInfo.getInetAddresses()).forEach(s -> strings.add(s.toString().replaceAll("/", "")));
            if (strings.size() <= 0) {
                strings.add(finalServiceAddressString);
            }
            intent.putStringArrayListExtra(SavegameActivity.SERVICE_ADDRESS_KEY, strings);
            intent.putExtra(SavegameActivity.SERVICE_PORT_KEY, serviceInfo.getPort());
            getContext().startActivity(intent);
        });

        return view;
    }

    private String getFullServiceAddressString(ServiceInfo serviceInfo, String propertyIp) throws Exception {
        String portPropertyString = serviceInfo.getPropertyString("port");
        String portServiceInfoString = String.valueOf(serviceInfo.getPort());
        if (!portServiceInfoString.equals("") && !portServiceInfoString.equals("0")) {
            return getHostAddressString(serviceInfo, propertyIp) + ":" + portServiceInfoString;
        } else if (portPropertyString != null && !portPropertyString.equals("0")) {
            return getHostAddressString(serviceInfo, propertyIp) + ":" + portPropertyString;
        } else {
            throw new Exception("No port found for service.");
        }
    }

    private String getHostAddressString(ServiceInfo serviceInfo, String propertyIp) {
        if (serviceInfo.getInetAddresses().length > 0 &&
                !(serviceInfo.getInetAddresses()[0].toString().toLowerCase().startsWith("0.") ||
                        serviceInfo.getInetAddresses()[0].toString().toLowerCase().contains("0:"))) {
            return serviceInfo.getInetAddresses()[0].toString().replaceAll("/", "");
        } else if (propertyIp != null) {
            return propertyIp;
        } else {
            return ((ServiceInfoImpl) serviceInfo).getDns().getLocalHost().getInetAddress().getHostAddress();
        }
    }
}
