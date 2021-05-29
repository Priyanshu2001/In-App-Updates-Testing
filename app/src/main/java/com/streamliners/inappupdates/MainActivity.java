package com.streamliners.inappupdates;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import com.google.android.play.core.review.model.ReviewErrorCode;
import com.google.android.play.core.tasks.Task;
import com.streamliners.inappupdates.databinding.ActivityMainBinding;

import java.util.concurrent.CountedCompleter;

import static com.google.android.play.core.install.model.AppUpdateType.IMMEDIATE;

public class MainActivity extends AppCompatActivity {

    private static final int MY_UPDATE_REQUEST_CODE = 1;
    private AppUpdateManager appUpdateManager;
    private ActivityMainBinding activityMainBinding;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityMainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(activityMainBinding.getRoot());
        appUpdateManager = AppUpdateManagerFactory.create(MainActivity.this);
        activityMainBinding.mainTxt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, MainActivity2.class);
                startActivity(intent);
            }
        });
        activityMainBinding.floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Do you Like Our App ?")
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ReviewManager manager = ReviewManagerFactory.create(MainActivity.this);
                                Task<ReviewInfo> request = manager.requestReviewFlow();
                                request.addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        // We can get the ReviewInfo object
                                        ReviewInfo reviewInfo = task.getResult();
                                        Task<Void> flow = manager.launchReviewFlow(MainActivity.this, reviewInfo);
                                        flow.addOnCompleteListener(task1 -> {
                                            // The flow has finished. The API does not indicate whether the user
                                            // reviewed or not, or even whether the review dialog was shown. Thus, no
                                            // matter the result, we continue our app flow.
                                        });
                                    } else {
                                        // There was some problem, log or handle the error code.
                                     //   @ReviewErrorCode int reviewErrorCode = task.getException()).getErrorCode();
                                    }
                                });

                            }
                        })
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .show();
            }
        });

        checkForUpdates();
    }


    private void checkForUpdates() {

// Returns an intent object that you use to check for an update.
        com.google.android.play.core.tasks.Task<AppUpdateInfo> appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();

// Checks that the platform will allow the specified type of update.
        appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                    && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                // Request the update.
               activityMainBinding.update.setVisibility(View.VISIBLE);
                InstallStateUpdatedListener listener = state -> {
                    // (Optional) Provide a download progress bar.
                    if (state.installStatus() == InstallStatus.DOWNLOADING) {
                        activityMainBinding.textView.setText("Downloading Your Update ...");

                        long bytesDownloaded = state.bytesDownloaded();
                        long totalBytesToDownload = state.totalBytesToDownload();
                        int progress = (int) (bytesDownloaded * 1f/totalBytesToDownload) * 100;
                        activityMainBinding.progressBar.setProgress(progress);
                        activityMainBinding.progressBar.setMax(100);
                    }
                    // Log state or install the update.
                    installUpdate();
                };

// Before starting an update, register a listener for updates.
                appUpdateManager.registerListener(listener);

             activityMainBinding.button.setOnClickListener(new View.OnClickListener() {
                 @Override
                 public void onClick(View v) {
                     try {
                         appUpdateManager.startUpdateFlowForResult(appUpdateInfo, AppUpdateType.FLEXIBLE, MainActivity.this, MY_UPDATE_REQUEST_CODE);

                     } catch (IntentSender.SendIntentException e) {
                         e.printStackTrace();
                     }
                 }
             });

                if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                    appUpdateManager.unregisterListener(listener);
                }

            }

            else if(appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                    && appUpdateInfo.isUpdateTypeAllowed(IMMEDIATE)){
                activityMainBinding.update.setVisibility(View.VISIBLE);
                try {
                    appUpdateManager.startUpdateFlowForResult(
                            // Pass the intent that is returned by 'getAppUpdateInfo()'.
                            appUpdateInfo,
                            // Or 'AppUpdateType.FLEXIBLE' for flexible updates.
                            IMMEDIATE,
                            // The current activity making the update request.
                            this,
                            // Include a request code to later monitor this update request.
                            MY_UPDATE_REQUEST_CODE);
                } catch (IntentSender.SendIntentException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void installUpdate() {
        appUpdateManager
                .getAppUpdateInfo()
                .addOnSuccessListener(appUpdateInfo -> {
                    // If the update is downloaded but not installed,
                    // notify the user to complete the update.
                    if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                        popupSnackbarForCompleteUpdate();
                    }
                    if (appUpdateInfo.updateAvailability()
                            == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                        // If an in-app update is already running, resume the update.
                        try {
                            appUpdateManager.startUpdateFlowForResult(
                                    appUpdateInfo,
                                    IMMEDIATE,
                                    this,
                                    MY_UPDATE_REQUEST_CODE);
                        } catch (IntentSender.SendIntentException e) {
                            e.printStackTrace();
                        }
                    }
                });
    }

    private void popupSnackbarForCompleteUpdate() {
        activityMainBinding.textView.setText("Installing Your Update ...");
        activityMainBinding.button2.setVisibility(View.VISIBLE);

        activityMainBinding.button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appUpdateManager.completeUpdate();
                activityMainBinding.update.setVisibility(View.GONE);
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        installUpdate();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MY_UPDATE_REQUEST_CODE && requestCode != RESULT_OK) {
             activityMainBinding.textView.setText("Update Failed !");
             activityMainBinding.button.setText("Retry");
        }
    }
}