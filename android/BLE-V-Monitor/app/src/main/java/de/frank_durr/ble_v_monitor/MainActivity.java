/**
 * This file is part of BLE-V-Monitor.
 *
 * Copyright 2015 Frank Duerr
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.frank_durr.ble_v_monitor;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * The main activity and entry point of the app.
 */
public class MainActivity extends android.support.v7.app.AppCompatActivity {

    public static final String TAG = MainActivity.class.getName();

    private enum HistoryTypes {historyMinutely, historyHourly, historyDaily}

    private enum BluetoothTasks {
        none, getVoltage, getMinutelyHistory, getHourlyHistory, getDailyHistory
    }

    // Standard base UUID (used to extend standard 16 bit UUIDs)
    public static final long STANDARD_BASE_UUID_MSB = 0x0000000000001000L;
    public static final long STANDARD_BASE_UUID_LSB = 0x800000805f9b34fbL;
    // 16 bit id of client characteristic configuration descriptor
    public static final short CLIENT_CHARACTERISTIC_CONFIGURATION_ID = 0x2902;

    // Base UUID of BLE-V-Monitor Service and its characteristics:
    //   0xde0eXXXXf0af4d389a1a33e88519d3b2L
    // XXXX will be replaced by the 16 bit ID of the service or characteristic defined below.
    public static final long BASE_UUID_MSB = 0xde0e0000f0af4d38L;
    public static final long BASE_UUID_LSB = 0x9a1a33e88519d3b2L;
    // 16 bit ids of BLE-V-Monitor service and characteristics
    public static final short SERVICE_ID = 0x0001;
    public static final short CHARACTERISTIC_ID_CURRENT_VOLTAGE = 0x0100;
    public static final short CHARACTERISTIC_ID_MINUTELY_HISTORY = 0x0200;
    public static final short CHARACTERISTIC_ID_HOURLY_HISTORY = 0x0300;
    public static final short CHARACTERISTIC_ID_DAILY_HISTORY = 0x0400;

    public static final int REQUEST_ENABLE_BT = 1;
    public static final int REQUEST_SELECT_DEVICE = 2;

    static public final int UPDATE_CURRENT_VOLTAGE = 1;
    static public final int UPDATE_MINUTELY_HISTORY = 2;
    static public final int UPDATE_HOURLY_HISTORY = 3;
    static public final int UPDATE_DAILY_HISTORY = 4;

    static private final String BUNDLE_KEY_CURRENT_VOLTAGE = "current_voltage";
    static private final String BUNDLE_KEY_HISTORY_MINUTELY = "history_minutely";
    static private final String BUNDLE_KEY_HISTORY_HOURLY = "history_hourly";
    static private final String BUNDLE_KEY_HISTORY_DAILY = "history_daily";
    static private final String BUNDLE_KEY_BLUETOOTH_DEVICE = "bluetooth_device";
    static private final String BUNDLE_KEY_CURRENT_VOLTAGE_FRAGMENT = "fragment_current_voltage";
    static private final String BUNDLE_KEY_MINUTELY_HISTORY_FRAGMENT = "fragment_minutely_history";
    static private final String BUNDLE_KEY_HOURLY_HISTORY_FRAGMENT = "fragment_hourly_history";
    static private final String BUNDLE_KEY_DAILY_HISTORY_FRAGMENT = "fragment_daily_history";

    static private final UUID gattServiceUUID = getUUID(BASE_UUID_MSB, BASE_UUID_LSB, SERVICE_ID);
    static private final UUID currentVoltageCharacteristicUUID =
            getUUID(BASE_UUID_MSB, BASE_UUID_LSB, CHARACTERISTIC_ID_CURRENT_VOLTAGE);
    static private final UUID minutelyHistoryCharacteristicUUID =
            getUUID(BASE_UUID_MSB, BASE_UUID_LSB, CHARACTERISTIC_ID_MINUTELY_HISTORY);
    static private final UUID hourlyHistoryCharacteristicUUID =
            getUUID(BASE_UUID_MSB, BASE_UUID_LSB, CHARACTERISTIC_ID_HOURLY_HISTORY);
    static private final UUID dailyHistoryCharacteristicUUID =
            getUUID(BASE_UUID_MSB, BASE_UUID_LSB, CHARACTERISTIC_ID_DAILY_HISTORY);
    static private final UUID clientCharacteristicConfigurationUUID =
            getUUID(STANDARD_BASE_UUID_MSB, STANDARD_BASE_UUID_LSB,
                    CLIENT_CHARACTERISTIC_CONFIGURATION_ID);

