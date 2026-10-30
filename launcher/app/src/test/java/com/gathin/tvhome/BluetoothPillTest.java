package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BluetoothPillTest {
    // BluetoothClass.Device.Major and BluetoothDevice.DEVICE_TYPE_* values.
    private static final int UNCATEGORIZED = 0x1F00, AUDIO_VIDEO = 0x0400, PHONE = 0x0200, PERIPHERAL = 0x0500;
    private static final int CLASSIC = 1, LE = 2, DUAL = 3;

    @Test public void theTvRemoteIsLeftOut() {
        // Xiaomi RC: Bluetooth LE with no class of device.
        assertTrue(BluetoothPill.isRemoteLike(UNCATEGORIZED, 0, LE));
        // A classic remote announces itself as a peripheral, subtype remote control (0x0C).
        assertTrue(BluetoothPill.isRemoteLike(PERIPHERAL, 0x050C, CLASSIC));
    }

    @Test public void keyboardsAndMiceAreLeftOut() {
        assertTrue(BluetoothPill.isRemoteLike(PERIPHERAL, 0x0540, CLASSIC));
        assertTrue(BluetoothPill.isRemoteLike(PERIPHERAL, 0x0580, DUAL));
    }

    @Test public void headphonesSpeakersPhonesAndGamepadsAreShown() {
        assertFalse(BluetoothPill.isRemoteLike(AUDIO_VIDEO, 0x0418, CLASSIC));   // headphones
        assertFalse(BluetoothPill.isRemoteLike(AUDIO_VIDEO, 0x0414, DUAL));      // loudspeaker
        assertFalse(BluetoothPill.isRemoteLike(PHONE, 0x020C, DUAL));            // smartphone
        assertFalse(BluetoothPill.isRemoteLike(PERIPHERAL, 0x0508, CLASSIC));    // gamepad
        assertFalse(BluetoothPill.isRemoteLike(PERIPHERAL, 0x0504, LE));         // joystick
    }

    @Test public void aClassicDeviceWithNoClassIsShown() {
        // Only LE remotes skip the class; an old classic device with none is still named.
        assertFalse(BluetoothPill.isRemoteLike(UNCATEGORIZED, 0, CLASSIC));
    }
}
