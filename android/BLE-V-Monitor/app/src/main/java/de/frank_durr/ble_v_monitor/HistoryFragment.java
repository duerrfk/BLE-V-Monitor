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
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;


/**
 * Fragment displaying history information from the data model.
 * The fragment is parametrized to show minutely, hourly, or daily history information.
 */
public class HistoryFragment extends Fragment {

    static final String TAG = HistoryFragment.class.getName();

    static final String BUNDLE_KEY_HISTORY_TYPE = "history_type";

    public enum HistoryType {minutely, hourly, daily};
    private HistoryType historyType;

    private MainActivity activity = null;

    // Handler to receive a notification when the data model has been updated.
    public ModelUpdateNotificationHandler updateNotificationHandler = null;

    private LineChart chart = null;

    /**
     * The update notification handler receives a message when the data model has been
     * updated, and then triggers a view update.
     */
    private static class UpdateNotificationHandler extends ModelUpdateNotificationHandler {
        private final WeakReference<HistoryFragment> fragment;

        public UpdateNotificationHandler(HistoryFragment fragment) {
            this.fragment = new WeakReference<>(fragment);
        }

        @Override
        public void handleMessage(Message message) {
            HistoryFragment f = fragment.get();

            if (message.arg1 == ModelUpdateNotificationHandler.HISTORY_UPDATED_MINUTELY &&
                    f.historyType == HistoryType.minutely) {
                f.updateView();
            }

            if (message.arg1 == ModelUpdateNotificationHandler.HISTORY_UPDATED_HOURLY &&
                    f.historyType == HistoryType.hourly) {
                f.updateView();
            }

            if (message.arg1 == ModelUpdateNotificationHandler.HISTORY_UPDATED_DAILY &&
                    f.historyType == HistoryType.daily) {
                f.updateView();
            }
        }
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param historyType Type of history managed by this fragment
     *
     * @return A new instance of fragment HistoryFragment.
     */
    public static HistoryFragment newInstance(HistoryType historyType) {
        HistoryFragment fragment = new HistoryFragment();
        fragment.historyType = historyType;
        Bundle args = new Bundle();
        fragment.setArguments(args);

        return fragment;
    }

    /**
     * Required empty public constructor
     */
    public HistoryFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_history, container, false);

        FloatingActionButton fab = (FloatingActionButton)
                v.findViewById(R.id.fab_update_history);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                triggerHistoryUpdate();
            }
        });

        chart = (LineChart) v.findViewById(R.id.charthistory);

        updateNotificationHandler = new UpdateNotificationHandler(this);

        if (savedInstanceState != null) {
            int i = savedInstanceState.getInt(getTag()+BUNDLE_KEY_HISTORY_TYPE);
            switch (i) {
                case 0:
                    historyType = HistoryType.minutely;
                    break;
                case 1:
                    historyType = HistoryType.hourly;
                    break;
                case 2:
                    historyType = HistoryType.daily;
                    break;
            }
        }

        return v;
    }

    @Override
    public void onStart() {
        super.onStart();

        Log.i(TAG, "Started");
        updateView();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        int i = 0;
        switch (historyType) {
            case minutely:
                i = 0;
                break;
            case hourly:
                i = 1;
                break;
            case daily:
                i = 2;
                break;
        }
        outState.putInt(getTag()+BUNDLE_KEY_HISTORY_TYPE, i);
    }

    /**
     * Update the view data according to the data stored by the data model.
     */
    private void updateView() {
        List<Integer> historyData = null;
        String label = null;
        String timeUnitStr = null;
        switch (historyType) {
            case minutely:
                historyData = DataModel.theModel.getMinutelyHistory();
                label = getResources().getString(R.string.minutely_history);
                timeUnitStr = "min";
                break;
            case hourly:
                historyData = DataModel.theModel.getHourlyHistory();
                label = getResources().getString(R.string.hourly_history);
                timeUnitStr = "h";
                break;
            case daily:
                historyData = DataModel.theModel.getDailyHistory();
                label = getResources().getString(R.string.daily_history);
                timeUnitStr = "d";
                break;
        }

        if (historyData == null) {
            // No history data in data model
            return;
        }

        ArrayList<Entry> values = new ArrayList<>();
        int x = 0;
        for (Integer value: historyData) {
            float v = (float) (value.floatValue()/1000.0);
            Entry entry = new Entry(v, x++);
            values.add(entry);
        }

        LineDataSet dataSet = new LineDataSet(values, label);
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleSize(4.5f);
        dataSet.setColor(Color.rgb(255, 0, 0));
        dataSet.setCircleColor(Color.rgb(255, 0, 0));
        dataSet.setHighLightColor(Color.rgb(255, 0, 0));
        dataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        dataSet.setDrawValues(false);
        //dataSet.setValueTextSize(10f);

        ArrayList<LineDataSet> dataSets = new ArrayList<>();
        dataSets.add(dataSet);

        int xValueCnt = historyData.size();
        ArrayList<String> xVals = new ArrayList<>();
        for (int i = 0; i < xValueCnt; i++) {
            xVals.add(Integer.toString(-xValueCnt+i+1) + timeUnitStr);
        }
        LineData data = new LineData(xVals, dataSets);

        chart.setDescription("");

        // Refresh chart
        chart.setData(data);
        chart.notifyDataSetChanged();
        chart.invalidate();
    }

    /**
     * Trigger an update of the corresponding history of the data model by querying the GATT server.
     * All interaction with the GATT server is done by the main activity, thus, we need
     * to send a message to the main activity to trigger the update. After the data model
     * has been updated, this fragment will receive a notification via the
     * update notification handler.
     */
    private void triggerHistoryUpdate() {
        if (activity.updateTriggerHandler != null) {
            Message msg = activity.updateTriggerHandler.obtainMessage();
            switch (historyType) {
                case minutely:
                    msg.arg1 = MainActivity.UPDATE_MINUTELY_HISTORY;
                    break;
                case hourly:
                    msg.arg1 = MainActivity.UPDATE_HOURLY_HISTORY;
                    break;
                case daily:
                    msg.arg1 = MainActivity.UPDATE_DAILY_HISTORY;
                    break;
            }
            msg.sendToTarget();
        }
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