    // Timeout of a task in milliseconds.
    static private final int TASK_TIMOUT = 15000;

    static private final int MAX_HISTORY_SIZE = 128;

    private BluetoothAdapter bluetoothAdapter = null;
    private BluetoothDevice bluetoothDevice = null;

    // Handler to receive requests (e.g., from other views) to update the data model.
    public ModelUpdateTriggerHandler updateTriggerHandler = null;

    private CurrentVoltageFragment fragmentCurrentVoltage = null;
    private HistoryFragment fragmentMinutelyHistory = null;
    private HistoryFragment fragmentHourlyHistory = null;
    private HistoryFragment fragmentDailyHistory = null;

    // The currently active Bluetooth task for retrieving data from the GATT server.
    // Only one task can be active at a time.
    private BluetoothTasks activeBluetoothTask = BluetoothTasks.none;

    private Handler taskTimoutHandler = null;
    private TimeoutTask timeoutTask = null;

    private GattCallbackHandler gattHandler = null;
    private BluetoothGatt gatt = null;
    private BluetoothGattService gattService = null;
    private boolean gattServicesDiscovered = false;
    private BluetoothGattCharacteristic characteristic = null;

    private LinkedList<Integer> tempHistoryValues = null;

    private ProgressDialog progressDialog = null;

    /**
     * The model update trigger handler is used to signal that parts of the data model
     * should be updated by querying the GATT server.
     */
    public class ModelUpdateTriggerHandler extends Handler {

        public ModelUpdateTriggerHandler() {
        }

        @Override
        public void handleMessage(Message message) {
            switch (message.arg1) {
                case UPDATE_CURRENT_VOLTAGE:
                    startTask(BluetoothTasks.getVoltage);
                    break;
                case UPDATE_MINUTELY_HISTORY:
                    startTask(BluetoothTasks.getMinutelyHistory);
                    break;
                case UPDATE_HOURLY_HISTORY:
                    startTask(BluetoothTasks.getHourlyHistory);
                    break;
                case UPDATE_DAILY_HISTORY:
                    startTask(BluetoothTasks.getDailyHistory);
                    break;
            }
        }
    }

    /**
     * Adapter to integrated fragments into the tabs of the tab view.
     */
    public class FPAdapter extends FragmentPagerAdapter {
        private final int PAGE_COUNT = 4;

        public FPAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getCount() {
            return PAGE_COUNT;
        }

        @Override
        public Fragment getItem(int position) {
            Fragment fragment;
            switch (position) {
                case 0 :
                    fragmentCurrentVoltage = CurrentVoltageFragment.newInstance();
                    fragment = fragmentCurrentVoltage;
                    break;
                case 1:
                    fragmentMinutelyHistory = HistoryFragment.newInstance(
                            HistoryFragment.HistoryType.minutely);
                    fragment = fragmentMinutelyHistory;
                    break;
                case 2:
                    fragmentHourlyHistory = HistoryFragment.newInstance(
                            HistoryFragment.HistoryType.hourly);
                    fragment = fragmentHourlyHistory;
                    break;
                case 3:
                    fragmentDailyHistory = HistoryFragment.newInstance(
                            HistoryFragment.HistoryType.daily);
                    fragment = fragmentDailyHistory;
                    break;
                default:
                    fragment = null;
            }

            return fragment;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            // Generate tab labels based on tab position
            String tabLabel = null;

            switch (position) {
                case 0:
                    tabLabel = getResources().getString(R.string.tab_label_currentvoltage);
                    break;
                case 1:
                    tabLabel = getResources().getString(R.string.tab_label_minutelyhistory);
                    break;
                case 2:
                    tabLabel = getResources().getString(R.string.tab_label_hourlyhistory);
                    break;
                case 3:
                    tabLabel = getResources().getString(R.string.tab_label_dailyhistory);
                    break;
            }

            return tabLabel;
        }
    }

