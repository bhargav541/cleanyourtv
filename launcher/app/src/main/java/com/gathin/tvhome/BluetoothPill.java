package com.gathin.tvhome;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.SparseArray;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * config.xml bluetooth_pill: a round Bluetooth button beside Wi-Fi, whose description names the connected devices.
 * Remotes and other plain input peripherals are left out, since the TV's own remote is connected all the time.
 * Listens only between start and stop (Home on screen): connection broadcasts and the profile proxies, no polling.
 */
@SuppressLint("MissingPermission")   // BLUETOOTH is granted at install up to Android 11; canRead checks CONNECT after
final class BluetoothPill {
    // Hidden before Android 12 but stable: BluetoothProfile.INPUT_DEVICE (HID host) and A2DP_SINK.
    private static final int INPUT_DEVICE = 4;
    private static final int A2DP_SINK = 11;
    private static final int[] PROFILES = {BluetoothProfile.A2DP, BluetoothProfile.HEADSET, INPUT_DEVICE, A2DP_SINK};

    private final Context context;
    private final TextView pill;
    private final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    private final SparseArray<BluetoothProfile> proxies = new SparseArray<>();
    private boolean started;

    BluetoothPill(Context context, TextView pill) {
        this.context = context;
        this.pill = pill;
    }

    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            refresh();
        }
    };

    private final BluetoothProfile.ServiceListener listener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (!started) {
                if (adapter != null) adapter.closeProfileProxy(profile, proxy);
                return;
            }
            proxies.put(profile, proxy);
            refresh();
        }

        @Override
        public void onServiceDisconnected(int profile) {
            proxies.remove(profile);
            refresh();
        }
    };

    void start() {
        if (started) return;
        started = true;
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        filter.addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED");
        filter.addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED");
        filter.addAction("android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED");
        filter.addAction("android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED");
        context.registerReceiver(changes, filter);
        if (adapter != null && canRead()) {
            for (int profile : PROFILES) {
                try {
                    adapter.getProfileProxy(context, listener, profile);
                } catch (RuntimeException ignored) {
                    // a profile this firmware does not have
                }
            }
        }
        refresh();
    }

    void stop() {
        if (!started) return;
        started = false;
        try {
            context.unregisterReceiver(changes);
        } catch (IllegalArgumentException ignored) {
        }
        for (int i = 0; i < proxies.size(); i++) {
            try {
                adapter.closeProfileProxy(proxies.keyAt(i), proxies.valueAt(i));
            } catch (RuntimeException ignored) {
            }
        }
        proxies.clear();
    }

    /** Android 12 made reading devices a runtime permission; before that BLUETOOTH, granted at install, covers it. */
    private boolean canRead() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    /** The pill is an icon: the names go to its description, which TalkBack reads. */
    private void refresh() {
        String description;
        if (adapter == null || !adapter.isEnabled()) {
            description = context.getString(R.string.bluetooth_off);
        } else {
            List<String> names = connectedNames();
            description = names.isEmpty() ? context.getString(R.string.bluetooth_none)
                    : context.getString(R.string.bluetooth_connected, String.join(", ", names));
        }
        pill.setContentDescription(description);
    }

    /** Connected devices other than remotes, keyboards and pointers, by name, in the order the profiles report them. */
    private List<String> connectedNames() {
        Map<String, String> byAddress = new LinkedHashMap<>();
        if (!canRead()) return new ArrayList<>();
        for (int i = 0; i < proxies.size(); i++) {
            List<BluetoothDevice> devices;
            try {
                devices = proxies.valueAt(i).getConnectedDevices();
            } catch (RuntimeException e) {
                continue;
            }
            for (BluetoothDevice device : devices) {
                if (isRemoteLike(device)) continue;
                String name = device.getName();
                if (name == null || name.trim().isEmpty()) name = device.getAddress();
                byAddress.put(device.getAddress(), name.trim());
            }
        }
        return new ArrayList<>(byAddress.values());
    }

    /**
     * A remote, keyboard or mouse: the peripheral class, or a Bluetooth LE device with no class at all, which is how
     * the TV remotes announce themselves (Xiaomi RC). Gamepads and joysticks stay, as people play with them.
     */
    static boolean isRemoteLike(int majorClass, int deviceClass, int type) {
        if (majorClass == BluetoothClass.Device.Major.PERIPHERAL) {
            // Minor bits: 0x40 keyboard, 0x80 pointing; below them the subtype, 0x04 joystick and 0x08 gamepad.
            int subtype = deviceClass & 0x3C;
            boolean gamepad = (deviceClass & 0xC0) == 0 && (subtype == 0x04 || subtype == 0x08);
            return !gamepad;
        }
        return type == BluetoothDevice.DEVICE_TYPE_LE && majorClass == BluetoothClass.Device.Major.UNCATEGORIZED;
    }

    private static boolean isRemoteLike(BluetoothDevice device) {
        BluetoothClass cls = device.getBluetoothClass();
        int major = cls == null ? BluetoothClass.Device.Major.UNCATEGORIZED : cls.getMajorDeviceClass();
        int full = cls == null ? 0 : cls.getDeviceClass();
        return isRemoteLike(major, full, device.getType());
    }
}
