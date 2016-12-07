package nodomain.freeyourgadget.gadgetbridge.activities;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.NavUtils;
import android.support.v4.app.RemoteInput;
import android.support.v4.content.LocalBroadcastManager;
import android.text.method.ScrollingMovementMethod;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService;
import nodomain.freeyourgadget.gadgetbridge.service.SummerProject;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.github.nkzawa.emitter.Emitter;
/**
 * Created by David on 2016/5/29.
 */
public class SummerConfigActivity extends GBActivity {

    private WebSocketClient mWebSocketClient;
    private static final Logger LOG = LoggerFactory.getLogger(SummerConfigActivity.class);

    private static final String EXTRA_REPLY = "reply";
    private static final String ACTION_REPLY
            = "nodomain.freeyourgadget.gadgetbridge.DebugActivity.action.reply";

    private boolean allowUpdate=true;
    private Boolean isConnected = true;
    private Button connect2ServerButton;
    private Button disconnectButton;
    private Button startMonitorHeartRateButton;
    private Button stopMonitorHeartRateButton;
    private int hrValue=-1;
    private TextView debugContent;
    private TextView userIDContent;
    private Thread t;

    private Socket mSocket;
    {
        try {
            mSocket = IO.socket("http://10.0.1.11:3000");
        } catch (URISyntaxException e) {}
    }



    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case GBApplication.ACTION_QUIT: {
                    finish();
                    break;
                }
                case ACTION_REPLY: {
                    Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
                    CharSequence reply = remoteInput.getCharSequence(EXTRA_REPLY);
                    LOG.info("got wearable reply: " + reply);
                    GB.toast(context, "got wearable reply: " + reply, Toast.LENGTH_SHORT, GB.INFO);
                    break;
                }
                case SummerProject.COPA_RESULT:
                {
                    GB.toast(context,intent.getStringExtra(SummerProject.SUMMER_MESSAGE),Toast.LENGTH_SHORT, GB.INFO);
                   // String temp=  debugContent.getText().toString()+"\n";
                   // debugContent.scrollTo(, Integer.MAX_VALUE);
                    //debugContent.setText(temp+intent.getStringExtra(SummerProject.SUMMER_MESSAGE));
                    debugContent.append("\n"+intent.getStringExtra(SummerProject.SUMMER_MESSAGE));
                    break;
                }
              /* case DeviceService.ACTION_HEARTRATE_MEASUREMENT: {
                    hrValue = intent.getIntExtra(DeviceService.EXTRA_HEART_RATE_VALUE, -1);
                    GB.toast(SummerConfigActivity.this, "Heart Rate measured: " + hrValue, Toast.LENGTH_LONG, GB.INFO);
                    try {
                        new updatingHeartbeat().execute(String.valueOf(hrValue));
                    } catch (Exception e) {
                        LOG.info("update error"+e.toString());
                        e.printStackTrace();
                    }
                    allowUpdate=true;
                    break;
                }*/
            }
        }
    };




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config_summer);

        IntentFilter filter = new IntentFilter();
        filter.addAction(GBApplication.ACTION_QUIT);
        filter.addAction(ACTION_REPLY);
       // filter.addAction(DeviceService.ACTION_HEARTRATE_MEASUREMENT);
        filter.addAction(SummerProject.COPA_RESULT);
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, filter);
        registerReceiver(mReceiver, filter); // for ACTION_REPLY
        debugContent = (TextView) findViewById(R.id.text_Debug);
        debugContent.setMovementMethod(new ScrollingMovementMethod());
        userIDContent = (TextView) findViewById(R.id.text_userID);

        userIDContent.setText(Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
        connect2ServerButton = (Button) findViewById(R.id.connect2ServerButton);
        connect2ServerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent intent = new Intent(SummerConfigActivity.this, SummerProject.class);
                intent.putExtra("needupdate",true);
                startService(intent);
                LOG.info("Start service for testing! No need for click with Website " );

                }
            }
        );

        disconnectButton = (Button) findViewById(R.id.disconnectButton);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent intent = new Intent(SummerConfigActivity.this, SummerProject.class);
                stopService(intent);
            }
        }
        );

        startMonitorHeartRateButton = (Button) findViewById(R.id.startMonitorHeartRateButton);
        startMonitorHeartRateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SummerConfigActivity.this, SummerProject.class);
                intent.putExtra("needupdate",false);
                startService(intent);
               //mSocket.emit("begin");
                LOG.info("Starting Service! " );
            }
        }
        );

        stopMonitorHeartRateButton = (Button) findViewById(R.id.stopMonitorHeartRateButton);
        stopMonitorHeartRateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SummerConfigActivity.this, SummerProject.class);
                stopService(intent);
               /* try {
                    shutdownThread();
                } catch (InterruptedException e) {
                    LOG.info("Shut down thread error! "+e.toString() );
                    e.printStackTrace();
                }*/
            }
        }
        );
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
        unregisterReceiver(mReceiver);
    }



    void threadMessage(String message) {
        String threadName =
                Thread.currentThread().getName();
        System.out.format("%s: %s%n",
                threadName,
                message);
    }
    private  class MessageLoop implements Runnable {
        public void run() {

                int i = 0;
                while (true) {
                    if (allowUpdate) {
                        allowUpdate = false;
                        GBApplication.deviceService().onHeartRateTest();

                        threadMessage("david ididid " + i);
                        i++;
                    }

                }



        }
    }

    private String sendPost(String heartrate)
            throws Exception {

        String json = "";


        // 3. build jsonObject
        JSONObject userdata = new JSONObject();
        JSONObject bs=new JSONObject();
        JSONObject parent=new JSONObject();

        bs.put("userid",Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID) );
        bs.put("heart", heartrate);
        userdata.put("userdata", bs);

        // 4. convert JSONObject to JSON to String
        json = userdata.toString();

        HttpURLConnection urlConnection;
        String url1="http://10.0.1.11:3000/users/";
        String data = json;
        String result = null;
    //  try {


            //Connect
            urlConnection = (HttpURLConnection) ((new URL(url1).openConnection()));
        urlConnection.setDoOutput(true);
        urlConnection.setRequestProperty("Content-Type", "application/json");
        urlConnection.setRequestProperty("Accept", "application/json");
        urlConnection.setRequestMethod("POST");
        urlConnection.connect();

        //Write
        OutputStream outputStream = urlConnection.getOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, "UTF-8"));
        writer.write(data);
        writer.close();
        outputStream.close();

            //Read
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), "UTF-8"));

        String line = null;
        StringBuilder sb = new StringBuilder();

        while ((line = bufferedReader.readLine()) != null) {
            sb.append(line);
        }

        bufferedReader.close();
        result = sb.toString();
        return result;
       /* } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }*/

    }


    private class updatingHeartbeat extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... heartrate) {
            try {
                ;
                return "send post" +sendPost(heartrate[0]);
            } catch (Exception e) {

                e.printStackTrace();
                return " send post error " ;
            }

            // params comes from the execute() call: params[0] is the url.
        }
        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            LOG.info(result);
        }
    }


}
