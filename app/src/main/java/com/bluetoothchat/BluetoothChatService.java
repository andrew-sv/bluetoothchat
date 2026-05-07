package com.bluetoothchat;

import android.bluetooth.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import java.io.*;
import java.util.UUID;

public class BluetoothChatService {

    // Unique UUID for this app — both devices must use the same one
    private static final UUID MY_UUID =
        UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

    public static final int STATE_NONE = 0;
    public static final int STATE_LISTEN = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 3;

    public static final int MSG_STATE_CHANGE = 1;
    public static final int MSG_READ = 2;
    public static final int MSG_TOAST = 3;

    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState = STATE_NONE;

    public BluetoothChatService(Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mHandler = handler;
    }

    private synchronized void setState(int state) {
        mState = state;
        mHandler.obtainMessage(MSG_STATE_CHANGE, state, -1).sendToTarget();
    }

    public synchronized int getState() { return mState; }

    // Start listening as a server
    public synchronized void start() {
        if (mConnectThread != null) { mConnectThread.cancel(); mConnectThread = null; }
        if (mConnectedThread != null) { mConnectedThread.cancel(); mConnectedThread = null; }
        if (mAcceptThread == null) {
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }
        setState(STATE_LISTEN);
    }

    // Connect to a remote device as client
    public synchronized void connect(BluetoothDevice device) {
        if (mConnectThread != null) { mConnectThread.cancel(); mConnectThread = null; }
        if (mConnectedThread != null) { mConnectedThread.cancel(); mConnectedThread = null; }
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    // Start the connected thread after a socket is established
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (mConnectThread != null) { mConnectThread.cancel(); mConnectThread = null; }
        if (mConnectedThread != null) { mConnectedThread.cancel(); mConnectedThread = null; }
        if (mAcceptThread != null) { mAcceptThread.cancel(); mAcceptThread = null; }
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
        setState(STATE_CONNECTED);
    }

    public void write(byte[] out) {
        ConnectedThread r;
        synchronized (this) { if (mState != STATE_CONNECTED) return; r = mConnectedThread; }
        r.write(out);
    }

    public synchronized void stop() {
        if (mConnectThread != null) { mConnectThread.cancel(); mConnectThread = null; }
        if (mConnectedThread != null) { mConnectedThread.cancel(); mConnectedThread = null; }
        if (mAcceptThread != null) { mAcceptThread.cancel(); mAcceptThread = null; }
        setState(STATE_NONE);
    }

    // --- SERVER THREAD: waits for incoming connections ---
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = mAdapter.listenUsingRfcommWithServiceRecord("BluetoothChat", MY_UUID);
            } catch (IOException e) { e.printStackTrace(); }
            mmServerSocket = tmp;
        }

        public void run() {
            if (mmServerSocket == null) return;
            BluetoothSocket socket;
            while (true) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) { break; }
                if (socket != null) {
                    synchronized (BluetoothChatService.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                connected(socket, socket.getRemoteDevice());
                                break;
                            default:
                                try { socket.close(); } catch (IOException e) { e.printStackTrace(); }
                        }
                    }
                }
            }
        }

        void cancel() {
            if (mmServerSocket == null) return;
            try { mmServerSocket.close(); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    // --- CLIENT THREAD: initiates connection to server ---
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) { e.printStackTrace(); }
            mmSocket = tmp;
        }

        public void run() {
            mAdapter.cancelDiscovery();
            try {
                mmSocket.connect();
            } catch (IOException e) {
                try { mmSocket.close(); } catch (IOException e2) { e2.printStackTrace(); }
                setState(STATE_NONE);
                mHandler.obtainMessage(MSG_TOAST, -1, -1, "Connection failed").sendToTarget();
                BluetoothChatService.this.start();
                return;
            }
            synchronized (BluetoothChatService.this) { mConnectThread = null; }
            connected(mmSocket, mmDevice);
        }

        void cancel() {
            try { mmSocket.close(); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    // --- CONNECTED THREAD: handles read/write on an open socket ---
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { e.printStackTrace(); }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);
                    // Send received bytes to UI
                    mHandler.obtainMessage(MSG_READ, bytes, -1, buffer.clone()).sendToTarget();
                } catch (IOException e) {
                    mHandler.obtainMessage(MSG_TOAST, -1, -1, "Connection lost").sendToTarget();
                    BluetoothChatService.this.start();
                    break;
                }
            }
        }

        void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
            } catch (IOException e) { e.printStackTrace(); }
        }

        void cancel() {
            try { mmSocket.close(); } catch (IOException e) { e.printStackTrace(); }
        }
    }
}