    /**
     * Handler for GATT callbacks.
     */
    private class GattCallbackHandler extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Bluetooth: connected");
                MainActivity.this.gatt = gatt;
                // Connected. Continue current task.
                switch (activeBluetoothTask) {
                    case getVoltage:
                        bleRetrieveCurrentVoltage();
                        break;
                    case getMinutelyHistory:
                        bleRetrieveHistory(HistoryTypes.historyMinutely);
                        break;
                    case getHourlyHistory:
                        bleRetrieveHistory(HistoryTypes.historyHourly);
                        break;
                    case getDailyHistory:
                        bleRetrieveHistory(HistoryTypes.historyDaily);
                        break;
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (activeBluetoothTask != BluetoothTasks.none) {
                    // Disconnection while performing task. Cancel task.
                    toast(R.string.err_bluetooth_connection);
                }
                finishTask();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                // Service discovery failed. Cancel task.
                toast(R.string.err_bluetooth_discovery);
                finishTask();
            } else {
                // Service discovered. Continue current task.
                gattServicesDiscovered = true;
                switch (activeBluetoothTask) {
                    case getVoltage:
                        bleRetrieveCurrentVoltage();
                        break;
                    case getMinutelyHistory:
                        bleRetrieveHistory(HistoryTypes.historyMinutely);
                        break;
                    case getHourlyHistory:
                        bleRetrieveHistory(HistoryTypes.historyHourly);
                        break;
                    case getDailyHistory:
                        bleRetrieveHistory(HistoryTypes.historyDaily);
                        break;
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                // Read operation failed. Cancel task.
                toast(R.string.err_bluetooth_read);
                finishTask();
            } else {
                // Successfully retrieved data.
                if (activeBluetoothTask == BluetoothTasks.getVoltage) {
                    // Update data model
                    int value = characteristic.getIntValue(
                            BluetoothGattCharacteristic.FORMAT_SINT16, 0);
                    DataModel.theModel.setCurrentVoltage(value);

                    // Notify view that value has been updated.
                    Message msg = fragmentCurrentVoltage.updateNotificationHandler.obtainMessage();
                    msg.arg1 = ModelUpdateNotificationHandler.VOLTAGE_UPDATED;
                    msg.sendToTarget();

                    // Finish task (close connection, release GATT resources)
                    finishTask();
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            int value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0);
            if (value == -1) {
                // -1 indicates the end of the history
                Message msg = null;
                switch (activeBluetoothTask) {
                    case getMinutelyHistory:
                        DataModel.theModel.setMinutelyHistory(tempHistoryValues);
                        msg = fragmentMinutelyHistory.updateNotificationHandler.obtainMessage();
                        msg.arg1 = ModelUpdateNotificationHandler.HISTORY_UPDATED_MINUTELY;
                        break;
                    case getHourlyHistory:
                        DataModel.theModel.setHourlyHistory(tempHistoryValues);
                        msg = fragmentHourlyHistory.updateNotificationHandler.obtainMessage();
                        msg.arg1 = ModelUpdateNotificationHandler.HISTORY_UPDATED_HOURLY;
                        break;
                    case getDailyHistory:
                        DataModel.theModel.setDailyHistory(tempHistoryValues);
                        msg = fragmentDailyHistory.updateNotificationHandler.obtainMessage();
                        msg.arg1 = ModelUpdateNotificationHandler.HISTORY_UPDATED_DAILY;
                        break;
                    default:
                        // Should never happen since indications are only sent for histories.
                        return;
                }

                // Notify corresponding fragment to update view.
                msg.sendToTarget();

                // Finish task (close connection, release GATT resources)
                finishTask();
            } else {
                // The history is transmitted in reverse chronological order (newest first).
                // Thus, we need to add values at the list head to achieve chronological
                // order in the end.
                Log.i(TAG, Integer.toString(value));
                tempHistoryValues.addFirst(value);
                ProgressDialog dlg = progressDialog;
                if (dlg != null) {
                    dlg.setProgress(tempHistoryValues.size());
                }
            }
        }
    }

    /**
     * Task for showing a toast on the UI thread.
     */
    private class ToastTask implements Runnable {
        int stringResourceId;

        public ToastTask(int stringResourceId) {
            this.stringResourceId = stringResourceId;
        }

