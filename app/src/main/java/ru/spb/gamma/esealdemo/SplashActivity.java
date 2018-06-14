package ru.spb.gamma.esealdemo;

import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Handler;
import android.os.Parcelable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class SplashActivity extends AppCompatActivity {

    private static final int DELAY = 1000;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Jump to SensorsActivity after DELAY milliseconds
        new Handler().postDelayed(() -> {
            final Intent newIntent = new Intent(SplashActivity.this, DeviceActivity.class);
            newIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

            // Handle NFC message, if app was opened using NFC AAR record
            startActivity(newIntent);
            finish();
        }, DELAY);
    }

    @Override
    public void onBackPressed() {
        // do nothing. Protect from exiting the application when splash screen is shown
    }

    /**
     * Inverts endianness of the byte array.
     * @param bytes input byte array
     * @return byte array in opposite order
     */
    /*
    private byte[] invertEndianness(final byte[] bytes) {
        if (bytes == null)
            return null;
        final int length = bytes.length;
        final byte[] result = new byte[length];
        for (int i = 0; i < length; i++)
            result[i] = bytes[length - i - 1];
        return result;
    }
*/
}
