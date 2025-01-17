package com.vivek.bluetoothsdk;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ListView;

import androidx.activity.ComponentActivity;
import androidx.core.app.ActivityCompat;

import com.vivek.blutoothsdk.BluetoothSDK;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends ComponentActivity {

    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 101;
    private BluetoothSDK bluetoothSDK;
    private BluetoothDevice selectedDevice;
    private TextView statusTextView;
    private List<BluetoothDevice> deviceList = new ArrayList<>();
    private ArrayAdapter<String> deviceAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothSDK = new BluetoothSDK(this);
        statusTextView = findViewById(R.id.tv_status);

        Button scanButton = findViewById(R.id.btn_scan);
        scanButton.setOnClickListener(v -> handleScan());


        ListView deviceListView = findViewById(R.id.device_list);
        Button pairButton = findViewById(R.id.btn_pair);

        // Set up ListView adapter
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        deviceListView.setAdapter(deviceAdapter);


        // Select a device from the list
        deviceListView.setOnItemClickListener((
                AdapterView<?> parent, View view, int position, long id) -> {
            selectedDevice = deviceList.get(position);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            String deviceInfo = selectedDevice.getName() + " (" + selectedDevice.getAddress() + ")";
            statusTextView.setText("Selected: " + deviceInfo);
            Toast.makeText(MainActivity.this, "Selected: " + deviceInfo, Toast.LENGTH_SHORT).show();
        });


        pairButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                bluetoothSDK.connectToGatt(selectedDevice, new BluetoothSDK.PairingCallback() {
                    @Override
                    public void onPaired(BluetoothDevice device) {

                        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                            return;
                        }
                        String message = "Paired with: " + device.getName();
                        updateStatus(message);
                    }

                    @Override
                    public void onPairingFailed(BluetoothDevice device) {

                        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                            return;
                        }
                        String message = "Pairing failed with: " + device.getName();
                        updateStatus(message);
                    }
                });

            }
        });


    }

    private void handleScan() {
        if (hasBluetoothPermissions()) {
            startScanning();
        } else {
            requestBluetoothPermissions();
        }
    }

    private void startScanning() {
        statusTextView.setText("Scanning for devices...");
        deviceList.clear();
        deviceAdapter.clear();


        bluetoothSDK.startScan(new BluetoothSDK.Callback<List<BluetoothDevice>>() {
            @Override
            public void onResult(List<BluetoothDevice> devices) {

                for (BluetoothDevice device : devices) {
                    deviceList.add(device);
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    deviceAdapter.add(device.getName() + " (" + device.getAddress() + ")");
                    deviceAdapter.notifyDataSetChanged();
                    Log.d("BLE", "Device: " + device.getName() + " [" + device.getAddress() + "]");
                }
                // Update UI or handle the list of devices

            }
        });
    }


    private void updateStatus(String message) {
        statusTextView.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private boolean hasBluetoothPermissions() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBluetoothPermissions() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT},
                REQUEST_BLUETOOTH_PERMISSIONS
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanning();
            } else {
                Toast.makeText(this, "Bluetooth permissions are required.", Toast.LENGTH_SHORT).show();
            }
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        bluetoothSDK.stopScan();
    }
}
