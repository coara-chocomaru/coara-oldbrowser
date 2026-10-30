package com.coara.oldbrowser.util;

import android.app.AlertDialog;
import android.content.Context;

import com.coara.oldbrowser.R;

public final class Dialogs {
    private Dialogs() {
    }

    public static AlertDialog.Builder builder(Context context) {
        return new AlertDialog.Builder(context, R.style.Theme_CoaraBrowser_Dialog);
    }
}
