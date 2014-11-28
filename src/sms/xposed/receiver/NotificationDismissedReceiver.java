package sms.xposed.receiver;

import static sms.xposed.util.LogUtils.LOGD;
import static sms.xposed.util.LogUtils.makeLogTag;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class NotificationDismissedReceiver extends BroadcastReceiver {

	private static final String TAG = makeLogTag(NotificationDismissedReceiver.class);
	public static final String ACTION_NOTIFICATIONDISMISSED_RECEIVER = "sms.xposed.notificationdismissed_receiver";

	@Override
	public void onReceive(Context context, Intent intent) {
		LOGD(TAG, "onReceive");
		Toast.makeText(context, "!!!!!!!!!!!!!!!!!!!!!", Toast.LENGTH_SHORT)
				.show();
	}
}
