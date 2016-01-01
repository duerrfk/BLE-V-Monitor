package de.frank_durr.ble_v_monitor;

import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;

/**
 * An edit text preference managing float values instead of strings.
 */
public class FloatEditTextPreference  extends EditTextPreference {
    public FloatEditTextPreference(Context context) {
        super(context);
    }

    public FloatEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FloatEditTextPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected String getPersistedString(String defaultReturnValue) {
        return String.valueOf(getPersistedFloat(0.0f));
    }

    @Override
    protected boolean persistString(String value) {
        return persistFloat(Float.valueOf(value));
    }
}
