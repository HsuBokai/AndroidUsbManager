package com.example.epsilon.test1;

import android.app.Activity;
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
import android.util.Log;

import java.util.HashMap;
import java.util.Iterator;

public class ConnectUsb {

    public ConnectUsb(Activity parentActivity){
        mActivity = parentActivity;
    }

    private Activity mActivity;
    private UsbManager mUsbManager;
    private boolean mIsGranted = false;
    private UsbDevice mUsbDevice = null;
    private UsbDeviceConnection mConnection = null;
    private UsbInterface mInterface = null;
    private UsbEndpoint mEndpointOut = null;
    private UsbEndpoint mEndpointIn = null;
    private int mMaxPacketSize;
    private byte[] mBuffer;

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
                        deviceConnect();
                    }
                    else{
                        Log.d("BroadcastOnReceive", "permission denied for device " + device);
                    }
                }
            }
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if(device == null) return;
                if(!isMyDevice(device)) return;
                deviceDisconnect();
                mUsbDevice = null;
            }
        }
    };

    private boolean hasDevice(){
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

    private void deviceDisconnect(){
        if(mUsbDevice == null) return;
        if(mInterface == null) return;
        if(mConnection == null) return;
        mConnection.releaseInterface(mInterface);
        mConnection.close();
        mInterface = null;
        mConnection = null;
    }

    private void deviceConnect() {
        if(mUsbManager == null) return;
        if(mUsbDevice == null) return;
        if(! mIsGranted ) return;
        Log.d("deviceConnect", "USB device attached: name: " + mUsbDevice.getDeviceName());

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

        mMaxPacketSize = mEndpointIn.getMaxPacketSize();
        mBuffer = new byte[mMaxPacketSize];
    }

    private boolean checkUSBS(byte[] nonce){
        return mBuffer[0] == (byte)0x55 &&
                mBuffer[1] == (byte)0x53 &&
                mBuffer[2] == (byte)0x42 &&
                mBuffer[3] == (byte)0x53 &&
                mBuffer[4] == nonce[0] &&
                mBuffer[5] == nonce[1] &&
                mBuffer[6] == nonce[2] &&
                mBuffer[7] == nonce[3] ;
    }

    final static char[] _hexAlphabet = "0123456789ABCDEF".toCharArray();
    private String bytes2Hex(byte[] bytes, int len){
        len *= 2;
        char[] hexChar = new char[len];
        for(int i=0; i<len; ++i){
            hexChar[i] = (i%2==0) ? _hexAlphabet[(bytes[i/2] & 0xf0)>>>4] : _hexAlphabet[ bytes[i/2] & 0x0f];
        }
        return new String(hexChar);
    }


    public boolean init(){
        mUsbManager = (UsbManager) mActivity.getSystemService(Context.USB_SERVICE);

        Intent intent = mActivity.getIntent();
        if (intent != null) {
            if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                UsbDevice device  = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if(device != null && isMyDevice(device)) {
                    mUsbDevice = device;
                }
            }
        }

        if(mUsbDevice != null || hasDevice()) {

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            intentFilter.addAction(ACTION_USB_PERMISSION);
            mActivity.registerReceiver(mUsbReceiver, intentFilter);

            if (mUsbManager.hasPermission(mUsbDevice)) {
                mIsGranted = true;
                deviceConnect();
            } else {
                PendingIntent permissionIntent = PendingIntent.getBroadcast(mActivity, 0, new Intent(ACTION_USB_PERMISSION), 0);
                mUsbManager.requestPermission(mUsbDevice, permissionIntent);
            }
        }
        else{
            mUsbDevice = null;
            Log.d("init", "Usb Device not found");
            return false;
        }
        return true;
    }

    public void reset(){
        deviceDisconnect();
        if(mUsbDevice != null) mActivity.unregisterReceiver(mUsbReceiver);
    }

    public void getUid(){
        byte[] cmd = new byte[]{
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00};
        bulkCmd(cmd);
    }

    public void bulkCmd(byte[] cmd){
        if(mConnection == null) return;
        if(mEndpointIn == null || mEndpointOut == null) return;

        byte[] nonce = new byte[]{(byte)0x80, (byte)0xa8, (byte)0xa2, (byte)0x86};

        byte[] usbc = new byte[]{
                (byte)0x55, (byte)0x53, (byte)0x42, (byte)0x43,
                nonce[0], nonce[1], nonce[2], nonce[3],
                (byte)0x0a, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x80, (byte)0x00, (byte)0x0c, cmd[0],
                cmd[1], cmd[2], cmd[3], cmd[4],
                cmd[5], cmd[6], cmd[7], cmd[8],
                cmd[9], cmd[10], cmd[11], cmd[12],
                cmd[13], cmd[14], cmd[15]};

        int outLen = mConnection.bulkTransfer(mEndpointOut, usbc, usbc.length, TIMEOUT);
        if(outLen < 0){
            throw new IllegalArgumentException("bulkTransfer out error");
        }
        int inLen;
        do{
            inLen = mConnection.bulkTransfer(mEndpointIn, mBuffer, mMaxPacketSize, TIMEOUT);
            if(inLen < 0){
                throw new IllegalArgumentException("bulkTransfer in error");
            }
            Log.d("bulkCmd", "inLen: " + inLen);
            Log.d("bulkCmd", "inData: " + bytes2Hex(mBuffer, inLen));
        }while(!(inLen > 8 && checkUSBS(nonce)));

    }
}
