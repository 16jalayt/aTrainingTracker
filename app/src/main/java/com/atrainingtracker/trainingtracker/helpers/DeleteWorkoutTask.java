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

package com.atrainingtracker.trainingtracker.helpers;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.util.Log;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.Exporter.ExportManager;
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;


public class DeleteWorkoutTask extends AsyncTask<Long, String, Boolean> {
    public static final String FINISHED_DELETING = "de.rainerblind.trainingtraccker.helpers.DeleteWorkoutTask.FINISHED_DELETING";
    private static final String TAG = "DeleteWorkoutTask";
    private static final boolean DEBUG = false;
    private ProgressDialog progressDialog;
    private Context context;

    public DeleteWorkoutTask(Context context) {
        this.context = context;
        progressDialog = new ProgressDialog(context);
    }

    protected void onPreExecute() {
        progressDialog.setMessage(context.getString(R.string.deleting_please_wait));
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();
    }

    @Override
    protected void onPostExecute(final Boolean success) {
        if (DEBUG) Log.d(TAG, "onPostExecute");
        if (progressDialog.isShowing()) {
            if (DEBUG) Log.d(TAG, "dialog still showing => dismiss");
            try {
                progressDialog.dismiss();
                // sometimes this gives the following exception:
                // java.lang.IllegalArgumentException: View not attached to window manager
                // so we catch this exception
            } catch (IllegalArgumentException e) {
                // and nothing
                // http://stackoverflow.com/questions/2745061/java-lang-illegalargumentexception-view-not-attached-to-window-manager
            }
        } else {
            if (DEBUG) Log.d(TAG, "dialog no longer showing, so do nothing?");
        }
        context.sendBroadcast(new Intent(FINISHED_DELETING));
    }

    @Override
    public void onProgressUpdate(String... args) {
        progressDialog.setMessage(args[0]);
    }

    @Override
    protected Boolean doInBackground(Long... args) {
        SQLiteDatabase dbSummaries = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();
        SQLiteDatabase dbLaps = LapsDatabaseManager.getInstance().getOpenDatabase();

        Cursor cursor;

        for (int i = 0; i < args.length; i++) {

            long workoutId = args[i];

            if (DEBUG) Log.d(TAG, "delete workout " + workoutId);

            cursor = dbSummaries.query(WorkoutSummaries.TABLE, null, WorkoutSummaries.C_ID + "=?", new String[]{workoutId + ""}, null, null, null);
            if (!cursor.moveToFirst()) {
                return false;
            }
            String name = cursor.getString(cursor.getColumnIndex(WorkoutSummaries.WORKOUT_NAME));
            String baseFileName = cursor.getString(cursor.getColumnIndex(WorkoutSummaries.FILE_BASE_NAME));
            cursor.close();

            publishProgress(String.format("deleting %s...\nplease wait", name));

            // delete from WorkoutSummaries
            if (DEBUG) Log.d(TAG, "deleting from WorkoutSummaries");
            dbSummaries.delete(WorkoutSummaries.TABLE, WorkoutSummaries.C_ID + "=?", new String[]{workoutId + ""});
            dbSummaries.delete(WorkoutSummaries.TABLE_ACCUMULATED_SENSORS, WorkoutSummaries.WORKOUT_ID + "=?", new String[]{workoutId + ""});
            dbSummaries.delete(WorkoutSummaries.TABLE_EXTREMUM_VALUES, WorkoutSummaries.WORKOUT_ID + "=?", new String[]{workoutId + ""});

            // delete from WorkoutSamples
            if (DEBUG) Log.d(TAG, "deleting from WorkoutSamples");
            WorkoutSamplesDatabaseManager.getInstance().deleteWorkout(baseFileName);

            // delete from ExportManager
            if (DEBUG) Log.d(TAG, "deleting from ExportManager");
            (new ExportManager(context, TAG)).deleteWorkout(baseFileName);

            // delete from Laps
            if (DEBUG) Log.d(TAG, "deleting from Laps");
            dbLaps.delete(LapsDatabaseManager.Laps.TABLE, LapsDatabaseManager.Laps.WORKOUT_ID + "=?", new String[]{workoutId + ""});

        }

        WorkoutSummariesDatabaseManager.getInstance().closeDatabase();  // dbSummaries.close();
        LapsDatabaseManager.getInstance().closeDatabase(); // instead of dbLaps.close();

        return true;
    }

}
