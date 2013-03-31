package com.cyanogenmod.samsungservicemode;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

public class SamsungServiceModeActivity extends Activity implements AdapterView.OnItemClickListener {

    private static final String TAG = "SamsungServiceModeActivity";

    public static final String EXTRA_SECRET_CODE = "secret_code";

    private static final int ID_SERVICE_MODE_REFRESH = 1001;
    private static final int ID_SERVICE_MODE_REQUEST = 1008;
    private static final int ID_SERVICE_MODE_END = 1009;

    private static final int DIALOG_INPUT = 0;

    private static final int CHARS_PER_LINE = 34;

    private ListView mListView;
    private EditText mInputText;
    private String[] mDisplay;

    private int mCurrentSvcMode;
    private int mCurrentModeType;

    // Disable back when initialized with certain commands due to crash
    private boolean mAllowBack;
    private boolean mFirstRun = true;
    private String mFirstPageHead;

    private Phone mPhone;
    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
            case ID_SERVICE_MODE_REFRESH:
                Log.v(TAG, "Tick");
                byte[] data = null;
                switch(mCurrentSvcMode) {
                case OemCommands.OEM_SM_ENTER_MODE_MESSAGE:
                    data = OemCommands.getEnterServiceModeData(0, 0, OemCommands.OEM_SM_QUERY);
                    break;
                case OemCommands.OEM_SM_PROCESS_KEY_MESSAGE:
                    data = OemCommands.getPressKeyData('\0', OemCommands.OEM_SM_QUERY);
                    break;
                default:
                    Log.e(TAG, "Unknown mode: " + mCurrentSvcMode);
                    break;
                }

                if (data != null) {
                    sendRequest(data, ID_SERVICE_MODE_REQUEST);
                }
                break;
            case ID_SERVICE_MODE_REQUEST:
                AsyncResult result = (AsyncResult)msg.obj;
                if (result.exception != null) {
                    Log.e(TAG, "", result.exception);
                    return;
                }
                if (result.result == null) {
                    Log.v(TAG, "No need to refresh.");
                    return;
                }
                byte[] aob = (byte[])result.result;

                if (aob.length == 0) {
                    Log.v(TAG, "Length = 0");
                    return;
                }

                int lines = aob.length / CHARS_PER_LINE;

                if (mDisplay == null || mDisplay.length != lines) {
                    Log.v(TAG, "New array = " + lines);
                    mDisplay = new String[lines];
                }

                for (int i = 0; i < lines; i++) {
                    StringBuilder strb = new StringBuilder(CHARS_PER_LINE);
                    for (int j = 2; i < CHARS_PER_LINE; j++) {
                        int pos = i * CHARS_PER_LINE + j;
                        if (pos >= aob.length) {
                            Log.e(TAG, "Unexpected EOF");
                            break;
                        }
                        if (aob[pos] == 0) {
                            break;
                        }
                        strb.append((char)aob[pos]);
                    }
                    mDisplay[i] = strb.toString();
                }

                mListView.setAdapter(new ArrayAdapter<String>(
                        SamsungServiceModeActivity.this, R.layout.list_item, mDisplay));

                if (mFirstRun) {
                    mFirstPageHead = mDisplay[0];
                    mFirstRun = false;
                }

