package com.radiantridge.restoradiantridge.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;
import com.google.gson.JsonObject;
import com.radiantridge.restoradiantridge.R;
import com.radiantridge.restoradiantridge.objects.Restaurant;

/**
 * SyncBackEndHelper class is responsible to sync all locally saved restos
 * to the back-end database.
 *
 * @author Alena Shulzhenko
 * @version 2016-12-07
 */
public class SyncBackEndHelper {
    public static final String TAG = "SyncBackEndHelper";
    private DatabaseHelper dbh;
    private Context context;
    private String herokuCreateRestoUrl;

    /**
     * Instantiates SyncBackEndHelper object.
     * @param context the Context object.
     */
    public SyncBackEndHelper(Context context) {
        this.context = context;
        this.dbh = DatabaseHelper.getDatabaseConnector(context);
    }

    /**
     * Starts the sync to the back-end database.
     */
    public void start() {
        //check to see if we can get on the network
        ConnectivityManager connMgr = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            herokuCreateRestoUrl = "https://radiant-ridge-88291.herokuapp.com/api/api/create";
            Restaurant[] restos = dbh.getAllRestos();
            if(restos.length != 0)
                new SyncHerokuTask().execute(restos);
            else
                Toast.makeText(context, R.string.sync_nodata, Toast.LENGTH_SHORT).show();
        }
        //no connection
        else {
            Log.d(TAG, "No connection available.");
            Toast.makeText(context, R.string.net_error, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * The class that is responsible to send locally-created and zomato-saved
     * restaurants to the back-end database on Heroku.
     *
     */
    private class SyncHerokuTask extends AsyncTask<Restaurant, Void, Boolean> {

        /**
         * Indicates to the user that the sync has started.
         */
        @Override
        public void onPreExecute() {
            Toast.makeText(context, R.string.sync_start, Toast.LENGTH_SHORT).show();
        }

        /**
         * Informs the user whether the sync was successful.
         * @param noErrors true if there were no errors during the sync; false otherwise.
         */
        @Override
        public void onPostExecute(Boolean noErrors) {
            if(noErrors)
                Toast.makeText(context, R.string.sync_success, Toast.LENGTH_SHORT).show();
            else
                Toast.makeText(context, R.string.sync_error, Toast.LENGTH_SHORT).show();
        }

        /**
         * Sync local and zomato-saved resturants to Heroku database.
         * It updates the heroku id of the local database if the transaction was successful.
         * @param restos the restaurants to save on heroku.
         * @return true if there were no errors during the sync; false otherwise.
         */
        @Override
        public Boolean doInBackground(Restaurant... restos) {
            boolean noErrors = true;
            HttpsPostSenderHelper sender = new HttpsPostSenderHelper();
            for(Restaurant resto : restos) {
                //check if this resto was not from heroku initially
                int herokuId = resto.getHerokuId();
                if(resto.getSource() != 2 && (herokuId == 0 || herokuId == -1)) {
                    JsonObject json = resto.toJsonObject();
                    //add email and password to authenticate the user.
                    SharedPreferences prefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE);
                    json.addProperty("email", prefs.getString("email", ""));
                    json.addProperty("password", prefs.getString("password", ""));
                    herokuId = sender.send(json.toString(), herokuCreateRestoUrl);
                    //update heroku id for this resto in local database
                    if(herokuId != -1) {
                        resto.setHerokuId(herokuId);
                        dbh.updateResto(resto);
                    }
                    noErrors = noErrors && herokuId != -1;
                    Log.d(TAG, "Json object to send: " + json);
                }
            }
            return noErrors;
        }
    }
}
