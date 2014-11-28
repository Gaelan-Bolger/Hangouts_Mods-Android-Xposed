package sms.xposed;

import static sms.xposed.util.LogUtils.LOGD;
import static sms.xposed.util.LogUtils.LOGI;
import static sms.xposed.util.LogUtils.makeLogTag;
import sms.xposed.service.FlashFlashlightService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class SMSXposed implements IXposedHookZygoteInit, IXposedHookLoadPackage {

	private static final String TAG = makeLogTag(SMSXposed.class);

	private static final String PACKAGE_NAME = SMSXposed.class.getPackage()
			.getName();
	private static String MODULE_PATH = null;

	private static final String ACTION_SMS_DELIVER = "android.provider.Telephony.SMS_DELIVER";
	private static final String ACTION_C2DM_RECEIVE = "com.google.android.c2dm.intent.RECEIVE";
	private static final String PACKAGE_HANGOUTS = "com.google.android.talk";
	private static final String PERMISSION_RECEIVE_SMS = "android.permission.RECEIVE_SMS";

	private WakeLock mSMSWakeLock;
	private long mSendOrderedBroadcastTimeMillis = -1;

	@Override
	public void initZygote(StartupParam startupParam) throws Throwable {
		MODULE_PATH = startupParam.modulePath;
		LOGI(TAG, "initZygote");

		XSharedPreferences prefs = new XSharedPreferences(PACKAGE_NAME);
		final boolean wakeOnNewSMS = prefs.getBoolean("wake_on_new_sms", false);
		final boolean flashFlashlight = prefs.getBoolean("flash_flashlight",
				false);

		final Class<?> contextClass = XposedHelpers.findClass(
				"android.content.ContextWrapper", null);
		XposedBridge.hookAllMethods(contextClass, "sendOrderedBroadcast",
				new XC_MethodHook() {
					protected void beforeHookedMethod(MethodHookParam param)
							throws Throwable {
						LOGD(TAG, "sendOrderedBroadcast hooked");

						Intent i = (Intent) param.args[0];
						String intentAction = i.getAction();
						String intentPackage = i.getPackage();
						ComponentName component = i.getComponent();
						String componentPackage = null;
						if (null != component) {
							componentPackage = component.getPackageName();
						}
						String permission = (String) param.args[1];
						LOGI(TAG, "Action: " + intentAction);
						LOGI(TAG, "Package: " + intentPackage);
						LOGI(TAG, "Component: " + componentPackage);
						LOGI(TAG, "Permission: " + permission);

						boolean isSms = intentAction.equals(ACTION_SMS_DELIVER)
								&& (null != componentPackage && componentPackage
										.equals(PACKAGE_HANGOUTS))
								&& (null != permission && permission
										.equals(PERMISSION_RECEIVE_SMS));
						boolean isHangout = intentAction
								.equals(ACTION_C2DM_RECEIVE)
								&& (null != intentPackage && intentPackage
										.equals(PACKAGE_HANGOUTS));
						LOGI(TAG, "isSms = " + isSms + ", isHangout = "
								+ isHangout);

						if (isSms || isHangout) {
							boolean notificationHasBeenAlreadyIntercepted = false;
							long currentTime = System.currentTimeMillis();
							if (currentTime > (mSendOrderedBroadcastTimeMillis + 250)) {
								mSendOrderedBroadcastTimeMillis = currentTime;
								notificationHasBeenAlreadyIntercepted = true;
							} else {
								LOGD(TAG, "Duplicate broadcast");
							}

							if (notificationHasBeenAlreadyIntercepted) {
								Context context = (Context) param.thisObject;
								LOGD(TAG,
										"Hooked for package: "
												+ context.getPackageName());

								if (flashFlashlight) {
									LOGD(TAG, "Send flash flashlight broadcast");
									Intent intent = new Intent();
									intent.setAction("sms.xposed.flashflashlight_receiver");
									context.sendBroadcast(intent);
								}
								if (wakeOnNewSMS)
									wakeDevice(context);
							}
						}
					}
				});
	}

	private void wakeDevice(Context context) {
		LOGI(TAG, "wakeDevice");
		PowerManager pm = (PowerManager) context
				.getSystemService(Context.POWER_SERVICE);
		if (!pm.isScreenOn()) {
			int levelAndFlags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK
					| PowerManager.FULL_WAKE_LOCK
					| PowerManager.ACQUIRE_CAUSES_WAKEUP
					| PowerManager.ON_AFTER_RELEASE;
			mSMSWakeLock = pm.newWakeLock(levelAndFlags, "TAG");
			mSMSWakeLock.acquire();
			mSMSWakeLock.release();
		}
	}

	public void handleLoadPackage(final LoadPackageParam lpparam)
			throws Throwable {
		XSharedPreferences prefs = new XSharedPreferences(PACKAGE_NAME);

		if (lpparam.packageName.equals("com.android.mms"))
			hookSMS(lpparam, prefs);
		if (lpparam.packageName.equals("com.google.android.talk"))
			hookHangouts(lpparam, prefs);
		return;
	}

	private void hookSMS(final LoadPackageParam lpparam,
			XSharedPreferences prefs) {
	}

	private void hookHangouts(final LoadPackageParam lpparam,
			XSharedPreferences prefs) {
	}

}