                if (mDisplay[0].contains("End service mode")) {
                    finish();
                } else if (((mDisplay[0].contains("[")) && (mDisplay[0].contains("]")))
                        || ((mDisplay[1].contains("[")) && (mDisplay[1].contains("]")))) {
                    // This is a menu, don't refresh
                } else if ((mDisplay[0].length() != 0) && (mDisplay[1].length() == 0)
                        && (mDisplay[0].charAt(1) > 48) && (mDisplay[0].charAt(1) < 58)) {
                    // Only numerical display, refresh
                    mHandler.sendEmptyMessageDelayed(ID_SERVICE_MODE_REFRESH, 200);
                } else {
                    // Periodical refresh
                    mHandler.sendEmptyMessageDelayed(ID_SERVICE_MODE_REFRESH, 1500);
                }
                break;
            case ID_SERVICE_MODE_END:
                Log.v(TAG, "Service Mode End");
                break;
            }
        }

    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mInputText = new EditText(this);
        mListView = (ListView)findViewById(R.id.displayList);
        mListView.setOnItemClickListener(this);

        mPhone = PhoneFactory.getDefaultPhone();

        // Go to the page specified by the code used to enter service mode
        String code = getIntent().getStringExtra(EXTRA_SECRET_CODE);

        // Default to main page
        int modeType = OemCommands.OEM_SM_TYPE_TEST_MANUAL;
        int subType = OemCommands.OEM_SM_TYPE_SUB_ENTER;
        mAllowBack = true; // Some commands don't like having "back" executed on them

        if (TextUtils.isEmpty(code) || code.equals("197328640")) {
            // Use default (this exists to prevent NPE when code is null)
        }
        else if (code.equals("0011")) {
            modeType = OemCommands.OEM_SM_TYPE_MONITOR;
            subType = OemCommands.OEM_SM_TYPE_SUB_ENTER;
        } else if (code.equals("0228")) { // 0BAT
            subType = OemCommands.OEM_SM_TYPE_SUB_BATTERY_INFO_ENTER;
        } else if (code.equals("32489")) {
            subType = OemCommands.OEM_SM_TYPE_SUB_CIPHERING_PROTECTION_ENTER;
        } else if (code.equals("2580")) { // ALT0
            subType = OemCommands.OEM_SM_TYPE_SUB_INTEGRITY_PROTECTION_ENTER;
        } else if (code.equals("9090") || code.equals("7284")) { // PATH
            subType = OemCommands.OEM_SM_TYPE_SUB_USB_UART_DIAG_CONTROL_ENTER;
            mAllowBack = false;
        } else if (code.equals("0599") || code.equals("301279") || code.equals("279301")) {
            subType = OemCommands.OEM_SM_TYPE_SUB_RRC_VERSION_ENTER;
            mAllowBack = false;
        } else if (code.equals("2263")) { // BAND
            subType = OemCommands.OEM_SM_TYPE_SUB_BAND_SEL_ENTER;
            mAllowBack = false;
        } else if (code.equals("4238378")) { // GCFTEST
            subType = OemCommands.OEM_SM_TYPE_SUB_GCF_TESTMODE_ENTER;
            mAllowBack = false;
        } else if (code.equals("0283")) { // 0AUD
            subType = OemCommands.OEM_SM_TYPE_SUB_GSM_FACTORY_AUDIO_LB_ENTER;
        } else if (code.equals("1575")) {
            subType = OemCommands.OEM_SM_TYPE_SUB_GPSONE_SS_TEST_ENTER;
        } else if (code.equals("73876766")) { // SETSMSON
            subType = OemCommands.OEM_SM_TYPE_SUB_SELLOUT_SMS_ENABLE_ENTER;
            mAllowBack = false;
        } else if (code.equals("738767633")) { // SETSMSOFF
            subType = OemCommands.OEM_SM_TYPE_SUB_SELLOUT_SMS_DISABLE_ENTER;
            mAllowBack = false;
        } else if (code.equals("7387678378")) { // SETSMSTEST
            subType = OemCommands.OEM_SM_TYPE_SUB_SELLOUT_SMS_TEST_MODE_ON;
            mAllowBack = false;
        } else if (code.equals("7387677763")) { // SETSMSPROD
            subType = OemCommands.OEM_SM_TYPE_SUB_SELLOUT_SMS_PRODUCT_MODE_ON;
            mAllowBack = false;
        } else if (code.equals("4387264636")) {
            subType = OemCommands.OEM_SM_TYPE_SUB_GET_SELLOUT_SMS_INFO_ENTER;
            mAllowBack = false;
        } else if (code.equals("6984125*") || code.equals("2886")) { // AUTO
            // crash
            subType = OemCommands.OEM_SM_TYPE_SUB_TST_AUTO_ANSWER_ENTER;
        } else if (code.equals("2767*2878")) {
            // crash
            subType = OemCommands.OEM_SM_TYPE_SUB_TST_NV_RESET_ENTER;
        } else if (code.equals("1111")) {
            subType = OemCommands.OEM_SM_TYPE_SUB_TST_FTA_SW_VERSION_ENTER;
            mAllowBack = false;
        } else if (code.equals("2222")) {
            subType = OemCommands.OEM_SM_TYPE_SUB_TST_FTA_HW_VERSION_ENTER;
            mAllowBack = false;
        }

        enterServiceMode(modeType, subType);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case DIALOG_INPUT:
            return new AlertDialog.Builder(this)
            .setTitle(R.string.input)
            .setView(mInputText)
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    sendString(mInputText.getText().toString());
                    mInputText.setText("");
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        }
        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_input:
            showDialog(DIALOG_INPUT);
            break;
        case R.id.menu_quit:
            endServiceMode();
            break;
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (!mAllowBack && mDisplay[0].equals(mFirstPageHead)) {
            Log.v(TAG, "Back disabled. Ending service mode.");
            endServiceMode();
        } else {
            sendChar((char) 92);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        String str = mDisplay[position];

        if (str.equals("Input ?")) {
            // This one asks for input, show the input dialog for convenience
            showDialog(DIALOG_INPUT);
            return;
        }

        int start = str.indexOf('[');
        int end = str.indexOf(']');

        if (start == -1 || end == -1) {
            // This menu is not clickable
            return;
        }
        sendChar(str.charAt(start + 1));
    }

    @Override
    public void onPause() {
        super.onPause();
        mHandler.removeMessages(ID_SERVICE_MODE_REFRESH);
    }

    private void enterServiceMode(int modeType, int subType) {
        mCurrentSvcMode = OemCommands.OEM_SM_ENTER_MODE_MESSAGE;
        mCurrentModeType = modeType;
        byte[] data = OemCommands.getEnterServiceModeData(modeType, subType, OemCommands.OEM_SM_ACTION);
        sendRequest(data, ID_SERVICE_MODE_REQUEST);
    }

    private void endServiceMode() {
        mCurrentSvcMode = OemCommands.OEM_SM_END_MODE_MESSAGE;
        mHandler.removeMessages(ID_SERVICE_MODE_REFRESH);
        byte[] data = OemCommands.getEndServiceModeData(mCurrentModeType);
        sendRequest(data, ID_SERVICE_MODE_END);
        finish();
    }

    private void sendChar(char chr) {
        mCurrentSvcMode = OemCommands.OEM_SM_PROCESS_KEY_MESSAGE;
        mHandler.removeMessages(ID_SERVICE_MODE_REFRESH);
        if (chr >= 'a' && chr <= 'f') {
            chr = Character.toUpperCase(chr);
        } else if (chr == '-') {
            chr = '*';
        }

        byte[] data = OemCommands.getPressKeyData(chr, OemCommands.OEM_SM_ACTION);
        sendRequest(data, ID_SERVICE_MODE_REQUEST);
    }

    private void sendString(String str) {
        for (char chr : str.toCharArray()) {
            sendChar(chr);
        }
        sendChar((char) 83); // End
    }

    private void sendRequest(byte[] data, int id) {
        Message msg = mHandler.obtainMessage(id);
        mPhone.invokeOemRilRequestRaw(data, msg);
    }

}
