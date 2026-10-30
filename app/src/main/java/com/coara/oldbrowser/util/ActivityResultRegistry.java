package com.coara.oldbrowser.util;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ActivityResultRegistry {
    private static final int BASE_CODE = 0x4000;
    private static final int MAX_CODES = 0x3000;

    public interface Callback<T> {
        void onResult(T value);
    }

    public static final class Result {
        private final int resultCode;
        private final Intent data;

        Result(int resultCode, Intent data) {
            this.resultCode = resultCode;
            this.data = data;
        }

        public int getResultCode() {
            return resultCode;
        }

        public Intent getData() {
            return data;
        }
    }

    public abstract static class Launcher<I> {
        public abstract void launch(I input);
    }

    private final Activity host;
    private final Map<Integer, Callback<Result>> intentCallbacks = new LinkedHashMap<>();
    private final Map<Integer, Callback<Boolean>> permissionCallbacks = new LinkedHashMap<>();
    private final Map<Integer, Callback<Map<String, Boolean>>> permissionMapCallbacks = new LinkedHashMap<>();
    private int next;

    public ActivityResultRegistry(Activity host) {
        this.host = host;
    }

    private int nextCode() {
        return BASE_CODE + (next++ % MAX_CODES);
    }

    public Launcher<Intent> forIntent(final Callback<Result> callback) {
        final int code = nextCode();
        intentCallbacks.put(code, callback);
        return new Launcher<Intent>() {
            @Override
            public void launch(Intent input) {
                host.startActivityForResult(input, code);
            }
        };
    }

    public Launcher<String> forGetContent(final Callback<Uri> callback) {
        final int code = nextCode();
        intentCallbacks.put(code, new Callback<Result>() {
            @Override
            public void onResult(Result value) {
                Uri uri = value.getResultCode() == Activity.RESULT_OK && value.getData() != null
                        ? value.getData().getData() : null;
                callback.onResult(uri);
            }
        });
        return new Launcher<String>() {
            @Override
            public void launch(String mimeType) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(mimeType);
                host.startActivityForResult(intent, code);
            }
        };
    }

    public Launcher<String> forPermission(final Callback<Boolean> callback) {
        final int code = nextCode();
        permissionCallbacks.put(code, callback);
        return new Launcher<String>() {
            @Override
            public void launch(String permission) {
                host.requestPermissions(new String[]{permission}, code);
            }
        };
    }

    public Launcher<String[]> forPermissions(final Callback<Map<String, Boolean>> callback) {
        final int code = nextCode();
        permissionMapCallbacks.put(code, callback);
        return new Launcher<String[]>() {
            @Override
            public void launch(String[] permissions) {
                if (permissions == null || permissions.length == 0) {
                    callback.onResult(new LinkedHashMap<String, Boolean>());
                    return;
                }
                host.requestPermissions(permissions, code);
            }
        };
    }

    public boolean dispatchResult(int requestCode, int resultCode, Intent data) {
        Callback<Result> callback = intentCallbacks.get(requestCode);
        if (callback == null) {
            return false;
        }
        callback.onResult(new Result(resultCode, data));
        return true;
    }

    public boolean dispatchPermissions(int requestCode, String[] permissions, int[] grantResults) {
        Callback<Boolean> single = permissionCallbacks.get(requestCode);
        if (single != null) {
            single.onResult(grantResults != null && grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED);
            return true;
        }
        Callback<Map<String, Boolean>> multi = permissionMapCallbacks.get(requestCode);
        if (multi == null) {
            return false;
        }
        Map<String, Boolean> map = new LinkedHashMap<>();
        if (permissions != null && grantResults != null) {
            for (int i = 0; i < permissions.length && i < grantResults.length; i++) {
                map.put(permissions[i], grantResults[i] == PackageManager.PERMISSION_GRANTED);
            }
        }
        multi.onResult(map);
        return true;
    }
}
