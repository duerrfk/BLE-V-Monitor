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

import android.os.Handler;

/**
 * Handler signaling updates of the central data model.
 */
public class ModelUpdateNotificationHandler extends Handler {
    public static final int VOLTAGE_UPDATED = 1;
    public static final int HISTORY_UPDATED_MINUTELY = 2;
    public static final int HISTORY_UPDATED_HOURLY = 3;
    public static final int HISTORY_UPDATED_DAILY = 4;
}
