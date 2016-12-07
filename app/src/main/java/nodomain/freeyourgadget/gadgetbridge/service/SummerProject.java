package nodomain.freeyourgadget.gadgetbridge.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.app.RemoteInput;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Button;
import android.widget.Toast;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.github.nkzawa.emitter.Emitter;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Calendar;

public class SummerProject extends Service {


    private static final Logger LOG = LoggerFactory.getLogger(SummerProject.class);

    private static final String EXTRA_REPLY = "reply";
    private static final String server="http://180.150.188.225:3000";
    private static final String ACTION_REPLY
            = "nodomain.freeyourgadget.gadgetbridge.DebugActivity.action.reply";
    static final public String COPA_RESULT = "nodomain.freeyourgadget.gadgetbridge.service.SummerProject";

    static final public String SUMMER_MESSAGE = "com.ccs.summer.MSG";
    private LocalBroadcastManager broadcaster;


    private boolean allowUpdate=true;
    private boolean needUpdate=false;
    private Boolean isConnected = true;
    private Button connect2ServerButton;
    private Button disconnectButton;
    private Button startMonitorHeartRateButton;
    private Button stopMonitorHeartRateButton;
    private int hrValue=-1;

    private boolean isRunning  = false;

    private Thread t;

    private Socket mSocket;
    {
        try {
            IO.Options opts = new IO.Options();
            opts.forceNew = true;
            opts.reconnection = true;
            //opts.secure = true;
           // opts.sslContext = sc;

            mSocket = IO.socket(server,opts);
        } catch (URISyntaxException e) {}
    }

    public void sendResult(String message) {
        Intent intent = new Intent(COPA_RESULT);
        Calendar calendar = new GregorianCalendar();
        Date trialTime = new Date();
        calendar.setTime(trialTime);
        if(message != null)
            intent.putExtra(SUMMER_MESSAGE, message +" time:"+calendar.get(Calendar.HOUR)+":"+calendar.get(Calendar.MINUTE)+":"+calendar.get(Calendar.SECOND));
        broadcaster.sendBroadcast(intent);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //LOG.info(intent.getAction().toString());
            switch (intent.getAction()) {
                case DeviceService.ACTION_HEARTRATE_MEASUREMENT: {
                    hrValue = intent.getIntExtra(DeviceService.EXTRA_HEART_RATE_VALUE, -1);
                    sendResult("heart rate: "+String.valueOf(hrValue));
                  //  GB.toast(SummerConfigActivity.this, "Heart Rate measured: " + hrValue, Toast.LENGTH_LONG, GB.INFO);
                    try {
                      new updatingHeartbeat().execute(String.valueOf(hrValue));
                        allowUpdate=true;
                    } catch (Exception e) {
                        LOG.info("update error"+e.toString());
                        e.printStackTrace();
                    }

                    break;
                }
            }
        }
    };

    private Emitter.Listener onConnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {

                    // if(!isConnected) {
                    LOG.info("Websocket connnected! " );
                    sendResult("Websocket connnected! ");
                 //  Toast.makeText(SummerConfigActivity.this.getApplicationContext(),
                    //        "Websocket connnected! " , Toast.LENGTH_LONG).show();
                    isConnected = true;
                    // }

        }
    };

    private Emitter.Listener onDisconnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {

                    isConnected = false;

                    LOG.info("Websocket closed " +args[0].toString());

        }
    };

    private Emitter.Listener onConnectError = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            sendResult("Websocket error " +args[0].toString());
                    LOG.info("Websocket error " +args[0].toString());
                    // Toast.makeText(getApplicationContext(),
                    //       R.string.error_connect, Toast.LENGTH_LONG).show();

        }
    };

    private Emitter.Listener onNewMessage = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            sendResult("Start monitoring thread! " );
                    LOG.info("Start monitoring thread! " );
                    needUpdate=true;
                  //  t = new Thread(new MessageLoop());
                 //   t.start();

        }
    };
    private Emitter.Listener onReconnect = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {

            LOG.info("Reconnecting  "+args[0].toString() );

            //  t = new Thread(new MessageLoop());
            //   t.start();

        }
    };
    private Emitter.Listener onStopMonitoring = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {

                    needUpdate=false;
                    LOG.info("stop send post to server! ");
            sendResult("stop send post to server! " );

        }
    };


    private String sendPost(String heartrate)
            throws Exception {

        String json = "";


        // 3. build jsonObject
        JSONObject userdata = new JSONObject();
        JSONObject bs=new JSONObject();
        JSONObject parent=new JSONObject();

        bs.put("userid", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID) );
        bs.put("heart", heartrate);
        userdata.put("userdata", bs);

        // 4. convert JSONObject to JSON to String
        json = userdata.toString();

        HttpURLConnection urlConnection;
        String url1=server+"/users/";
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
                sendResult("posting heartrate to server! ");
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

    public SummerProject() {

    }
    @Override
    public void onCreate() {
        broadcaster = LocalBroadcastManager.getInstance(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                mSocket.on(Socket.EVENT_CONNECT,onConnect);
                mSocket.on(Socket.EVENT_DISCONNECT,onDisconnect);
                mSocket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
                mSocket.on(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
                mSocket.on(Socket.EVENT_RECONNECT, onReconnect);
                mSocket.on("give_me_heart", onNewMessage);
                mSocket.on("enough", onStopMonitoring);



            }
        }).start();

        IntentFilter filter = new IntentFilter();
        filter.addAction(DeviceService.ACTION_HEARTRATE_MEASUREMENT);
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, filter);
        registerReceiver(mReceiver, filter); // for ACTION_REPLY


        LOG.info("Websocket Start connect! " );
        isRunning = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Creating new thread for my service
        //Always write your long running tasks in a separate thread, to avoid ANR
        needUpdate= intent.getBooleanExtra("needupdate",false);
        mSocket.connect( );
        new Thread(new Runnable() {
                @Override
                public void run() {
                //Your logic that service will perform will be placed here
                //In this example we are just looping and waits for 1000 milliseconds in each loop.
                LOG.info("Startcommand triggered ");
              while (true)
              {
                  if(needUpdate) {
                      if (allowUpdate) {
                          allowUpdate = false;
                          if (!mSocket.connected()) {
                              try
                              {
                                  mSocket.connect();
                                  Thread.sleep(3000);
                              }
                              catch(InterruptedException e)
                              {
                                  LOG.info("server connect error "+e.toString());
                              }

                          }
                          GBApplication.deviceService().onHeartRateTest();
                          LOG.info("trigger update heartrate ");
                          sendResult("trigger update heartrate! ");
                      }
                  }
                  try {
                      Thread.sleep(1000);
                  } catch (InterruptedException e) {
                      e.printStackTrace();
                  }
              }
            }
        }).start();

        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        mSocket.close();
        needUpdate=false;
        isRunning = false;
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
        unregisterReceiver(mReceiver);
     //   Log.i(TAG, "Service onDestroy");
    }
    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
