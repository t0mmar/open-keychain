/*
 * Copyright (C) 2015 Dominik Schürmann <dominik@dominikschuermann.de>
 * Copyright (C) 2015 Vincent Breitmoser <v.breitmoser@mugenguild.com>
 * Copyright (C) 2013-2014 Signe Rüsch
 * Copyright (C) 2013-2014 Philipp Jakubeit
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain.ui.base;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.os.AsyncTask;
import android.os.Bundle;

import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.pgp.exception.PgpKeyNotFoundException;
import org.sufficientlysecure.keychain.provider.CachedPublicKeyRing;
import org.sufficientlysecure.keychain.provider.KeychainContract.KeyRings;
import org.sufficientlysecure.keychain.provider.ProviderHelper;
import org.sufficientlysecure.keychain.service.PassphraseCacheService;
import org.sufficientlysecure.keychain.service.input.CryptoInputParcel;
import org.sufficientlysecure.keychain.service.input.RequiredInputParcel;
import org.sufficientlysecure.keychain.smartcard.CardException;
import org.sufficientlysecure.keychain.smartcard.NfcTransport;
import org.sufficientlysecure.keychain.smartcard.SmartcardDevice;
import org.sufficientlysecure.keychain.smartcard.Transport;
import org.sufficientlysecure.keychain.util.UsbConnectionDispatcher;
import org.sufficientlysecure.keychain.smartcard.UsbTransport;
import org.sufficientlysecure.keychain.ui.CreateKeyActivity;
import org.sufficientlysecure.keychain.ui.PassphraseDialogActivity;
import org.sufficientlysecure.keychain.ui.ViewKeyActivity;
import org.sufficientlysecure.keychain.ui.dialog.FidesmoInstallDialog;
import org.sufficientlysecure.keychain.ui.dialog.FidesmoPgpInstallDialog;
import org.sufficientlysecure.keychain.ui.util.KeyFormattingUtils;
import org.sufficientlysecure.keychain.ui.util.Notify;
import org.sufficientlysecure.keychain.ui.util.Notify.Style;
import org.sufficientlysecure.keychain.util.Log;
import org.sufficientlysecure.keychain.util.Passphrase;

import java.io.IOException;

import nordpol.android.OnDiscoveredTagListener;
import nordpol.android.TagDispatcher;

public abstract class BaseSecurityTokenNfcActivity extends BaseActivity
        implements OnDiscoveredTagListener, UsbConnectionDispatcher.OnDiscoveredUsbDeviceListener {
    public static final int REQUEST_CODE_PIN = 1;

    public static final String EXTRA_TAG_HANDLING_ENABLED = "tag_handling_enabled";

    private static final String FIDESMO_APP_PACKAGE = "com.fidesmo.sec.android";

    protected SmartcardDevice mSmartcardDevice = SmartcardDevice.getInstance();
    protected TagDispatcher mTagDispatcher;
    protected UsbConnectionDispatcher mUsbDispatcher;
    private boolean mTagHandlingEnabled;

    private byte[] mSmartcardFingerprints;
    private String mSmartcardUserId;
    private byte[] mSmartcardAid;

    /**
     * Override to change UI before NFC handling (UI thread)
     */
    protected void onSmartcardPreExecute() {
    }

    /**
     * Override to implement NFC operations (background thread)
     */
    protected void doSmartcardInBackground() throws IOException {
        mSmartcardFingerprints = mSmartcardDevice.getFingerprints();
        mSmartcardUserId = mSmartcardDevice.getUserId();
        mSmartcardAid = mSmartcardDevice.getAid();
    }

    /**
     * Override to handle result of NFC operations (UI thread)
     */
    protected void onSmartcardPostExecute() {

        final long subKeyId = KeyFormattingUtils.getKeyIdFromFingerprint(mSmartcardFingerprints);

        try {
            CachedPublicKeyRing ring = new ProviderHelper(this).getCachedPublicKeyRing(
                    KeyRings.buildUnifiedKeyRingsFindBySubkeyUri(subKeyId));
            long masterKeyId = ring.getMasterKeyId();

            Intent intent = new Intent(this, ViewKeyActivity.class);
            intent.setData(KeyRings.buildGenericKeyRingUri(masterKeyId));
            intent.putExtra(ViewKeyActivity.EXTRA_SECURITY_TOKEN_AID, mSmartcardAid);
            intent.putExtra(ViewKeyActivity.EXTRA_SECURITY_TOKEN_USER_ID, mSmartcardUserId);
            intent.putExtra(ViewKeyActivity.EXTRA_SECURITY_TOKEN_FINGERPRINTS, mSmartcardFingerprints);
            startActivity(intent);
        } catch (PgpKeyNotFoundException e) {
            Intent intent = new Intent(this, CreateKeyActivity.class);
            intent.putExtra(CreateKeyActivity.EXTRA_NFC_AID, mSmartcardAid);
            intent.putExtra(CreateKeyActivity.EXTRA_NFC_USER_ID, mSmartcardUserId);
            intent.putExtra(CreateKeyActivity.EXTRA_NFC_FINGERPRINTS, mSmartcardFingerprints);
            startActivity(intent);
        }
    }

    /**
     * Override to use something different than Notify (UI thread)
     */
    protected void onSmartcardError(String error) {
        Notify.create(this, error, Style.WARN).show();
    }

    /**
     * Override to do something when PIN is wrong, e.g., clear passphrases (UI thread)
     */
    protected void onSmartcardPinError(String error) {
        onSmartcardError(error);
    }

    public void tagDiscovered(final Tag tag) {
        // Actual NFC operations are executed in doInBackground to not block the UI thread
        if (!mTagHandlingEnabled)
            return;

        smartcardDiscovered(new NfcTransport(tag));
    }

    public void usbDeviceDiscovered(final UsbDevice device) {
        // Actual USB operations are executed in doInBackground to not block the UI thread
        if (!mTagHandlingEnabled)
            return;

        UsbManager usbManager = (UsbManager) getSystemService(USB_SERVICE);
        smartcardDiscovered(new UsbTransport(device, usbManager));
    }

    public void smartcardDiscovered(final Transport transport) {
        // Actual Smartcard operations are executed in doInBackground to not block the UI thread
        if (!mTagHandlingEnabled)
            return;
        new AsyncTask<Void, Void, IOException>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                onSmartcardPreExecute();
            }

            @Override
            protected IOException doInBackground(Void... params) {
                try {
                    handleSmartcard(transport);
                } catch (IOException e) {
                    return e;
                }

                return null;
            }

            @Override
            protected void onPostExecute(IOException exception) {
                super.onPostExecute(exception);

                if (exception != null) {
                    handleSmartcardError(exception);
                    return;
                }

                onSmartcardPostExecute();
            }
        }.execute();
    }

    protected void pauseTagHandling() {
        mTagHandlingEnabled = false;
    }

    protected void resumeTagHandling() {
        mTagHandlingEnabled = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mTagDispatcher = TagDispatcher.get(this, this, false, false, true, false);
        mUsbDispatcher = new UsbConnectionDispatcher(this, this);

        // Check whether we're recreating a previously destroyed instance
        if (savedInstanceState != null) {
            // Restore value of members from saved state
            mTagHandlingEnabled = savedInstanceState.getBoolean(EXTRA_TAG_HANDLING_ENABLED);
        } else {
            mTagHandlingEnabled = true;
        }

        Intent intent = getIntent();
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {
            throw new AssertionError("should not happen: NfcOperationActivity.onCreate is called instead of onNewIntent!");
        }

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(EXTRA_TAG_HANDLING_ENABLED, mTagHandlingEnabled);
    }

    /**
     * This activity is started as a singleTop activity.
     * All new NFC Intents which are delivered to this activity are handled here
     */
    @Override
    public void onNewIntent(final Intent intent) {
        mTagDispatcher.interceptIntent(intent);
    }

    private void handleSmartcardError(IOException e) {

        if (e instanceof TagLostException) {
            onSmartcardError(getString(R.string.security_token_error_tag_lost));
            return;
        }

        if (e instanceof IsoDepNotSupportedException) {
            onSmartcardError(getString(R.string.security_token_error_iso_dep_not_supported));
            return;
        }

        short status;
        if (e instanceof CardException) {
            status = ((CardException) e).getResponseCode();
        } else {
            status = -1;
        }

        // Wrong PIN, a status of 63CX indicates X attempts remaining.
        if ((status & (short) 0xFFF0) == 0x63C0) {
            int tries = status & 0x000F;
            // hook to do something different when PIN is wrong
            onSmartcardPinError(getResources().getQuantityString(R.plurals.security_token_error_pin, tries, tries));
            return;
        }

        // Otherwise, all status codes are fixed values.
        switch (status) {
            // These errors should not occur in everyday use; if they are returned, it means we
            // made a mistake sending data to the token, or the token is misbehaving.
            case 0x6A80: {
                onSmartcardError(getString(R.string.security_token_error_bad_data));
                break;
            }
            case 0x6883: {
                onSmartcardError(getString(R.string.security_token_error_chaining_error));
                break;
            }
            case 0x6B00: {
                onSmartcardError(getString(R.string.security_token_error_header, "P1/P2"));
                break;
            }
            case 0x6D00: {
                onSmartcardError(getString(R.string.security_token_error_header, "INS"));
                break;
            }
            case 0x6E00: {
                onSmartcardError(getString(R.string.security_token_error_header, "CLA"));
                break;
            }
            // These error conditions are more likely to be experienced by an end user.
            case 0x6285: {
                onSmartcardError(getString(R.string.security_token_error_terminated));
                break;
            }
            case 0x6700: {
                onSmartcardPinError(getString(R.string.security_token_error_wrong_length));
                break;
            }
            case 0x6982: {
                onSmartcardError(getString(R.string.security_token_error_security_not_satisfied));
                break;
            }
            case 0x6983: {
                onSmartcardError(getString(R.string.security_token_error_authentication_blocked));
                break;
            }
            case 0x6985: {
                onSmartcardError(getString(R.string.security_token_error_conditions_not_satisfied));
                break;
            }
            // 6A88 is "Not Found" in the spec, but Yubikey also returns 6A83 for this in some cases.
            case 0x6A88:
            case 0x6A83: {
                onSmartcardError(getString(R.string.security_token_error_data_not_found));
                break;
            }
            // 6F00 is a JavaCard proprietary status code, SW_UNKNOWN, and usually represents an
            // unhandled exception on the security token.
            case 0x6F00: {
                onSmartcardError(getString(R.string.security_token_error_unknown));
                break;
            }
            // 6A82 app not installed on security token!
            case 0x6A82: {
                if (mSmartcardDevice.isFidesmoToken()) {
                    // Check if the Fidesmo app is installed
                    if (isAndroidAppInstalled(FIDESMO_APP_PACKAGE)) {
                        promptFidesmoPgpInstall();
                    } else {
                        promptFidesmoAppInstall();
                    }
                } else { // Other (possibly) compatible hardware
                    onSmartcardError(getString(R.string.security_token_error_pgp_app_not_installed));
                }
                break;
            }
            default: {
                onSmartcardError(getString(R.string.security_token_error, e.getMessage()));
                break;
            }
        }

    }

    /**
     * Called when the system is about to start resuming a previous activity,
     * disables NFC Foreground Dispatch
     */
    public void onPause() {
        super.onPause();
        Log.d(Constants.TAG, "BaseNfcActivity.onPause");

        mTagDispatcher.disableExclusiveNfc();
    }

    /**
     * Called when the activity will start interacting with the user,
     * enables NFC Foreground Dispatch
     */
    public void onResume() {
        super.onResume();
        Log.d(Constants.TAG, "BaseNfcActivity.onResume");
        mTagDispatcher.enableExclusiveNfc();
    }

    protected void obtainSecurityTokenPin(RequiredInputParcel requiredInput) {

        try {
            Passphrase passphrase = PassphraseCacheService.getCachedPassphrase(this,
                    requiredInput.getMasterKeyId(), requiredInput.getSubKeyId());
            if (passphrase != null) {
                mSmartcardDevice.setPin(passphrase);
                return;
            }

            Intent intent = new Intent(this, PassphraseDialogActivity.class);
            intent.putExtra(PassphraseDialogActivity.EXTRA_REQUIRED_INPUT,
                    RequiredInputParcel.createRequiredPassphrase(requiredInput));
            startActivityForResult(intent, REQUEST_CODE_PIN);
        } catch (PassphraseCacheService.KeyNotFoundException e) {
            throw new AssertionError(
                    "tried to find passphrase for non-existing key. this is a programming error!");
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_PIN: {
                if (resultCode != Activity.RESULT_OK) {
                    setResult(resultCode);
                    finish();
                    return;
                }
                CryptoInputParcel input = data.getParcelableExtra(PassphraseDialogActivity.RESULT_CRYPTO_INPUT);
                mSmartcardDevice.setPin(input.getPassphrase());
                break;
            }
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    protected void handleSmartcard(Transport transport) throws IOException {
        // Don't reconnect if device was already connected
        if (!(mSmartcardDevice.isConnected()
                && mSmartcardDevice.getTransport().equals(transport))) {
            mSmartcardDevice.setTransport(transport);
            mSmartcardDevice.connectToDevice();
        }
        doSmartcardInBackground();
    }

    public boolean isSmartcardConnected() {
        return mSmartcardDevice.isConnected();
    }

    public static class IsoDepNotSupportedException extends IOException {

        public IsoDepNotSupportedException(String detailMessage) {
            super(detailMessage);
        }

    }

    /**
     * Ask user if she wants to install PGP onto her Fidesmo token
     */
    private void promptFidesmoPgpInstall() {
        FidesmoPgpInstallDialog fidesmoPgpInstallDialog = new FidesmoPgpInstallDialog();
        fidesmoPgpInstallDialog.show(getSupportFragmentManager(), "fidesmoPgpInstallDialog");
    }

    /**
     * Show a Dialog to the user informing that Fidesmo App must be installed and with option
     * to launch the Google Play store.
     */
    private void promptFidesmoAppInstall() {
        FidesmoInstallDialog fidesmoInstallDialog = new FidesmoInstallDialog();
        fidesmoInstallDialog.show(getSupportFragmentManager(), "fidesmoInstallDialog");
    }

    /**
     * Use the package manager to detect if an application is installed on the phone
     *
     * @param uri an URI identifying the application's package
     * @return 'true' if the app is installed
     */
    private boolean isAndroidAppInstalled(String uri) {
        PackageManager mPackageManager = getPackageManager();
        boolean mAppInstalled;
        try {
            mPackageManager.getPackageInfo(uri, PackageManager.GET_ACTIVITIES);
            mAppInstalled = true;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(Constants.TAG, "App not installed on Android device");
            mAppInstalled = false;
        }
        return mAppInstalled;
    }

    @Override
    protected void onStop() {
        super.onStop();
        mUsbDispatcher.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mUsbDispatcher.onStart();
    }

    public SmartcardDevice getSmartcardDevice() {
        return mSmartcardDevice;
    }

    /**
     * Run smartcard routines if last used token is connected and supports
     * persistent connections
     */
    protected void checkDeviceConnection() {
        if (mSmartcardDevice.isConnected() && mSmartcardDevice.isPersistentConnectionAllowed()) {
            this.smartcardDiscovered(mSmartcardDevice.getTransport());
        }
    }
}
