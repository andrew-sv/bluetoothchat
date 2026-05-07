package com.bluetoothchat;

import android.app.Activity;
import android.bluetooth.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_DEVICE_CONNECT = 2;
    private static final int REQUEST_PERMISSIONS = 3;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothChatService mChatService;
    private ArrayAdapter<String> mConversationArrayAdapter;
    private EditText mOutEditText;
    private TextView mStatusText;

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case BluetoothChatService.MSG_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothChatService.STATE_CONNECTED:
                            mStatusText.setText("Connected");
                            break;
                        case BluetoothChatService.STATE_CONNECTING:
                            mStatusText.setText("Connecting...");
                            break;
                        case BluetoothChatService.STATE_LISTEN:
                        case BluetoothChatService.STATE_NONE:
                            mStatusText.setText("Not connected");
                            break;
                    }
                    break;
                case BluetoothChatService.MSG_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    mConversationArrayAdapter.add("Them: " + readMessage);
                    break;
                case BluetoothChatService.MSG_TOAST:
                    Toast.makeText(MainActivity.this, (String) msg.obj, Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mStatusText = findViewById(R.id.status_text);
        mOutEditText = findViewById(R.id.edit_text_out);
        mConversationArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        ((ListView) findViewById(R.id.in)).setAdapter(mConversationArrayAdapter);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        requestNeededPermissions();

        findViewById(R.id.button_send).setOnClickListener(v -> sendMessage());
        findViewById(R.id.button_connect).setOnClickListener(v -> {
            Intent intent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(intent, REQUEST_DEVICE_CONNECT);
        });
    }

    private void requestNeededPermissions() {
        // Pre-Marshmallow (e.g. Galaxy S3 / Android 4.3) grants BLUETOOTH/BLUETOOTH_ADMIN at install time.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;

        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(android.Manifest.permission.BLUETOOTH_CONNECT);
            perms.add(android.Manifest.permission.BLUETOOTH_SCAN);
        } else {
            perms.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }

        List<String> needed = new ArrayList<>();
        for (String p : perms) {
            if (ActivityCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                needed.add(p);
        }
        if (!needed.isEmpty())
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQUEST_PERMISSIONS);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            if (mChatService == null) setupChat();
        }
    }

    private void setupChat() {
        mChatService = new BluetoothChatService(mHandler);
        mChatService.start(); // Start listening for incoming connections
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK) {
            setupChat();
        } else if (requestCode == REQUEST_DEVICE_CONNECT && resultCode == Activity.RESULT_OK) {
            String address = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
            mChatService.connect(device);
        }
    }

    private void sendMessage() {
        String message = mOutEditText.getText().toString().trim();
        if (!message.isEmpty() && mChatService.getState() == BluetoothChatService.STATE_CONNECTED) {
            mChatService.write(message.getBytes());
            mConversationArrayAdapter.add("Me: " + message);
            mOutEditText.setText("");
        } else if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mChatService != null) mChatService.stop();
    }
}