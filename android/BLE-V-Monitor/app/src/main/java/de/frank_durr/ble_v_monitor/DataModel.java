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

import java.util.List;

/**
 * The central data model storing all data of the remote GATT server.
 */
public class DataModel {

    public static final int INVALID_VOLTAGE = Integer.MIN_VALUE;

    public static final DataModel theModel = new DataModel();

    private int currentVoltage = INVALID_VOLTAGE;
    private List<Integer> minutelyHistory = null;
    private List<Integer> hourlyHistory = null;
    private List<Integer> dailyHistory = null;

    public DataModel() {
    }

    public int getCurrentVoltage() {
        return currentVoltage;
    }

    public List<Integer> getMinutelyHistory() {
        return minutelyHistory;
    }

    public List<Integer> getHourlyHistory() {
        return hourlyHistory;
    }

    public List<Integer> getDailyHistory() {
        return dailyHistory;
    }

    public void setCurrentVoltage(int currentVoltage) {
        this.currentVoltage = currentVoltage;
    }

    public void setMinutelyHistory(List<Integer> history) {
        minutelyHistory = history;
    }

    public void setHourlyHistory(List<Integer> history) {
        hourlyHistory = history;
    }

    public void setDailyHistory(List<Integer> history) {
        dailyHistory = history;
    }
}
