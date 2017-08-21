/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
* This file is a modified version of https://github.com/googlesamples/android-BluetoothLeGatt
* This Activity is started by the DeviceScanActivity and is passed a device name and address to start with
* Clicking on the listed device, opens up a page which allows connection and for scanning of GATT Services available for the device
* */
//package com.example.android.openbciBLE;
package uk.ac.lancs.scc.openbcible;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = "OpenBCIBLE/"+ DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private static final byte[] mCommands = {'b','s'};
    private static int mCommandIdx = 0;
    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyOnRead;

    private boolean mIsDeviceGanglion;
    private boolean mIsDeviceCyton;
    private boolean mIsUnknownCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";



    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            Log.v(TAG,"Trying to connect to GATTServer on: "+mDeviceName+" Address: "+mDeviceAddress );
            mBluetoothLeService.connect(mDeviceAddress);
            mCommandIdx = 0;

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                Log.v(TAG,"GattServer Connected");
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.v(TAG,"GattServer Disconnected");
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.v(TAG,"GattServer Services Discovered");
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        //if it is a characteristic related view item, get the characteristic
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();

                        if(mIsDeviceGanglion){
                            if(SampleGattAttributes.UUID_GANGLION_SEND.equals(characteristic.getUuid().toString())){
                                //we use this only when the device is a ganglion
                                char c = toggleDataStream(characteristic);
                                Toast.makeText(getApplicationContext(), "Sent: '"+c+"' to Ganglion", Toast.LENGTH_SHORT).show();
                            }

                            if(SampleGattAttributes.UUID_GANGLION_RECEIVE.equals(characteristic.getUuid().toString())){
                                //check if we have registered for notifications
                                boolean updateNotifyOnRead = setCharacteristicNotification(mNotifyOnRead,characteristic,"Ganglion RECEIVE");
                                if(updateNotifyOnRead) mNotifyOnRead = characteristic;

                                //also read it for just now
                                mBluetoothLeService.readCharacteristic(characteristic);
                            }

                            if(SampleGattAttributes.UUID_GANGLION_DISCONNECT.equals(characteristic.getUuid().toString())){
                                Log.v(TAG,"Not sure what the disconnect characteristic does");
                                Toast.makeText(getApplicationContext(), "Disconnect Not Actionable", Toast.LENGTH_SHORT).show();
                            }
                            return true;//all done, return
                        }

                        if(mIsDeviceCyton){
                            if(SampleGattAttributes.UUID_CYTON_SEND.equals(characteristic.getUuid().toString())){
                                //we use this only when the device is a ganglion
                                char c = toggleDataStream(characteristic);
                                Toast.makeText(getApplicationContext(), "Sent: '"+c+"' to Cyton", Toast.LENGTH_SHORT).show();
                            }

                            if(SampleGattAttributes.UUID_CYTON_RECEIVE.equals(characteristic.getUuid().toString())){
                                //check if we have registered for notifications
                                boolean updateNotifyOnRead = setCharacteristicNotification(mNotifyOnRead,characteristic,"Cyton RECEIVE");
                                if(updateNotifyOnRead) mNotifyOnRead = characteristic;

                                //also read it for just now
                                mBluetoothLeService.readCharacteristic(characteristic);
                            }

                            if(SampleGattAttributes.UUID_CYTON_DISCONNECT.equals(characteristic.getUuid().toString())){
                                Log.v(TAG,"Not sure what the disconnect characteristic does");
                                Toast.makeText(getApplicationContext(), "Disconnect Not Actionable", Toast.LENGTH_SHORT).show();
                            }
                            return true;//all done, return
                        }


                        //If here, this is either not a Cyton/Ganglion board OR not the 3 primary characteristics

                        //specific readable characteristics

                        //sample test using battery level service (PlayStore:BLE Peripheral Simulator) BATTERY_LEVEL characteristic notifies
                        if(SampleGattAttributes.UUID_BATTERY_LEVEL.equals(characteristic.getUuid().toString())){//the
                            //we set it to notify, if it isn't already on notify
                            Log.v(TAG,"Battery level notification registration");
                            boolean updateNotifyOnRead = setCharacteristicNotification(mNotifyOnRead,characteristic,"Battery Level");
                            if(updateNotifyOnRead) mNotifyOnRead = characteristic;
                            mBluetoothLeService.readCharacteristic(characteristic);
                            return true;//all done, return
                        }

                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            //read it immediately
                            Log.v(TAG, "Reading characteristic: "+characteristic.getUuid().toString());
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }

                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            boolean updateNotifyOnRead = setCharacteristicNotification(mNotifyOnRead,characteristic,SampleGattAttributes.getShortUUID(characteristic.getUuid()));
                            if(updateNotifyOnRead) mNotifyOnRead = characteristic;
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        return true;
                    }
                    return false;
                }
            };

    private char toggleDataStream(BluetoothGattCharacteristic  BLEGChar){
        char cmd = (char) mCommands[mCommandIdx];
        Log.v(TAG,"Sending Command : "+cmd);
        BLEGChar.setValue(new byte[]{(byte)cmd});
        mBluetoothLeService.writeCharacteristic((BLEGChar));
        mCommandIdx = (mCommandIdx +1)% mCommands.length; //update for next run to toggle off
        return cmd;
    }

    private boolean setCharacteristicNotification(BluetoothGattCharacteristic currentNotify, BluetoothGattCharacteristic newNotify, String toastMsg){
        if(currentNotify==null){//none registered previously
            mBluetoothLeService.setCharacteristicNotification(newNotify, true);
        }
        else {//something was registered previously
            if (!currentNotify.getUuid().equals(newNotify.getUuid())) {//we are subscribed to another characteristic?
                mBluetoothLeService.setCharacteristicNotification(currentNotify, false);//unsubscribe
                mBluetoothLeService.setCharacteristicNotification(newNotify, true); //subscribe to Receive
            }
            else{
                //no change required
                return false;
            }
        }
        Toast.makeText(getApplicationContext(), "Notify: "+toastMsg, Toast.LENGTH_SHORT).show();
        return true;//indicates reassignment needed for mNotifyOnRead
    }

    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        //this activity was started by another with data stored in an intent, process it
        final Intent intent = getIntent();

        //get the devcie name and address
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        //set flags if CYTON or GANGLION is being used
        Log.v(TAG,"deviceName '"+mDeviceName+"'");
        if(mDeviceName!=null) {
            mIsDeviceCyton = mDeviceName.toUpperCase().contains(SampleGattAttributes.DEVICE_NAME_CYTON);
            mIsDeviceGanglion = mDeviceName.toUpperCase().contains(SampleGattAttributes.DEVICE_NAME_GANGLION);
        }
        else{//device name is not available
             mIsDeviceGanglion = false;mIsDeviceCyton = false;
        }

        if(mIsDeviceCyton||mIsDeviceGanglion){//if it is a desirable device
            Toast.makeText(getApplicationContext(), "OpenBCI " + (mIsDeviceCyton?"Cyton":"Ganglion") + " Connected", Toast.LENGTH_SHORT).show();
        }

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);

        Log.v(TAG,"Creating Service to Handle all further BLE Interactions");
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {

            Log.v(TAG,"Trying to connect to: "+mDeviceName+" Address: "+mDeviceAddress);
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                //the 'connect' button is clicked and this triggers the connection request
                Log.v(TAG,"Trying to connect to: "+mDeviceName+" Address: "+mDeviceAddress+ " on click");
                mBluetoothLeService.connect(mDeviceAddress);
                //the completion of the connection is returned separately
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            Log.w(TAG,"Service Iterator:"+gattService.getUuid());

            if(mIsDeviceGanglion){////we only want the SIMBLEE SERVICE, rest, we junk...
                if(!SampleGattAttributes.UUID_GANGLION_SERVICE.equals(gattService.getUuid().toString())) continue;
            }

            if(mIsDeviceCyton){////we only want the RFDuino SERVICE, rest, we junk...
                if(!SampleGattAttributes.UUID_CYTON_SERVICE.equals(gattService.getUuid().toString())) continue;
            }

            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);

                //if this is the read attribute for Cyton/Ganglion, register for notify service
                if((SampleGattAttributes.UUID_GANGLION_RECEIVE.equals(uuid))||(SampleGattAttributes.UUID_CYTON_RECEIVE.equals(uuid))){//the RECEIVE characteristic
                    Log.v(TAG,"Registering notify for: "+uuid);
                    //we set it to notify, if it isn't already on notify
                    if(mNotifyOnRead==null){
                        mBluetoothLeService.setCharacteristicNotification(gattCharacteristic, true);
                        mNotifyOnRead = gattCharacteristic;
                    }
                    else{
                        Log.v(TAG, "De-registering Notification for: "+mNotifyOnRead.getUuid().toString() +" first");
                        mBluetoothLeService.setCharacteristicNotification(mNotifyOnRead, false);
                        mBluetoothLeService.setCharacteristicNotification(gattCharacteristic, true);
                        mNotifyOnRead = gattCharacteristic;
                    }
                }
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
        mGattServicesList.setAdapter(gattServiceAdapter);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
