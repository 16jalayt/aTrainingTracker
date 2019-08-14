/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (C) 2011 - 2019 Rainer Blind <rainer.blind@gmail.com>
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
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.Exporter;

import android.Manifest;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.FileProvider;
import android.util.Log;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.Activities.MainActivityWithNavigation;
import com.atrainingtracker.trainingtracker.MyHelper;
import com.atrainingtracker.trainingtracker.TrainingApplication;

import java.io.File;
import java.util.ArrayList;

/**
 *
 */
public class ExportWorkoutIntentService extends IntentService {
    private static final String TAG = "ExportWorkoutIntentService";
    private static final boolean DEBUG = TrainingApplication.DEBUG & true;


    boolean exported = false;

    public ExportWorkoutIntentService() {
        super(TAG);
    }


    @Override
    protected void onHandleIntent(Intent intent) {
        if (DEBUG) Log.d(TAG, "onHandleIntent");

        ExportManager exportManager = new ExportManager(this, TAG);

        ArrayList<Uri> emailUris = new ArrayList<>();

        boolean tryExporting = true;  // TODO: what are we exactly doing with this variable?? 
        while (tryExporting) {
            tryExporting = false;
            for (ExportInfo exportInfo : exportManager.getExportQueue()) {

                if (DEBUG) Log.d(TAG, "ExportType: " + exportInfo.getExportType().toString()
                        + ", FileFormat: " + exportInfo.getFileFormat());

                if ((exportInfo.getExportType() == ExportType.FILE | exportInfo.getExportType() == ExportType.COMMUNITY)
                        && !TrainingApplication.havePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    continue;
                }

                BaseExporter exporter = null;
                switch (exportInfo.getExportType()) {
                    case FILE:
                        tryExporting = true;
                        switch (exportInfo.getFileFormat()) {
                            // TODO: change Uri.fromFile to the stuff explained at https://stackoverflow.com/questions/38200282/android-os-fileuriexposedexception-file-storage-emulated-0-test-txt-exposed
                            case CSV:
                                exporter = new CSVFileExporter(this);
                                if (TrainingApplication.sendCSVEmail()) {
                                    emailUris.add(FileProvider.getUriForFile(this,
                                            this.getApplicationContext().getPackageName() + ".com.atrainingtracker.file.provider",
                                            new File(BaseExporter.getDir(FileFormat.CSV.getDirName()), exportInfo.getFileBaseName() + FileFormat.CSV.getFileEnding())));
                                }
                                break;
                            case GC:
                                exporter = new GCFileExporter(this);
                                if (TrainingApplication.sendGCEmail()) {
                                    emailUris.add(FileProvider.getUriForFile(this,
                                            this.getApplicationContext().getPackageName() + ".com.atrainingtracker.file.provider",
                                            new File(BaseExporter.getDir(FileFormat.GC.getDirName()), exportInfo.getFileBaseName() + FileFormat.GC.getFileEnding())));
                                }
                                break;
                            case TCX:
                                exporter = new TCXFileExporter(this);
                                if (TrainingApplication.sendTCXEmail()) {
                                    emailUris.add(FileProvider.getUriForFile(this,
                                            this.getApplicationContext().getPackageName() + ".com.atrainingtracker.file.provider",
                                            new File(BaseExporter.getDir(FileFormat.TCX.getDirName()), exportInfo.getFileBaseName() + FileFormat.TCX.getFileEnding())));
                                }
                                break;
                            case GPX:
                                exporter = new GPXFileExporter(this);
                                if (TrainingApplication.sendGPXEmail()) {
                                    emailUris.add(FileProvider.getUriForFile(this,
                                            this.getApplicationContext().getPackageName() + ".com.atrainingtracker.file.provider",
                                            new File(BaseExporter.getDir(FileFormat.GPX.getDirName()), exportInfo.getFileBaseName() + FileFormat.GPX.getFileEnding())));
                                }
                                break;
                            case STRAVA:
                                exporter = new TCXFileExporter(this);
                                break;
                            case RUNKEEPER:
                                exporter = new RunkeeperFileExporter(this);
                                break;
                            case TRAINING_PEAKS:
                                exporter = new TCXFileExporter(this);
                                // exporter = new TrainingPeaksFileExporter(this);
                                break;
                        }
                        break;

                    case DROPBOX:
                        if (MyHelper.isOnline()) {
                            exporter = new DropboxUploader(this);
                        }
                        break;

                    case COMMUNITY:
                        if (MyHelper.isOnline()) {
                            switch (exportInfo.getFileFormat()) {
                                case STRAVA:
                                    exporter = new StravaUploader(this);
                                    break;
                                case RUNKEEPER:
                                    exporter = new RunkeeperUploader(this);
                                    break;
                                case TRAINING_PEAKS:
                                    exporter = new TrainingPeaksUploader(this);
                                    break;
                                default:
                                    // exporter remains null
                                    break;
                            }
                        }
                        break;
                }
                if (exporter != null) {
                    exported = true;
                    this.startForeground(TrainingApplication.EXPORT_PROGRESS_NOTIFICATION_ID, exporter.getExportProgressNotification(exportInfo));
                    exporter.export(exportInfo);
                    exporter.onFinished();
                }
            }
        }
        exportManager.onFinished(TAG);

        if (exported) {

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancelAll();

            // show notification with a summary of the result
            Bundle bundle = new Bundle();
            bundle.putString(MainActivityWithNavigation.SELECTED_FRAGMENT, MainActivityWithNavigation.SelectedFragment.WORKOUT_LIST.name());
            Intent notificationIntent = new Intent(this, MainActivityWithNavigation.class);
            notificationIntent.putExtras(bundle);
            // notificationIntent.setAction("barAction");
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
            // PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

            Notification.Builder notificationBuilder = new Notification.Builder(this)
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_save_black_48dp))
                    .setSmallIcon(R.drawable.logo)
                    .setContentTitle(getString(R.string.TrainingTracker))
                    .setContentText(getString(R.string.notification_exporting_finished))
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true);

            notificationManager.notify(TrainingApplication.EXPORT_RESULT_NOTIFICATION_ID, notificationBuilder.build());


            // send email stuff
            if (TrainingApplication.sendEmail()) {
                Intent emailIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                emailIntent.setType("text/plain");
                emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{TrainingApplication.getSpEmailAddress()});
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, TrainingApplication.getSpEmailSubject());
                emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, emailUris);
                emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                // emailIntent.setAction(Long.toString(System.currentTimeMillis()));

                PendingIntent pendingShareIntent = PendingIntent.getActivity(this, 0, emailIntent, PendingIntent.FLAG_ONE_SHOT);

                Notification.Builder notificationBuilder2 = new Notification.Builder(this)
                        .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_mail_outline_black_48dp))
                        .setSmallIcon(R.drawable.logo)
                        .setContentTitle(getString(R.string.TrainingTracker))
                        .setContentText(getString(R.string.ready_to_send_email))
                        .setContentIntent(pendingShareIntent)
                        .setAutoCancel(true);

                notificationManager.notify(TrainingApplication.SEND_EMAIL_NOTIFICATION_ID, notificationBuilder2.build());
            }
        }
    }
}
