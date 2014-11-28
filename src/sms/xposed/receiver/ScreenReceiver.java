package sms.xposed.receiver;

import static sms.xposed.util.LogUtils.LOGD;
import static sms.xposed.util.LogUtils.makeLogTag;
import sms.xposed.service.FlashFlashlightService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class ScreenReceiver extends BroadcastReceiver {

	private static final String TAG = makeLogTag(ScreenReceiver.class);

	@Override
	public void onReceive(Context context, Intent intent) {
		LOGD(TAG, "onReceive action: " + intent.getAction());
		context.stopService(new Intent(context, FlashFlashlightService.class));
	}

	public IntentFilter getIntentFilter() {
		IntentFilter f = new IntentFilter();
		f.addAction(Intent.ACTION_SCREEN_ON);
		return f;
	}

}
