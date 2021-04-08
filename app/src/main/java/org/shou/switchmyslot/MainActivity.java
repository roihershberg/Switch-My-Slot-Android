/********************************************************************************************
 org/shou/switchmyslot/MainActivity.java: MainActivity for Switch My Slot Android App

 Copyright (C) 2010 - 2021 Shou

 MIT License

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
 */

package org.shou.switchmyslot;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.stericson.RootShell.exceptions.RootDeniedException;
import com.stericson.RootShell.execution.Command;
import com.stericson.RootShell.execution.Shell;
import com.stericson.RootTools.RootTools;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeoutException;

public class MainActivity extends AppCompatActivity {

    TextView halInfoTV, numberOfSlotsTV, currentSlotTV, currentSlotSuffixTV;
    int currentSlot;
    String convertedSlotNumberToAlphabet = null;
    Button button;
    Shell shell;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Command halinfoCommand, numberOfSlotsCommand, currentSlotCommand, currentSlotSuffixCommand;

        halInfoTV = findViewById(R.id.halInfoTV);
        numberOfSlotsTV = findViewById(R.id.numberOfSlotsTV);
        currentSlotTV = findViewById(R.id.currentSlotTV);
        currentSlotSuffixTV = findViewById(R.id.CurrentSlotSuffixTV);
        button = findViewById(R.id.button);

        if (checkDeviceSupport()) {

            // Creating commands

            halinfoCommand = new Command(0, false, "bootctl hal-info")
            {
                @Override
                public void commandOutput(int id, final String line) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            halInfoTV.setText(line);
                        }
                    });
                    super.commandOutput(id, line);  // MUST be in the end of the method - not in the start
                }
            };

            numberOfSlotsCommand = new Command(1, false, "bootctl get-number-slots")
            {
                @Override
                public void commandOutput(int id, final String line) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            numberOfSlotsTV.setText(getString(R.string.number_of_slots) + " " + line);
                        }
                    });
                    super.commandOutput(id, line);
                }
            };

            currentSlotCommand = new Command(2, false, "bootctl get-current-slot")
            {
                @Override
                public void commandOutput(int id, final String line) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            currentSlot = Integer.parseInt(line);

                            if (currentSlot == 0) {
                                convertedSlotNumberToAlphabet = "A";
                                button.setText(getString(R.string.switch_slot_to) + " B"); //"Switch Slot to B"
                            } else if (currentSlot == 1) {
                                convertedSlotNumberToAlphabet = "B";
                                button.setText(getString(R.string.switch_slot_to) + " A"); //"Switch Slot to A"
                            }
                            currentSlotTV.setText(getString(R.string.current_slot) + " " + convertedSlotNumberToAlphabet);
                        }
                    });
                    super.commandOutput(id, line);
                }
            };

            currentSlotSuffixCommand = new Command(3, false, "bootctl get-suffix " + currentSlot)
            {
                @Override
                public void commandOutput(int id, final String line) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            currentSlotSuffixTV.setText(getString(R.string.current_slot_suffix) + " " + line);
                        }
                    });
                    super.commandOutput(id, line);
                }
            };

            try {
                shell = RootTools.getShell(true);

                // Executing commands
                shell.add(halinfoCommand);
                shell.add(numberOfSlotsCommand);
                shell.add(currentSlotCommand);
                shell.add(currentSlotSuffixCommand);

            } catch (RootDeniedException e) {
                e.printStackTrace();
                displayErrorAndExit(getString(R.string.error_root_denied));
            } catch (IOException | TimeoutException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * Checks if the device is supported. The requirements are:
     *  - Android version 7.1 or newer
     *  - A/B partitions (conventional or virtual)
     *  - SU availability
     *  - SU granted
     *  - Availability of bootctl utility
     *
     * If the device isn't supported then the app shows an error dialog and exits.
     *
     * @return True if the device is supported, else false.
     */
    public boolean checkDeviceSupport() {

        boolean supported = false;
        String unsupportedReason = "";

        if (Integer.parseInt(android.os.Build.VERSION.SDK) < 25) {  // Seamless A/B updates are only from Android 7.1
            unsupportedReason = getString(R.string.error_min_api);
        } else if (ABChecker.check() == null) {  // if the device don't support the conventional or virtual A/B partitions
            unsupportedReason = getString(R.string.error_ab_device);
        } else if (!RootTools.isRootAvailable()) {  // if su binary is not available
            unsupportedReason = getString(R.string.error_root_required);
        } else if (!RootTools.isAccessGiven()) {  // if user denied the su request
            unsupportedReason = getString(R.string.error_root_denied);
        } else if (!RootTools.checkUtil("bootctl")) {  // checking bootctl availability
            unsupportedReason = getString(R.string.error_bootctl_missing);
        } else {
            supported = true;
        }

        if (supported) {
            Log.d("Switch My Slot", "Device supported! This is an A/B device with Android version 7.1 or newer and bootctl utility is available.");
        } else {
            Log.e("Switch My Slot", "Error: Device unsupported. " + unsupportedReason);
            displayErrorAndExit(unsupportedReason);
        }

        return supported;
    }


    /**
     * Opens the github repo.
     *
     * @param view The TextView that got clicked
     */
    public void openRepo(View view) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/gibcheesepuffs/Switch-My-Slot-Android"));
        startActivity(browserIntent);
    }


    /**
     * Shows confirmation dialog and switches the active slot using bootctl utility.
     * Then reboots the device using the power manager. If it fails then executes a force reboot.
     *
     * @param view The button that got clicked.
     */
    public void switchSlot(View view) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(android.R.string.dialog_alert_title);
        builder.setMessage(getString(R.string.dialog_confirmation));

        builder.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                String setActiveSlotCommandText = "";
                Command setActiveSlotCommand, rebootCommand;

                if (currentSlot == 0) {
                    setActiveSlotCommandText = "bootctl set-active-boot-slot 1";
                } else if (currentSlot == 1) {
                    setActiveSlotCommandText = "bootctl set-active-boot-slot 0";
                }

                // Creating commands
                setActiveSlotCommand = new Command(4, false, setActiveSlotCommandText);
                rebootCommand = new Command(5, false, "svc power reboot || reboot");

                try {
                    
                    // Executing commands
                    shell.add(setActiveSlotCommand);
                    shell.add(rebootCommand);
                    shell.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        builder.setNegativeButton(getString(android.R.string.no), null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }


    /**
     * Displays an error dialog that when dismissed, it calls the app to exit.
     *
     * @param error The error message to display.
     */
    public void displayErrorAndExit(String error) {

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle(getString(R.string.dialog_error_title));
        builder.setMessage(error);
        builder.setPositiveButton(getString(android.R.string.ok), null);

        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {  // Closing app on dismiss
            @Override
            public void onDismiss(DialogInterface dialog) {
                exitApp();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();

    }


    /**
     * Exits the app by finishing the activity and then calling the System.exit function.
     * If the root shell is open then closing it.
     *
     */
    public void exitApp() {
        try {
            if (shell != null && !shell.isClosed) shell.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        finish();
        System.exit(0);
    }
}
