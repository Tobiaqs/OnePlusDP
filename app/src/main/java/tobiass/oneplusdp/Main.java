package tobiass.oneplusdp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.stericson.RootShell.RootShell;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

public class Main extends Activity implements CompoundButton.OnCheckedChangeListener, View.OnClickListener {
    private Switch mTamperSwitch;
    private Switch mUnlockedSwitch;
    private Switch mDisableRecoverySwitch;
    private String mRecoveryImageBackupPath;

    private static final String RECOVERY_IMAGE_PATH = "/dev/block/platform/msm_sdcc.1/by-name/recovery";
    private static final String RECOVERY_BACKUP_FILENAME = "recovery_backup.img";
    private static final String BOOT_IMAGE_PATH = "/dev/block/platform/msm_sdcc.1/by-name/aboot";
    private static final int BLOCK_SIZE = 1024 * 1024; // 1 mebibyte
    private static final int OFFSET_TAMPER_FLAG = 1048084;
    private static final int OFFSET_UNLOCK_FLAG = 1048080;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mRecoveryImageBackupPath = getApplicationInfo().dataDir + "/" + RECOVERY_BACKUP_FILENAME;

        mTamperSwitch = (Switch)findViewById(R.id.switch_tamper);
        mUnlockedSwitch = (Switch)findViewById(R.id.switch_unlocked);
        mDisableRecoverySwitch = (Switch)findViewById(R.id.switch_disable_recovery);

        TextView infoTamper = (TextView)findViewById(R.id.info_tamper);
        TextView infoUnlocked = (TextView)findViewById(R.id.info_unlocked);
        TextView infoRecovery = (TextView)findViewById(R.id.info_recovery);

        infoTamper.setOnClickListener(this);
        infoUnlocked.setOnClickListener(this);
        infoRecovery.setOnClickListener(this);

        if(RootShell.isAccessGiven()) {
            try {
                mTamperSwitch.setChecked(readByteFromCommand("dd ibs=1 count=1 skip=" + OFFSET_TAMPER_FLAG + " if=" + BOOT_IMAGE_PATH) == 1);
                mUnlockedSwitch.setChecked(readByteFromCommand("dd ibs=1 count=1 skip=" + OFFSET_TAMPER_FLAG + " if=" + BOOT_IMAGE_PATH) == 1);
                mDisableRecoverySwitch.setChecked(new File(mRecoveryImageBackupPath).exists());
            } catch (IOException e) {
                showError(R.string.error_reading);
            }
            mTamperSwitch.setOnCheckedChangeListener(this);
            mUnlockedSwitch.setOnCheckedChangeListener(this);
            mDisableRecoverySwitch.setOnCheckedChangeListener(this);
        }
        else {
            showError(R.string.error_no_root);
        }
    }

    void showError(CharSequence error) {
        findViewById(R.id.controls).setVisibility(View.GONE);
        ((TextView)findViewById(R.id.text_error)).setText(error);
    }

    void showError(int errorRes) {
        showError(getText(errorRes));
    }

    @Override
    public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
        new AsyncTask<Void, Void, Boolean>() {
            ProgressDialog mProgressDialog;

            @Override
            protected void onPreExecute() {
                int dialogTitle = R.string.writing;

                if (buttonView == mDisableRecoverySwitch) {
                    if (isChecked) {
                        dialogTitle = R.string.disabling_recovery;
                    } else {
                        dialogTitle = R.string.enabling_recovery;
                    }
                }

                mProgressDialog = ProgressDialog.show(Main.this, getText(dialogTitle), getText(R.string.please_wait));
            }
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    Process p = Runtime.getRuntime().exec("su");
                    DataOutputStream w = new DataOutputStream(p.getOutputStream());
                    if (buttonView == mDisableRecoverySwitch) {
                        if (isChecked) {
                            // Verify backup image does not exist
                            if (new File(mRecoveryImageBackupPath).exists()) {
                                return false;
                            }

                            // backup the recovery partition first
                            w.writeBytes("dd if=" + RECOVERY_IMAGE_PATH + " bs=" + BLOCK_SIZE + " count=16 of=" + mRecoveryImageBackupPath + "\n");
                            w.flush();

                            // Write 16 mebibytes of zeroes to the recovery partition
                            w.writeBytes("dd if=/dev/zero bs=" + BLOCK_SIZE + " count=16 of=" + RECOVERY_IMAGE_PATH + "\n");
                            w.flush();
                        } else {
                            // Verify backup image exists
                            if (!new File(mRecoveryImageBackupPath).exists()) {
                                return false;
                            }
                            // Write backup image to the recovery partition
                            w.writeBytes("dd if=" + mRecoveryImageBackupPath + " bs=" + BLOCK_SIZE + " count=16 of=" + RECOVERY_IMAGE_PATH + "\n");
                            w.flush();

                            // Remove backup image
                            w.writeBytes("rm " + mRecoveryImageBackupPath + "\n");
                            w.flush();
                        }
                        w.writeBytes("exit\n");
                        w.flush();
                    } else {
                        // Check whether dd supports conv options. We have to specify conv=notrunc
                        // if it does or it might truncate the boot image
                        String convOption = "";

                        // Run test command
                        byte ddByte = readByteFromCommand("dd conv=notrunc count=0 2>&1");
                        // First byte will be != 0x64 if DD supports conv options
                        if (ddByte != 0x64) {
                            convOption = "conv=notrunc ";
                        }

                        w.writeBytes("echo -n -e \\\\x" + Integer.toHexString(isChecked ? 1 : 0) + " | dd obs=1 count=1 " + convOption + "seek=" + (buttonView == mTamperSwitch ? OFFSET_TAMPER_FLAG : OFFSET_UNLOCK_FLAG) + " of=" + BOOT_IMAGE_PATH + "\n");
                        w.flush();
                        w.writeBytes("exit\n");
                        w.flush();
                    }
                    try {
                        p.waitFor();
                    } catch (InterruptedException e) {}
                } catch (IOException e) {
                    return false;
                }
                return true;
            }

            @Override
            protected void onPostExecute(Boolean result) {
                mProgressDialog.dismiss();
                if(!result) {
                    showError(R.string.error_writing);
                }
            }
        }.execute();
    }

    public byte readByteFromCommand(String theCommand) throws IOException {
        Process p = Runtime.getRuntime().exec("su");
        DataOutputStream w = new DataOutputStream(p.getOutputStream());
        DataInputStream r = new DataInputStream(p.getInputStream());

        w.writeBytes(theCommand + "\n");
        w.flush();
        byte resultByte = r.readByte();
        w.writeBytes("exit\n");
        w.flush();
        try {
            p.waitFor();
        } catch (InterruptedException e) {}
        return resultByte;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.info_tamper:
                new AlertDialog.Builder(this)
                        .setTitle(R.string.tamper_flag)
                        .setMessage(R.string.tamper_help)
                        .show();
                break;
            case R.id.info_unlocked:
                new AlertDialog.Builder(this)
                        .setTitle(R.string.unlocked_flag)
                        .setMessage(R.string.unlocked_help)
                        .show();
                break;
            case R.id.info_recovery:
                new AlertDialog.Builder(this)
                        .setTitle(R.string.disable_recovery)
                        .setMessage(R.string.disable_recovery_help)
                        .show();
                break;
        }
    }
}