        @Override
        public void run() {
            String message = getResources().getString(stringResourceId);

            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Task performed after a Bluetooth task timed-out.
     */
    private class TimeoutTask implements Runnable {

        @Override
        public void run() {
            toast(R.string.err_bluetooth_timeout);
            finishTask();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        restoreDataModel(savedInstanceState);

        if (bluetoothAdapter == null) {
            BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(
                    Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();
        }

        if (savedInstanceState != null &&
                savedInstanceState.containsKey(BUNDLE_KEY_BLUETOOTH_DEVICE)) {
            bluetoothDevice = savedInstanceState.getParcelable(BUNDLE_KEY_BLUETOOTH_DEVICE);
        }

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        gattHandler = new GattCallbackHandler();

        taskTimoutHandler = new Handler();

        updateTriggerHandler = new ModelUpdateTriggerHandler();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        String appName = getString(R.string.app_name);
        toolbar.setTitle(appName);
        setSupportActionBar(toolbar);

        FragmentManager fm = getSupportFragmentManager();
        ViewPager viewPager = (ViewPager) findViewById(R.id.viewpager);
        FPAdapter fpAdapter = new FPAdapter(fm);
        viewPager.setAdapter(fpAdapter);

        // Get references to already existing tags
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(BUNDLE_KEY_CURRENT_VOLTAGE_FRAGMENT)) {
                String tag = savedInstanceState.getString(BUNDLE_KEY_CURRENT_VOLTAGE_FRAGMENT);
                fragmentCurrentVoltage = (CurrentVoltageFragment) fm.findFragmentByTag(tag);
            }
            if (savedInstanceState.containsKey(BUNDLE_KEY_MINUTELY_HISTORY_FRAGMENT)) {
                String tag = savedInstanceState.getString(BUNDLE_KEY_MINUTELY_HISTORY_FRAGMENT);
                fragmentMinutelyHistory = (HistoryFragment) fm.findFragmentByTag(tag);
            }
            if (savedInstanceState.containsKey(BUNDLE_KEY_HOURLY_HISTORY_FRAGMENT)) {
                String tag = savedInstanceState.getString(BUNDLE_KEY_HOURLY_HISTORY_FRAGMENT);
                fragmentHourlyHistory = (HistoryFragment) fm.findFragmentByTag(tag);
            }
            if (savedInstanceState.containsKey(BUNDLE_KEY_DAILY_HISTORY_FRAGMENT)) {
                String tag = savedInstanceState.getString(BUNDLE_KEY_DAILY_HISTORY_FRAGMENT);
                fragmentDailyHistory = (HistoryFragment) fm.findFragmentByTag(tag);
            }
        }

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tablayout);
        tabLayout.setupWithViewPager(viewPager);
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        fragmentCurrentVoltage = null;
        fragmentMinutelyHistory = null;
        fragmentHourlyHistory = null;
        fragmentDailyHistory = null;
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        // Save the data model

        savedInstanceState.putInt(BUNDLE_KEY_CURRENT_VOLTAGE,
                DataModel.theModel.getCurrentVoltage());

        List<Integer> minutelyHistory = DataModel.theModel.getMinutelyHistory();
        if (minutelyHistory != null) {
            ArrayList<Integer> values = new ArrayList<>(minutelyHistory);
            savedInstanceState.putIntegerArrayList(BUNDLE_KEY_HISTORY_MINUTELY, values);
        }

        List<Integer> hourlyHistory = DataModel.theModel.getHourlyHistory();
        if (hourlyHistory != null) {
            ArrayList<Integer> values = new ArrayList<>(hourlyHistory);
            savedInstanceState.putIntegerArrayList(BUNDLE_KEY_HISTORY_HOURLY, values);
        }

        List<Integer> dailyHistory = DataModel.theModel.getDailyHistory();
        if (dailyHistory != null) {
            ArrayList<Integer> values = new ArrayList<>(dailyHistory);
            savedInstanceState.putIntegerArrayList(BUNDLE_KEY_HISTORY_DAILY, values);
        }

        // Save information about selected Bluetooth device
        // TODO: Move this to preferences?
        if (bluetoothDevice != null) {
            savedInstanceState.putParcelable(BUNDLE_KEY_BLUETOOTH_DEVICE, bluetoothDevice);
        }

        // Save tags of existing fragments so we can retrieve fragment references later in the
        // newly created activity via the fragments manager.

        if (fragmentCurrentVoltage != null) {
            String tag = fragmentCurrentVoltage.getTag();
            savedInstanceState.putString(BUNDLE_KEY_CURRENT_VOLTAGE_FRAGMENT, tag);
        }

        if (fragmentMinutelyHistory != null) {
            String tag = fragmentMinutelyHistory.getTag();
            savedInstanceState.putString(BUNDLE_KEY_MINUTELY_HISTORY_FRAGMENT, tag);
        }

        if (fragmentHourlyHistory != null) {
            String tag = fragmentHourlyHistory.getTag();
            savedInstanceState.putString(BUNDLE_KEY_HOURLY_HISTORY_FRAGMENT, tag);
        }

        if (fragmentDailyHistory != null) {
            String tag = fragmentDailyHistory.getTag();
            savedInstanceState.putString(BUNDLE_KEY_DAILY_HISTORY_FRAGMENT, tag);
        }
    }

