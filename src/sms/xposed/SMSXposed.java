package sms.xposed;

import static sms.xposed.util.LogUtils.LOGD;
import static sms.xposed.util.LogUtils.LOGI;
import static sms.xposed.util.LogUtils.makeLogTag;
import sms.xposed.receiver.FlashFlashlightReceiver;
import sms.xposed.receiver.NotificationDismissedReceiver;
import sms.xposed.receiver.ScreenStateReceiver;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
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
	private static final String PERMISSION_RECEIVE_SMS = "android.permission.RECEIVE_SMS";
	private static final String PACKAGE_HANGOUTS = "com.google.android.talk";
	private static final String ANDROID = "android";

	private WakeLock mSMSWakeLock;
	private long mSendOrderedBroadcastTimeMillis = -1;
	private boolean mBooted = false;

	@Override
	public void initZygote(StartupParam startupParam) throws Throwable {
		MODULE_PATH = startupParam.modulePath;
		LOGI(TAG, "initZygote");

		XSharedPreferences prefs = new XSharedPreferences(PACKAGE_NAME);
		final boolean wakeOnNewSMS = prefs.getBoolean("wake_on_new_sms", false);
		final boolean flashFlashlight = prefs.getBoolean("flash_flashlight",
				false);

		// Hook for Hangouts notifications
		final Class<?> notificationManagerClass = XposedHelpers.findClass(
				"android.app.NotificationManager", null);
		XposedHelpers.findAndHookMethod(notificationManagerClass, "notify",
				String.class, int.class, Notification.class,
				new XC_MethodHook() {
					@Override
					protected void beforeHookedMethod(MethodHookParam param)
							throws Throwable {
						Context context = (Context) XposedHelpers
								.getObjectField(param.thisObject, "mContext");
						if (context.getPackageName().equals(PACKAGE_HANGOUTS)) {
							LOGD(TAG, "Hangouts notify");
							Notification notification = (Notification) param.args[2];
							Intent intent = new Intent();
							intent.setAction(NotificationDismissedReceiver.ACTION_NOTIFICATIONDISMISSED_RECEIVER);
							notification.deleteIntent = PendingIntent
									.getBroadcast(context, 0, intent, 0);
						}
					}
				});

		// Hook to listen for SMS or Hangout received broadcasts
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
						LOGI(TAG, "isSms = " + isSms);
						LOGI(TAG, "isHangout = " + isHangout);

						if (isSms || isHangout) {
							boolean notificationHasBeenAlreadyIntercepted = false;
							long currentTime = System.currentTimeMillis();
							if (currentTime > (mSendOrderedBroadcastTimeMillis + 250)) {
								mSendOrderedBroadcastTimeMillis = currentTime;
								notificationHasBeenAlreadyIntercepted = true;
							}

							if (notificationHasBeenAlreadyIntercepted) {
								Context context = (Context) param.thisObject;
								if (flashFlashlight) {
									Intent intent = new Intent();
									intent.setAction(FlashFlashlightReceiver.ACTION_FLASHFLASHLIGHT_RECEIVER);
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

	@Override
	public void handleLoadPackage(final LoadPackageParam lpparam)
			throws Throwable {
		XSharedPreferences prefs = new XSharedPreferences(PACKAGE_NAME);
		if (lpparam.packageName.equals(ANDROID)) {
			LOGD(TAG, "Hooking Android");
			hookAndroid(lpparam, prefs);
		}
		if (lpparam.packageName.equals(PACKAGE_HANGOUTS)) {
			LOGD(TAG, "Hooking Hangouts");
			hookHangouts(lpparam, prefs);
		}
		return;
	}

	private void hookAndroid(final LoadPackageParam lpparam,
			final XSharedPreferences prefs) {
		// Hook for boot_completed
		final Class<?> bootReceiverClass = XposedHelpers.findClass(
				"com.android.server.BootReceiver", lpparam.classLoader);
		XposedHelpers.findAndHookMethod(bootReceiverClass, "onReceive",
				Context.class, Intent.class, new XC_MethodHook() {

					@Override
					protected void afterHookedMethod(MethodHookParam param)
							throws Throwable {
						LOGD(TAG, "BootReceiver.onReceiver");
						mBooted = true;
					}
				});

		// Hooks to get notified of screen ON / OFF events
		final Class<?> displayPowerStateClass = XposedHelpers.findClass(
				"com.android.server.power.Notifier", lpparam.classLoader);
		XposedHelpers.findAndHookMethod(displayPowerStateClass, "onScreenOn",
				new XC_MethodHook() {

					@Override
					protected void afterHookedMethod(MethodHookParam param)
							throws Throwable {
						if (mBooted) {
							Context context = (Context) XposedHelpers
									.getObjectField(param.thisObject,
											"mContext");
							Intent intent = new Intent(
									ScreenStateReceiver.ACTION_SCREENSTATE_RECEIVER);
							intent.putExtra(
									ScreenStateReceiver.EXTRA_SCREEN_ON, true);
							context.sendBroadcast(intent);
						}
					}

				});
		XposedHelpers.findAndHookMethod(displayPowerStateClass, "onScreenOff",
				new XC_MethodHook() {

					@Override
					protected void afterHookedMethod(MethodHookParam param)
							throws Throwable {
						if (mBooted) {
							Context context = (Context) XposedHelpers
									.getObjectField(param.thisObject,
											"mContext");
							Intent intent = new Intent(
									ScreenStateReceiver.ACTION_SCREENSTATE_RECEIVER);
							intent.putExtra(
									ScreenStateReceiver.EXTRA_SCREEN_ON, false);
							context.sendBroadcast(intent);
						}
					}

				});
	}

	private void hookHangouts(final LoadPackageParam lpparam,
			XSharedPreferences prefs) {
		XposedHelpers
				.findAndHookMethod(
						"com.google.android.apps.hangouts.realtimechat.NotificationReceiver",
						lpparam.classLoader, "onReceive", Context.class,
						Intent.class, new XC_MethodHook() {
							@Override
							protected void beforeHookedMethod(
									MethodHookParam param) throws Throwable {
								LOGD(TAG,
										"NotificationReceiver onReceive hooked");
								Context context = (Context) param.args[0];
								Intent intent = (Intent) param.args[1];
								Bundle extras = intent.getExtras();
								if (extras != null
										&& extras.containsKey("sms_sender")) {
									LOGD(TAG,
											"sms_sender = "
													+ extras.getString("sms_sender"));
									LOGD(TAG,
											"sms_msg = "
													+ extras.getString("sms_msg"));
								}
							}
						});
	}

}