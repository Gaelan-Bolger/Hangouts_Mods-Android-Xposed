package sms.xposed.receiver;

import static sms.xposed.util.LogUtils.LOGD;
import static sms.xposed.util.LogUtils.makeLogTag;
import sms.xposed.service.FlashFlashlightService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.preference.PreferenceManager;

public class ScreenStateReceiver extends BroadcastReceiver {

	private static final String TAG = makeLogTag(ScreenStateReceiver.class);
	public static final String ACTION_SCREENSTATE_RECEIVER = "sms.xposed.screenstate_receiver";
	public static final String EXTRA_SCREEN_ON = "screenOn";

	@Override
	public void onReceive(Context context, Intent intent) {
		LOGD(TAG, "onReceive");
		context.stopService(new Intent(context, FlashFlashlightService.class));
	}

	public void register(Context context) {
		context.registerReceiver(this, new IntentFilter(
				ACTION_SCREENSTATE_RECEIVER));
		PreferenceManager.getDefaultSharedPreferences(context).edit()
				.putBoolean("screenstate_receiver_registered", true).commit();
		LOGD(TAG, "ScreenStateReceiver registered");
	}

	public void unregister(Context context) {
		context.unregisterReceiver(this);
		PreferenceManager.getDefaultSharedPreferences(context).edit()
				.putBoolean("screenstate_receiver_registered", false).commit();
		LOGD(TAG, "ScreenStateReceiver unregistered");
	}
}
