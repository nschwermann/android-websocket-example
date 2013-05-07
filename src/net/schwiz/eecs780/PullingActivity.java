package net.schwiz.eecs780;

import android.os.Bundle;
import android.os.SystemClock;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Intent;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class PullingActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		final Button pull = (Button)findViewById(R.id.pulling_button);
		if(PullingService.hasAlarm(this)){
			pull.setText("Stop Service");
		}
		else {
			pull.setText("Start Service");
		}
		pull.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if(PullingService.hasAlarm(PullingActivity.this)){
					AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
					PendingIntent pi = PullingService.createIntent(PullingActivity.this);
					am.cancel(pi);
					pi.cancel();
					pull.setText("Start Service");
				}
				else {
					Intent i = new Intent(PullingActivity.this, PullingService.class);
					startService(i);
					pull.setText("Stop Service");
				}
			}
		});
	}

}
