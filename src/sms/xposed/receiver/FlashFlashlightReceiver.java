package sms.xposed.receiver;

import static sms.xposed.util.LogUtils.LOGD;
import static sms.xposed.util.LogUtils.makeLogTag;
import sms.xposed.service.FlashFlashlightService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

public class FlashFlashlightReceiver extends BroadcastReceiver {

	private static final String TAG = makeLogTag(FlashFlashlightReceiver.class);
	public static final String ACTION_FLASHFLASHLIGHT_RECEIVER = "sms.xposed.flashflashlight_receiver";

	@Override
	public void onReceive(Context context, Intent intent) {
		LOGD(TAG, "onReceive");
		PackageManager packageManager = context.getPackageManager();
		if (packageManager
				.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
			context.startService(new Intent(context,
					FlashFlashlightService.class));
		}
	}
}
