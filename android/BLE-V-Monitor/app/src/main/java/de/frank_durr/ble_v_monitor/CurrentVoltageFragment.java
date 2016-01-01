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

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;


/**
 * Fragment displaying current voltage information from the data model.
 */
public class CurrentVoltageFragment extends Fragment {

    public static final String TAG = CurrentVoltageFragment.class.getName();

    // Voltage values to determine battery status.
    // For instance, we assume the battery to be charged 50 %, if the voltage is within
    // the interval [ (CHARGED_25+CHARGED_50)/2 , (CHARGED_50+CHARGED_75)/2 ]
    private static final double CHARGED_100 = 12.66;
    private static final double CHARGED_75 = 12.35;
    private static final double CHARGED_50 = 12.10;
    private static final double CHARGED_25 = 11.95;
    private static final double CHARGED_0 = 11.7;

    private TextView textViewBatteryVoltage;
    private TextView textViewChargeStatus;
    private ImageView imageViewChargeStatus;

    private MainActivity activity = null;

    // Handler to receive a notification when the data model has been updated.
    public ModelUpdateNotificationHandler updateNotificationHandler = null;

    /**
     * The update notification handler receives a message when the data model has been
     * updated, and then triggers a view update.
     */
    private static class UpdateNotificationHandler extends ModelUpdateNotificationHandler {
        private final WeakReference<CurrentVoltageFragment> fragment;

        public UpdateNotificationHandler(CurrentVoltageFragment fragment) {
            this.fragment = new WeakReference<>(fragment);
        }

        @Override
        public void handleMessage(Message message) {
            fragment.get().updateView();
        }
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment CurrentVoltageFragment.
     */
    public static CurrentVoltageFragment newInstance() {
        CurrentVoltageFragment fragment = new CurrentVoltageFragment();

        Bundle args = new Bundle();
        fragment.setArguments(args);

        return fragment;
    }

    /**
     * An empty constructor is required.
     */
    public CurrentVoltageFragment() {
    }

    /**
     * Update the view according to the data of the data model.
     */
    private void updateView() {
        if (DataModel.theModel.getCurrentVoltage() == DataModel.INVALID_VOLTAGE) {
            textViewBatteryVoltage.setText(R.string.unknown);
            textViewChargeStatus.setText(R.string.unknown);
            imageViewChargeStatus.setImageResource(R.drawable.ic_battery_unknown);
        } else {
            double voltage = (double) (DataModel.theModel.getCurrentVoltage())/1000.0;

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            float voltageOffset = prefs.getFloat("pref_voltage_offset", 0.0f);
            voltage += voltageOffset;

            String strVoltage = Double.toString(voltage);
            textViewBatteryVoltage.setText(strVoltage);
            // Assign the battery voltage to one of the charge levels 0 %, 25 %, 50 %, 75 %, 100 %
            // by checking whether the battery voltage falls into a certain voltage interval.
            // For instance, we assume the battery to be charged 50 %, if the voltage is within
            // the interval [ (CHARGED_25+CHARGED_50)/2 , (CHARGED_50+CHARGED_75)/2 ]
            if (voltage < (CHARGED_0+CHARGED_25)/2.0) {
                textViewChargeStatus.setText(R.string.charged0);
                imageViewChargeStatus.setImageResource(R.drawable.ic_battery_0);
            } else if (voltage < (CHARGED_25+CHARGED_50)/2.0) {
                textViewChargeStatus.setText(R.string.charged25);
                imageViewChargeStatus.setImageResource(R.drawable.ic_battery_25);
            } else if (voltage < (CHARGED_50+CHARGED_75)/2.0) {
                textViewChargeStatus.setText(R.string.charged50);
                imageViewChargeStatus.setImageResource(R.drawable.ic_battery_50);
            } else if (voltage < (CHARGED_75+CHARGED_100)/2.0) {
                textViewChargeStatus.setText(R.string.charged75);
                imageViewChargeStatus.setImageResource(R.drawable.ic_battery_75);
            } else {
                // Charged 100 %
                textViewChargeStatus.setText(R.string.charged100);
                imageViewChargeStatus.setImageResource(R.drawable.ic_battery_100);
            }
        }
    }

    /**
     * Trigger an update of the voltage data of the data model by querying the GATT server.
     * All interaction with the GATT server is done by the main activity, thus, we need
     * to send a message to the main activity to trigger the update. After the data model
     * has been updated, this fragment will receive a notification via the
     * update notification handler.
     */
    private void triggerVoltageUpdate() {
        if (activity.updateTriggerHandler != null) {
            Message msg = activity.updateTriggerHandler.obtainMessage();
            msg.arg1 = MainActivity.UPDATE_CURRENT_VOLTAGE;
            msg.sendToTarget();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_current_voltage, container, false);

        FloatingActionButton fab = (FloatingActionButton)
                v.findViewById(R.id.fab_update_currentvoltage);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                triggerVoltageUpdate();
            }
        });

        textViewBatteryVoltage = (TextView) v.findViewById(R.id.textview_value_batteryvoltage);
        textViewChargeStatus = (TextView) v.findViewById(R.id.textview_value_chargestatus);
        imageViewChargeStatus = (ImageView) v.findViewById(R.id.imageView_chargestatus);
        updateView();

        updateNotificationHandler = new UpdateNotificationHandler(this);

        return v;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // TODO
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = (MainActivity) activity;
    }

    @Override
    public void onDetach()  {
        super.onDetach();
        activity = null;
    }
}
