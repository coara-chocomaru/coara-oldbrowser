package com.coara.oldbrowser;

import android.os.Bundle;
import android.view.WindowManager;


public final class SecretTouchpadActivity extends TouchpadActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected String getPreferenceName() {
        return "SecretBrowserPrefs";
    }

}
