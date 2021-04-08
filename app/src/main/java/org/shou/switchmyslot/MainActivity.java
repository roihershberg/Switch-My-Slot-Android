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
import com.stericson.RootShell.execution.Command;
import com.stericson.RootShell.execution.Shell;
import com.stericson.RootTools.RootTools;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    TextView halInfoTV, numberOfSlotsTV, currentSlotTV, CurrentSlotSuffixTV;
    int currentSlot;
    String convertedSlotNumberToAlphabet = null;
    Button button;
    Shell shell;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        halInfoTV = findViewById(R.id.halInfoTV);
        numberOfSlotsTV = findViewById(R.id.numberOfSlotsTV);
        currentSlotTV = findViewById(R.id.currentSlotTV);
        CurrentSlotSuffixTV = findViewById(R.id.CurrentSlotSuffixTV);
        button = findViewById(R.id.button);

        try {

            if (checkDeviceSupport()) {

                Process halInfoProcess;
                halInfoProcess = Runtime.getRuntime().exec("su -c bootctl hal-info");
                BufferedReader halinfoOUT = new BufferedReader(new InputStreamReader(halInfoProcess.getInputStream()));
                halInfoTV.setText(halinfoOUT.readLine());

                Process numberOfSlotsProcess;
                numberOfSlotsProcess = Runtime.getRuntime().exec("su -c bootctl get-number-slots");
                BufferedReader numberOfSlotsProcessOUT = new BufferedReader(new InputStreamReader(numberOfSlotsProcess.getInputStream()));
                numberOfSlotsTV.setText(getString(R.string.number_of_slots) + " " + numberOfSlotsProcessOUT.readLine()); //Number of slots:

                Process currentSlotProcess;
                currentSlotProcess = Runtime.getRuntime().exec("su -c bootctl get-current-slot");
                BufferedReader currentSlotProcessOUT = new BufferedReader(new InputStreamReader(currentSlotProcess.getInputStream()));

                currentSlot = Integer.parseInt(currentSlotProcessOUT.readLine());
                if (currentSlot == 0) {
                    convertedSlotNumberToAlphabet = "A";
                    button.setText(getString(R.string.switch_slot_to) + " B"); //"Switch Slot to B"
                }

                if (currentSlot == 1) {
                    convertedSlotNumberToAlphabet = "B";
                    button.setText(getString(R.string.switch_slot_to) + " A"); //"Switch Slot to A"
                }

                currentSlotTV.setText(getString(R.string.current_slot) + " " + convertedSlotNumberToAlphabet); //"Current slot: "

                Process CurrentSlotSuffixProcess;
                CurrentSlotSuffixProcess = Runtime.getRuntime().exec("su -c bootctl get-suffix " + currentSlot);
                BufferedReader CurrentSlotSuffixProcessOUT = new BufferedReader(new InputStreamReader(CurrentSlotSuffixProcess.getInputStream()));
                CurrentSlotSuffixTV.setText(getString(R.string.current_slot_suffix) + " " + CurrentSlotSuffixProcessOUT.readLine()); //"Current slot suffix: "
            }
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
        }
    }


    /**
     * Checks if the device is supported. The requirements are:
     *  - Android version 7.1 or newer
     *  - A/B partitions (conventional or virtual)
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
        } else {
            if (ABChecker.check() != null) {  // if the device supports the conventional or virtual A/B partitions

                // checking for bootctl availability
                try {
                    Process bootctlCheckerProcess;
                    bootctlCheckerProcess = Runtime.getRuntime().exec("su -c if [ -x \"$(command -v bootctl)\" ]; then echo 1; else echo 0; fi");
                    BufferedReader bootctlCheckerProcessOUT = new BufferedReader(new InputStreamReader(bootctlCheckerProcess.getInputStream()));

                    if (bootctlCheckerProcessOUT.readLine().equals("1")) {  // if bootctl is available
                        supported = true;
                    } else {
                        unsupportedReason = getString(R.string.error_bootctl_missing);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            } else {
                unsupportedReason = getString(R.string.error_ab_device);
            }
        }

        if (!supported) {
            Log.e("Switch My Slot", "Error: Device unsupported. " + unsupportedReason);
            displayErrorAndExit(unsupportedReason);
        }
        Log.d("Switch My Slot", "Device supported! This is an A/B device with Android version 7.1 or newer and bootctl utility is available.");

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
                try {
                    if (currentSlot == 0) {
                        Runtime.getRuntime().exec("su -c bootctl set-active-boot-slot 1");
                    }
                    if (currentSlot == 1) {
                        Runtime.getRuntime().exec("su -c bootctl set-active-boot-slot 0");
                    }
                    Runtime.getRuntime().exec("su -c svc power reboot || reboot");
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
     *
     */
    public void exitApp() {
        finish();
        System.exit(0);
    }
}