    /**
     * Creates a 128 bit UUID of a service or characteristic from a 128 base UUID and 16 bit
     * service/characteristic id.
     *
     * Example: Given
     * - 128 bit base UUID 550eXXXX-e29b-11d4-a716-446655440000
     * - 16 bit service ID: 0x1234
     * The resulting UUID is generated by replacing XXXX by the 16 bit id of the service:
     * - UUID: 550e1234-e29b-11d4-a716-446655440000
     *
     * @param baseMSB most significant bits of the base UUID
     * @param baseLSB least significant bits of the base UUID
     * @param id 16 bit id of the service or characteristic
     * @return UUID of the service of characteristic
     */
    public static UUID getUUID(long baseMSB, long baseLSB, short id) {
        long msb = baseMSB & 0xffff0000ffffffffL;
        msb |= ((long) id)<<32;

        return new UUID(msb, baseLSB);
    }

    /**
     * Show a toast. This method ensures that the toast is displayed on the UI thread.
     *
     * @param stringResource id of the string resource defining the message.
     */
    public void toast(int stringResource) {
        runOnUiThread(new ToastTask(stringResource));
    }

    /**
     * Restore the data model from saved instance state.
     * It is save to call this method with empty or partial state only containing portions
     * of the data model.
     *
     * @param savedInstanceState the instance state containing portions of the data model
     */
    private void restoreDataModel(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            return;
        }

        // There is some data to be restored.
        if (savedInstanceState.containsKey(BUNDLE_KEY_CURRENT_VOLTAGE)) {
            DataModel.theModel.setCurrentVoltage(savedInstanceState.getInt(
                    BUNDLE_KEY_CURRENT_VOLTAGE));
        }

        ArrayList<Integer> values =
                savedInstanceState.getIntegerArrayList(BUNDLE_KEY_HISTORY_MINUTELY);
        if (values != null) {
            DataModel.theModel.setMinutelyHistory(values);
        }

        values = savedInstanceState.getIntegerArrayList(BUNDLE_KEY_HISTORY_HOURLY);
        if (values != null) {
            DataModel.theModel.setHourlyHistory(values);
        }

        values = savedInstanceState.getIntegerArrayList(BUNDLE_KEY_HISTORY_DAILY);
        if (values != null) {
            DataModel.theModel.setDailyHistory(values);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //toolbar.inflateMenu(R.menu.menu_main);
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.action_scan) {
            selectDevice();
            return true;
        }

