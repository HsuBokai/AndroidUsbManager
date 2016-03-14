package com.example.epsilon.test1;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
                    if(!is_ibadge(device)) return;

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        unregisterReceiver(mUsbReceiver);
                        mIsGranted = true;
                    }
                    else {
                        Log.d("check_permission", "permission denied for device " + device);
                    }
                }
            }
        }
    };

    BroadcastReceiver mUsbDetachedReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if(device == null) return;
                if(!is_ibadge(device)) return;
                device_disconnect();
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
                device_connect();
            }
        });

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);



        Intent intent = getIntent();
        if (intent != null) {
            Log.d("onResume", "intent: " + intent.toString());
            if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                mUsbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                // assume permission granted
                mIsGranted = true;
                return;
            }
        }

        if(has_device()){
            registerReceiver(mUsbReceiver, new IntentFilter(ACTION_USB_PERMISSION));
            PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
            mUsbManager.requestPermission(mUsbDevice, permissionIntent);
        }
        else{
            Log.d("onCreate", "Usb Device not found");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mUsbDetachedReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));
    }

    @Override
    protected void onPause(){
        unregisterReceiver(mUsbDetachedReceiver);
        super.onPause();
    }

    @Override
    protected  void onDestroy(){
        device_disconnect();
        super.onDestroy();
    }

    private boolean has_device(){
        if(mUsbManager == null) return false;
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while(deviceIterator.hasNext()){
            UsbDevice device = deviceIterator.next();
            if (is_ibadge(device)) {
                mUsbDevice = device;
                return true;
            }
        }
        return false;
    }

    private boolean is_ibadge(UsbDevice device){
        return device.getVendorId()==2323 && device.getProductId()==14112;
    }

    private void device_disconnect(){
        if(mInterface == null) return;
        if(mConnection == null) return;
        mConnection.releaseInterface(mInterface);
        mConnection.close();
    }

    private void device_connect() {
        if(mUsbDevice == null) return;
        if(mIsGranted == false) return;
        Log.d("device_connect", "USB device attached: name: " + mUsbDevice.getDeviceName());

        byte[] bytes = new byte[]{0x00, 0x00, 0x00, 0x00};
        mInterface = mUsbDevice.getInterface(0);
        UsbEndpoint endpoint = mInterface.getEndpoint(0);
        mConnection = mUsbManager.openDevice(mUsbDevice);
        mConnection.claimInterface(mInterface, forceClaim);
        mConnection.bulkTransfer(endpoint, bytes, bytes.length, TIMEOUT);
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
