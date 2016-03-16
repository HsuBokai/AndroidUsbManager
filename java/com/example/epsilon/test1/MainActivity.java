package com.example.epsilon.test1;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import java.util.HashMap;
import java.util.Iterator;

public class MainActivity extends AppCompatActivity {
    private UsbManager mUsbManager;
    private boolean mIsGranted = false;
    private UsbDevice mUsbDevice = null;
    private UsbDeviceConnection mConnection = null;
    private UsbInterface mInterface = null;
    private UsbEndpoint mEndpointOut = null;
    private UsbEndpoint mEndpointIn = null;

    private static int TIMEOUT = 0;
    private boolean forceClaim = true;


    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if(device == null) return;
                    if(!isMyDevice(device)) return;

                    if(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)){
                        mIsGranted = true;
                        device_connect();
                    }
                    else{
                        Log.d("check_permission", "permission denied for device " + device);
                    }
                }
            }
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if(device == null) return;
                if(!isMyDevice(device)) return;
                device_disconnect();
                mUsbDevice = null;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

                if(mConnection == null) return;
                if(mEndpointIn == null || mEndpointOut == null) return;

                byte[] command = new byte[]{(byte)0x55, (byte)0x53, (byte)0x42, (byte)0x43,
                        (byte)0x80, (byte)0xa8, (byte)0xa2, (byte)0x86,
                        (byte)0x0a, (byte)0x00, (byte)0x00, (byte)0x00,
                        (byte)0x80, (byte)0x00, (byte)0x0c, (byte)0x00,
                        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                        (byte)0x00, (byte)0x00, (byte)0x00};

                int outLen = mConnection.bulkTransfer(mEndpointOut, command, command.length, TIMEOUT);
                if(outLen < 0){
                    throw new IllegalArgumentException("bulkTransfer out error");
                }

                int maxPacketSize = mEndpointIn.getMaxPacketSize();
                byte[] buffer = new byte[maxPacketSize];
                byte b0 = buffer[0];
                int inLen = mConnection.bulkTransfer(mEndpointIn, buffer, maxPacketSize, TIMEOUT);
                if(inLen < 0){
                    throw new IllegalArgumentException("bulkTransfer in error");
                }
                b0 = buffer[0];
            }
        });

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        Intent intent = getIntent();
        if (intent != null) {
            Log.d("onResume", "intent: " + intent.toString());
            if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                UsbDevice device  = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if(device != null && isMyDevice(device)) {
                    mUsbDevice = device;
                }
            }
        }

        if(mUsbDevice != null || has_device()) {

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            intentFilter.addAction(ACTION_USB_PERMISSION);
            registerReceiver(mUsbReceiver, intentFilter);

            if (mUsbManager.hasPermission(mUsbDevice)) {
                mIsGranted = true;
                device_connect();
            } else {
                PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
                mUsbManager.requestPermission(mUsbDevice, permissionIntent);
            }
        }
        else{
            mUsbDevice = null;
            Log.d("onCreate", "Usb Device not found");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause(){
        super.onPause();
    }

    @Override
    protected  void onDestroy(){
        if(mUsbDevice != null) unregisterReceiver(mUsbReceiver);
        device_disconnect();
        super.onDestroy();
    }

    private boolean has_device(){
        if(mUsbManager == null) return false;
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while(deviceIterator.hasNext()){
            UsbDevice device = deviceIterator.next();
            if (isMyDevice(device)) {
                mUsbDevice = device;
                return true;
            }
        }
        return false;
    }

    private boolean isMyDevice(UsbDevice device){
        return device.getVendorId()==2323 && device.getProductId()==14112;
    }

    private void device_disconnect(){
        if(mUsbDevice == null) return;
        if(mInterface == null) return;
        if(mConnection == null) return;
        mConnection.releaseInterface(mInterface);
        mConnection.close();
        mInterface = null;
        mConnection = null;
    }

    private void device_connect() {
        if(mUsbManager == null) return;
        if(mUsbDevice == null) return;
        if(! mIsGranted ) return;
        Log.d("device_connect", "USB device attached: name: " + mUsbDevice.getDeviceName());

        mInterface = mUsbDevice.getInterface(0);
        mConnection = mUsbManager.openDevice(mUsbDevice);
        if(mConnection == null){
            throw new IllegalArgumentException("UsbManager openDevice fail");
        }
        if(! mConnection.claimInterface(mInterface, forceClaim)){
            throw new IllegalArgumentException("Connection claimInterface fail");
        }

        // look for our bulk endpoints
        int endpointCount = mInterface.getEndpointCount();
        for (int i = 0; i < endpointCount; i++) {
            UsbEndpoint ep = mInterface.getEndpoint(i);
            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                    mEndpointOut = ep;
                } else {
                    mEndpointIn = ep;
                }
            }
        }
        if (mEndpointOut == null || mEndpointIn == null) {
            throw new IllegalArgumentException("not all endpoints found");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
