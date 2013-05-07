package net.schwiz.eecs780;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.PowerManager.WakeLock;
import android.util.Log;

public class PullingService extends IntentService {

	public static int PULL_TIME = 30*1000;
	
	public PullingService(){
		this("PullingService");
	}
	
	public PullingService(String name) {
		super(name);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
		WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PullingService");
		wl.acquire();
		try {
			URL url = new URL(getString(R.string.api_host) + "/apis/list");
			Log.i("EECS780", "opening connection");
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			BufferedInputStream is = new BufferedInputStream(connection.getInputStream());
			String ret = fromStream(is);
			Log.i("EECS780", ret);
		} catch (Exception e) {
			e.printStackTrace();
		} finally{
			wl.release();
			AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
			am.cancel(createIntent(this));
			am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + PULL_TIME, createIntent(this));
		}
	}

	public static PendingIntent createIntent(Context context){
		Intent i = new Intent(context, PullingService.class);
		PendingIntent pi = PendingIntent.getService(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
		return pi;
	}
	
	public static boolean hasAlarm(Context context){
		return PendingIntent.getService(context, 0, new Intent(context, PullingService.class), PendingIntent.FLAG_NO_CREATE) != null;
	}
	
	String fromStream(InputStream in) throws IOException{
	    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
	    StringBuilder out = new StringBuilder();
	    String line;
	    while ((line = reader.readLine()) != null) {
	        out.append(line);
	    }
	    return out.toString();
	}
}
