package com.vivek.blutoothsdk;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothSDK {

    private static final String TAG = "BluetoothSDK";
    private static final int PERMISSION_REQUEST_CODE = 101;
    private static final int DISCOVERY_TIMEOUT = 30000; // 30 seconds
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private ScanCallback scanCallback;
    private BluetoothGatt bluetoothGatt;
    private BroadcastReceiver scanReceiver;
    private final Activity activity;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    private static final UUID SERVICE_UUID = UUID.fromString("2F123456-CF6D-4A0F-ADF2-F4911BA9FFA6");
    // Replace with your service UUID
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb"); // Replace with your characteristic UUID


    public BluetoothSDK(Activity activity) {
        this.activity = activity;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    private void showEnableBluetoothDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Enable Bluetooth");
        builder.setMessage("Bluetooth is disabled.Please Enable Bluetooth for scanning.");
        builder.setCancelable(true);

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                // Optionally handle cancel action
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void showNoBluetoothDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Bluetooth Not Supported");
        builder.setMessage("This device does not support Bluetooth.");
        builder.setCancelable(false);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                activity.finish(); // Close the app as Bluetooth is not supported
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(
                activity,
                new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                },
                PERMISSION_REQUEST_CODE
        );
    }


    public void connectToRingerDevice(String deviceAddress, PairingCallback callback) {
        BluetoothManager bluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);

        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_PERMISSIONS);
            callback.onPairingFailed(device);
            return;
        }

        Log.d(TAG, "Attempting to connect to device: " + deviceAddress);
        bluetoothGatt = device.connectGatt(activity, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(@NonNull BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Device connected: " + deviceAddress);
                    callback.onPaired(device);
                    if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "Device disconnected: " + deviceAddress);
                    if (status == 133) {
                        Log.e(TAG, "GATT connection failed with status 133. Retrying...");
                        retryGattConnection(device, callback);
                    } else {
                        callback.onPairingFailed(device);
                    }
                }
            }

            @Override
            public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Services discovered successfully.");
                } else {
                    Log.e(TAG, "Service discovery failed with status: " + status);
                }
            }
        });

        // Implement timeout logic
        new android.os.Handler().postDelayed(() -> {
            if (bluetoothGatt != null && bluetoothGatt.getDevice().equals(device)) {
                Log.w(TAG, "Connection timeout. Disconnecting...");
                bluetoothGatt.disconnect();
                callback.onPairingFailed(device);
            }
        }, 10000); // 10 seconds timeout
    }

    public void connectToGatt(BluetoothDevice device, PairingCallback callback) {
        if (ActivityCompat.checkSelfPermission(this.activity, "android.permission.BLUETOOTH_SCAN") == 0 &&
                ActivityCompat.checkSelfPermission(this.activity, "android.permission.BLUETOOTH_CONNECT") == 0 &&
                ActivityCompat.checkSelfPermission(this.activity, "android.permission.ACCESS_FINE_LOCATION") == 0) {

            // Check if device is already bonded
            if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                Log.e("BluetoothSDK", "Device is not bonded. Initiating bonding...");

                // Start the bonding process
                boolean bonded = device.createBond();
                if (!bonded) {
                    Log.e("BluetoothSDK", "Failed to start bonding process");
                    callback.onPairingFailed(device);
                    return;
                }
                BroadcastReceiver receiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String action = intent.getAction();
                        if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                            BluetoothDevice bondedDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                            int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);

                            if (bondedDevice != null && bondedDevice.equals(device)) {
                                switch (bondState) {
                                    case BluetoothDevice.BOND_NONE:
                                        Log.e("BluetoothSDK", "Bonding failed or bond removed for device: " + device.getAddress());
                                        callback.onPairingFailed(device);
                                        break;

                                    case BluetoothDevice.BOND_BONDING:
                                        Log.d("BluetoothSDK", "Bonding in progress for device: " + device.getAddress());
                                        // Optionally, notify UI or log progress
                                        break;

                                    case BluetoothDevice.BOND_BONDED:
                                        Log.d("BluetoothSDK", "Device bonded successfully: " + device.getAddress());
                                        callback.onPaired(device);
                                        initiateGattConnection(device, callback);
                                        break;

                                    default:
                                        Log.w("BluetoothSDK", "Unknown bond state: " + bondState + " for device: " + device.getAddress());
                                        break;
                                }
                            }
                        }
                    }
                };
                activity.registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
            } else {
                // If already bonded, proceed with GATT connection
                Log.d("BluetoothSDK", "Device is already bonded. Initiating GATT connection...");
                initiateGattConnection(device, callback);
            }
        } else {
            ActivityCompat.requestPermissions(this.activity, new String[]{"android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT", "android.permission.ACCESS_FINE_LOCATION"}, 1);
        }
    }

    private void initiateGattConnection(BluetoothDevice device, PairingCallback callback) {
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        this.bluetoothGatt = device.connectGatt(this.activity, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                Log.d("BluetoothGattCallback", "Connection state changed. Status: " + status + ", New State: " + newState);
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("BluetoothGattCallback", "Device connected. Discovering services...");
                    if (ActivityCompat.checkSelfPermission(BluetoothSDK.this.activity, "android.permission.BLUETOOTH_CONNECT") != PackageManager.PERMISSION_GRANTED) {
                        Log.e("BluetoothSDK", "BLUETOOTH_CONNECT permission not granted during service discovery.");
                        callback.onPairingFailed(device);
                        return;
                    }
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.e("BluetoothGattCallback", "Device disconnected. Status: " + status);
                    if (status == 133) {
                        Log.e("BluetoothSDK", "Status 133 encountered. Retrying connection...");
                        retryGattConnection(gatt.getDevice(), callback);
                    } else {
                        callback.onPairingFailed(device);
                    }
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BluetoothGattCallback", "Services discovered successfully.");
                    callback.onPaired(device);
                } else {
                    Log.e("BluetoothGattCallback", "Failed to discover services. Status: " + status);
                    callback.onPairingFailed(device);
                }
            }
        });
    }

    private void retryGattConnection(BluetoothDevice device, PairingCallback callback) {
        // Post the retry logic to the main thread
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.d(TAG, "Retrying GATT connection for device: " + device.getAddress());
            connectToRingerDevice(device.getAddress(), callback);
        }, 3000); // Retry after 3 seconds
    }


    public void sendCommand(String command, final Callback<String> callback) {
        if (bluetoothGatt == null) {
            callback.onResult("BluetoothGatt is null");
            return;
        }

        BluetoothGattService service = bluetoothGatt.getService(UUID.fromString("SERVICE_UUID")); // Replace with actual UUID
        if (service == null) {
            callback.onResult("Service not found");
            return;
        }

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString("CHARACTERISTIC_UUID")); // Replace with actual UUID
        if (characteristic == null) {
            callback.onResult("Characteristic not found");
            return;
        }

        characteristic.setValue(command.getBytes());
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);

        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        boolean success = bluetoothGatt.writeCharacteristic(characteristic);
        if (success) {
            callback.onResult("Command sent: " + command);
        } else {
            callback.onResult("Failed to send command");
        }
    }

    public void startScan(final Callback<List<BluetoothDevice>> callback) {

        // Check if permissions are granted
        if (!hasPermissions()) {
            requestPermissions();
            return;
        }

        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth is not supported on this device.");
            showNoBluetoothDialog();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth is disabled. Please enable Bluetooth.");
            showEnableBluetoothDialog();
            return;
        }

        BluetoothManager bluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();


        // List to hold discovered devices
        List<BluetoothDevice> scannedDevices = new ArrayList<>();

        // Check permissions for Android 6.0+ (ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 2);
                return;
            }
        }

        // Check permissions for Android 12+ (BLUETOOTH_SCAN and BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.BLUETOOTH_SCAN}, 1);
                return;
            }
        }

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();

                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }

                // Add device to list if not already present
                if (!scannedDevices.contains(device)) {
                    scannedDevices.add(device);
                    Log.d("BLE", "Device found: " + device.getName() + " [" + device.getAddress() + "]");
                }

                // If specific device found, stop scanning (optional)
                if ("RingerDevice".equals(device.getName())) { // Replace with your target device name
                    bluetoothLeScanner.stopScan(scanCallback);
                    callback.onResult(scannedDevices); // Return the list of devices
                }
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                for (ScanResult result : results) {
                    BluetoothDevice device = result.getDevice();
                    if (!scannedDevices.contains(device)) {
                        scannedDevices.add(device);
//                    Log.d("BLE", "Batch device found: " + device.getName() + " [" + device.getAddress() + "]");
                    }
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e("BLE", "Scan failed with error code: " + errorCode);
                callback.onResult(scannedDevices); // Return the list even if scanning fails
            }
        };

        bluetoothLeScanner.startScan(scanCallback);

        // Stop scan after a timeout (e.g., 10 seconds) and return the list
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            bluetoothLeScanner.stopScan(scanCallback);
            callback.onResult(scannedDevices);
            Log.d("BLE", "Scanning stopped after timeout");
        }, 10000); // 10 seconds timeout
    }

    public void stopScan() {
        if (bluetoothLeScanner != null && scanCallback != null) {
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            bluetoothLeScanner.stopScan(scanCallback);
            Log.d("BLE", "Scanning stopped");
        }
    }

    public interface Callback<T> {
        void onResult(T result);
    }

    public interface PairingCallback {
        void onPaired(BluetoothDevice device);
        void onPairingFailed(BluetoothDevice device);
    }
}

