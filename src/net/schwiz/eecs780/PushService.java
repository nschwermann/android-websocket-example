package net.schwiz.eecs780;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;

import com.codebutler.android_websockets.WebSocketClient;
import com.codebutler.android_websockets.WebSocketClient.Listener;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

public class PushService extends Service implements Listener {

	public static final String ACTION_PING = "edu.ku.eecs780.ACTION_PING";
	public static final String ACTION_CONNECT = "edu.ku.eecs780.ACTION_CONNECT";
	public static final String ACTION_SHUT_DOWN = "edu.edu.eecs780.ACTION_SHUT_DOWN";
	
	private WebSocketClient mClient;
	private final IBinder mBinder = new Binder();
	private HashSet<String> mList = new HashSet<String>();
	private boolean mShutDown = false;
	private PushListener mListener;
	private Handler mHandler;
	
	public static Intent startIntent(Context context){
		Intent i = new Intent(context, PushService.class);
		i.setAction(ACTION_CONNECT);
		return i;
	}
	
	public static Intent pingIntent(Context context){
		Intent i = new Intent(context, PushService.class);
		i.setAction(ACTION_PING);
		return i;
	}
	
	public static Intent closeIntent(Context context){
		Intent i = new Intent(context, PushService.class);
		i.setAction(ACTION_SHUT_DOWN);
		return i;
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mHandler = new Handler();
		Log.d("EECS780", "Creating Service " + this.toString());
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d("EECS780", "Destroying Service " + this.toString());
		if(mClient != null && mClient.isConnected()) mClient.disconnect();
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		WakeLock wakelock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EECS780 Service");
		wakelock.acquire();
		Log.i("EECS780", "PushService start command");
		if(intent != null) Log.i("EECS780", intent.toUri(0));
		mShutDown = false;
		if(mClient == null) {
			WakeLock clientlock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EECS780");
			mClient = new WebSocketClient(URI.create(getString(R.string.socket_host) + "/list/socket"), this, null, clientlock);
		}
		
		if(!mClient.isConnected()) mClient.connect();
		
		if(intent != null) {
			if(ACTION_PING.equals(intent.getAction())){
				if(mClient.isConnected()) mClient.send("{\"action\":\"ping\"}");
			}
			else if(ACTION_SHUT_DOWN.equals(intent.getAction())){
				mShutDown = true;
				if(mClient.isConnected()) mClient.disconnect();
			}
		}
		
		if(intent == null || !intent.getAction().equals(ACTION_SHUT_DOWN)){
			AlarmManager am = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
			PendingIntent operation = PendingIntent.getService(this, 0, PushService.pingIntent(this), PendingIntent.FLAG_NO_CREATE); 
			if(operation == null){
		       	operation = PendingIntent.getService(this, 0, PushService.pingIntent(this), PendingIntent.FLAG_UPDATE_CURRENT);
		       	am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), AlarmManager.INTERVAL_HALF_HOUR, operation);
		    }
		}
		
		wakelock.release();
		return START_STICKY;
	}

	public class Binder extends android.os.Binder{
		
		PushService getService(){
			return PushService.this;
		}
	}

	public synchronized void setListener(PushListener listener){
		mListener = listener;
	}
	
	public synchronized void addItem(String item){
		if(mClient != null && mClient.isConnected()){
			Response res = new Response();
			res.setAction("add");
			res.setName(item);
			mClient.send(res.toString());
			mList.add(item);
		}
	}
	
	public synchronized void removeItem(String item){
		if(mClient != null && mClient.isConnected()){
			Response res = new Response();
			res.setAction("delete");
			res.setName(item);
			mClient.send(res.toString());
			mList.remove(item);
		}
	}
	
	public synchronized boolean isConnected() {
		return mClient != null && mClient.isConnected();
	}
	
	public synchronized ArrayList<String> getList(){
		ArrayList<String> ret = new ArrayList<String>();
		ret.addAll(mList);
		return ret;
	}
	
	@Override
	public void onConnect() {
		Log.d("EECS780", "Connected to websocket");
	}

	@Override
	public synchronized void onDisconnect(int code, String reason) {
		Log.d("EECS780", String.format("Disconnected! Code: %d Reason: %s", code, reason));
		if(!mShutDown){
			startService(startIntent(this));
		}
		else{
			stopSelf();
		}
	}

	@Override
	public synchronized void onError(Exception arg0) {
		Log.e("EECS780", "PushService", arg0);
		startService(startIntent(this));
	}

	@Override
	public synchronized void onMessage(String arg0) {
		WakeLock wakelock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EECS780 Service");
		wakelock.acquire();
		Log.d("EECS780", arg0);
		final Response response = Response.deserializeList(arg0);
		if("connect".equals(response.getAction())){
			mList.addAll(response.getList());
		}	
		else if("add".equals(response.getAction())){
			mList.add(response.getName());
		}
		else if("delete".equals(response.getAction())){
			mList.remove(response.getName());
		}
		else {
			wakelock.release();
			return;
		}
		
		mHandler.post(new Runnable() {
			
			@Override
			public void run() {
				if(mListener != null) mListener.newResponse(response);
				else sendNotification(response);
			}
		});
		wakelock.release();
	}

	private void sendNotification(Response response){
		Notification.Builder notBuilder = new Notification.Builder(this);
		StringBuilder stringBuilder = new StringBuilder();
		for(String s : mList) stringBuilder.append(s).append('\n');
		if(response.getAction().equals("connect")) {
			notBuilder.setContentTitle("Connected to list server");
		}
		else if(response.getAction().equals("add")) {
			notBuilder.setContentTitle("New item added");
			notBuilder.setContentText(response.getName());
		}
		else if(response.getAction().equals("delete")){
			notBuilder.setContentTitle("Item removed");
			notBuilder.setContentText(response.getName());
		}
		Intent intent =  new Intent(this, PushActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
		PendingIntent pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		notBuilder.setContentIntent(pi);
		notBuilder.setSmallIcon(android.R.drawable.ic_notification_overlay);
		notBuilder.setSound(RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION));
		notBuilder.setOnlyAlertOnce(false);
		notBuilder.setAutoCancel(true);
		Notification.BigTextStyle bst = new Notification.BigTextStyle(notBuilder);
		bst.bigText(stringBuilder.toString());
		
		NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		nm.notify(0, bst.build());
	}
	
	@Override
	public synchronized void onMessage(byte[] arg0) {
		// TODO Auto-generated method stub
		
	}
	
	public interface PushListener{
		public void newResponse(Response response);
	}
}