        if (id == R.id.action_about) {
            showAboutDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                    // Bluetooth has been enabled. Continue task.
                    switch (activeBluetoothTask) {
                        case getVoltage:
                            bleRetrieveCurrentVoltage();
                            break;
                        case getMinutelyHistory:
                            bleRetrieveHistory(HistoryTypes.historyMinutely);
                            break;
                        case getHourlyHistory:
                            bleRetrieveHistory(HistoryTypes.historyHourly);
                            break;
                        case getDailyHistory:
                            bleRetrieveHistory(HistoryTypes.historyDaily);
                            break;
                    }
                } else {
                    // No Bluetooth available. Cancel task.
                    finishTask();
                }
                break;
            case REQUEST_SELECT_DEVICE:
                if (resultCode == RESULT_OK) {
                    // Bluetooth device has been selected. Continue task.
                    bluetoothDevice = data.getParcelableExtra(
                            DeviceSelectionActivity.RESULT_BLUETOOTHDEVICE);
                    switch (activeBluetoothTask) {
                        case getVoltage:
                            bleRetrieveCurrentVoltage();
                            break;
                        case getMinutelyHistory:
                            bleRetrieveHistory(HistoryTypes.historyMinutely);
                            break;
                        case getHourlyHistory:
                            bleRetrieveHistory(HistoryTypes.historyHourly);
                            break;
                        case getDailyHistory:
                            bleRetrieveHistory(HistoryTypes.historyDaily);
                            break;
                    }
                } else {
                    // No suitable Bluetooth device found. Cancel task.
                    finishTask();
                }
                break;
        }
    }

    /**
     * Trigger selection of Bluetooth device in dedicated activity.
     */
    private void selectDevice() {
        Intent intent = new Intent(this, DeviceSelectionActivity.class);
        startActivityForResult(intent, REQUEST_SELECT_DEVICE);
    }

    /**
     * Query the GATT server for current voltage data.
     */
    synchronized private void bleRetrieveCurrentVoltage() {
        if (activeBluetoothTask != BluetoothTasks.getVoltage) {
            // Task cancelled
            return;
        }

        // Check whether a BLE device has already been selected. If not, select it first.
        if (bluetoothDevice == null) {
            // No device selected so far -> let user select one now
            selectDevice();
            return; // Wait for activity result
        }

        // BLE device has been selected.

        if (!bluetoothAdapter.isEnabled()) {
            // First, turn on Bluetooth
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_ENABLE_BT);
            return; // Wait for activity result
        }

        // BLE device has been selected. BLE is turned on.

        if (gatt == null) {
            bluetoothDevice.connectGatt(this, false, gattHandler);
            return; // Wait for callback
        }

        // BLE device has been selected. BLE is turned on. We are connected to GATT server.

        if (!gattServicesDiscovered) {
            if (!gatt.discoverServices()) {
                // Cannot start discovery
                toast(R.string.err_bluetooth_discovery);
                finishTask();
                return;
            }
            return; // Wait for GATT callback
        }

        // BLE device has been selected. BLE is turned on. We are connected to GATT server.
        // GATT services have been discovered.

        gattService = gatt.getService(gattServiceUUID);
        if (gattService == null) {
            // Required service not offered by device
            toast(R.string.err_bluetooth_service);
            finishTask();
            return;
        }

        // BLE device has been selected. BLE is turned on. We are connected to GATT server.
        // GATT services have been discovered. Service is ready.

        characteristic = gattService.getCharacteristic(currentVoltageCharacteristicUUID);
        if (characteristic == null) {
            // Required characteristic is not available.
            toast(R.string.err_bluetooth_characteristic);
            finishTask();
            return;
        }

        // BLE device has been selected. BLE is turned on. We are connected to GATT server.
        // GATT services have been discovered. Service is ready. Characteristic is available.

        if (!gatt.readCharacteristic(characteristic)) {
            // Cannot read characteristic value
            toast(R.string.err_bluetooth_read);
            finishTask();
            return;
        }

        // Data is received and processed in GATT callback. Wait for GATT callback.
    }

    /**
     * Starts a task.
     */
    synchronized private void startTask(BluetoothTasks task) {
        if (activeBluetoothTask != BluetoothTasks.none) {
            // Only one task at a time
            return;
        }

        Log.i(TAG, "Starting Bluetooth task" + task.toString());

        switch (task) {
            case getVoltage:
                activeBluetoothTask = BluetoothTasks.getVoltage;
                bleRetrieveCurrentVoltage();
                break;
            case getMinutelyHistory:
                activeBluetoothTask = BluetoothTasks.getMinutelyHistory;
                bleRetrieveHistory(HistoryTypes.historyMinutely);
                tempHistoryValues = new LinkedList<>();
                break;
            case getHourlyHistory:
                activeBluetoothTask = BluetoothTasks.getHourlyHistory;
                bleRetrieveHistory(HistoryTypes.historyHourly);
                tempHistoryValues = new LinkedList<>();
                break;
            case getDailyHistory:
                activeBluetoothTask = BluetoothTasks.getDailyHistory;
                bleRetrieveHistory(HistoryTypes.historyDaily);
                tempHistoryValues = new LinkedList<>();
                break;
        }
    }

    /**
     * Finish a running Bluetooth task and release GATT resources.
     */
    synchronized private void finishTask() {
        Log.i(TAG, "Finishing Bluetooth task");

        // Cancel task
        activeBluetoothTask = BluetoothTasks.none;

        // Stop timeout timer
        if (timeoutTask != null) {
            taskTimoutHandler.removeCallbacks(timeoutTask);
            timeoutTask = null;
        }

        // Release all GATT resources
        gattService = null;
        characteristic = null;
        gattServicesDiscovered = false;
        if (gatt != null) {
            gatt.close();
            gatt = null;
            Log.i(TAG, "Bluetooth: diconnecting");
        }

        tempHistoryValues = null;

        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    /**
     * Query the GATT server for a voltage history.
     *
     * @param historyType the history to be retrieved from the GATT server
     */
    synchronized private void bleRetrieveHistory(HistoryTypes historyType) {
        switch (historyType) {
            case historyMinutely:
                if (activeBluetoothTask != BluetoothTasks.getMinutelyHistory) {
                    return;
                }
                break;
            case historyHourly:
                if (activeBluetoothTask != BluetoothTasks.getHourlyHistory) {
                    return;
                }
                break;
            case historyDaily:
                if (activeBluetoothTask != BluetoothTasks.getDailyHistory) {
                    return;
                }
                break;
        }

        // Check whether a BLE device has already been selected. If not, select device first.
        if (bluetoothDevice == null) {
            // No device selected so far -> let user select device now
            selectDevice();
            return; // Wait for activity result
        }

        // BLE device has been selected.

        if (!bluetoothAdapter.isEnabled()) {
            // First, turn on Bluetooth
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_ENABLE_BT);
            return; // Wait for activity result
        }

        // BLE device has been selected. BLE is turned on.

        // From here on, we show a progress dialog ... retrieving hundreds of
        // BLE indications (stop&wait protocol) can take longer.
        if (progressDialog == null) {
            String dialogTitle = getString(R.string.waiting);
            String dialogMessage = getString(R.string.download_in_progress);
            String cancel = getString(R.string.cancel);
            progressDialog = new ProgressDialog(this);
            progressDialog.setTitle(dialogTitle);
            progressDialog.setMessage(dialogMessage);
            progressDialog.setCancelable(true);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setMax(MAX_HISTORY_SIZE);
            progressDialog.setProgress(0);
            progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, cancel,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finishTask();
                        }
                    });
            progressDialog.show();
        }

        if (gatt == null) {
            bluetoothDevice.connectGatt(this, false, gattHandler);
            return; // Wait for callback
        }

        // BLE device has been selected. BLE is turned on. We are connected to GATT server.

        if (!gattServicesDiscovered) {
            if (!gatt.discoverServices()) {
                // Cannot start discovery
                toast(R.string.err_bluetooth_discovery);
                finishTask();
                return;
            }
            return; // Wait for GATT callback
        }

        // BLE device has been selected. BLE is turned on. We are connected to GATT server.
        // GATT services have been discovered.

        gattService = gatt.getService(gattServiceUUID);
        if (gattService == null) {
            // Required service not offered by device
            toast(R.string.err_bluetooth_service);
            finishTask();
            return;
        }

        // BLE device has been selected. BLE is turned on. We are connected to GATT server.
        // GATT services have been discovered. Service is ready.

        switch (historyType) {
            case historyMinutely:
                characteristic = gattService.getCharacteristic(minutelyHistoryCharacteristicUUID);
                break;
            case historyHourly:
                characteristic = gattService.getCharacteristic(hourlyHistoryCharacteristicUUID);
                break;
            case historyDaily:
                characteristic = gattService.getCharacteristic(dailyHistoryCharacteristicUUID);
                break;
        }

        if (characteristic == null) {
            // Required characteristic is not available.
            toast(R.string.err_bluetooth_characteristic);
            finishTask();
            return;
        }

        // BLE device has been selected. BLE is turned on. We are connected to GATT server.
        // GATT services have been discovered. Service is ready. Characteristic is available.

        // History values are returned as indications. -> subscribe for notifications.
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            toast(R.string.err_bluetooth_notification);
            finishTask();
            return;
        }
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                clientCharacteristicConfigurationUUID);
        if (descriptor == null) {
            toast(R.string.err_bluetooth_notification);
            finishTask();
            return;
        }
        if (!descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
            toast(R.string.err_bluetooth_notification);
            finishTask();
            return;
        }
        if (!gatt.writeDescriptor(descriptor)) {
            toast(R.string.err_bluetooth_notification);
            finishTask();
            return;
        }

        // Data is received and processed in GATT callback. Wait for GATT callback.
    }

    private void showAboutDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_about, null, false);
        TextView textView = (TextView) view.findViewById(R.id.about_message);
        textView.setText(Html.fromHtml(getString(R.string.about_message)));
        textView.setMovementMethod(LinkMovementMethod.getInstance());

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.mipmap.ic_launcher);
        builder.setTitle(R.string.app_name);
        builder.setView(view);
        builder.create();
        builder.show();
    }
}
