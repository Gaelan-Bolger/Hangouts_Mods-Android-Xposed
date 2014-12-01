package sms.xposed;

import static sms.xposed.util.LogUtils.LOGD;
import static sms.xposed.util.LogUtils.LOGE;
import static sms.xposed.util.LogUtils.LOGI;
import static sms.xposed.util.LogUtils.makeLogTag;

import java.util.Set;

import sms.xposed.receiver.FlashFlashlightReceiver;
import sms.xposed.receiver.NotificationActionReceiver;
import sms.xposed.receiver.ScreenStateReceiver;
import android.app.Notification;
import android.app.Notification.Action;
import android.app.Notification.Builder;
import android.app.AppOpsManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.XResources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.RemoteViews;
import android.widget.TextView;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class SMSXposed implements IXposedHookZygoteInit, IXposedHookLoadPackage {

	private static final String TAG = makeLogTag(SMSXposed.class);
	private static final String PACKAGE_NAME = SMSXposed.class.getPackage()
			.getName();
	private static final String PACKAGE_HANGOUTS = "com.google.android.talk";
	private static final String ANDROID = "android";
	private static String MODULE_PATH = null;
	public static final String EXTRA_SOUND = "sound";
	public static final String EXTRA_DEFAULTS = "defaults";
	public static final String EXTRA_LARGE_ICON = "largeIcon";
	public static final String EXTRA_CONTENT_TEXT = "contentText";
	public static final String EXTRA_CONTENT_TITLE = "contentTitle";
	public static final String EXTRA_TICKER_VIEW = "tickerView";
	public static final String EXTRA_TICKER_TEXT = "tickerText";
	public static final String EXTRA_WHEN = "when";
	public static final String EXTRA_FLAGS = "flags";
	public static final String EXTRA_ICON_LEVEL = "iconLevel";
	public static final String EXTRA_ICON = "icon";
	public static final String EXTRA_CONTENT_INTENT = "contentIntent";
	public static final String EXTRA_CONTENT_VIEW = "contentView";
	public static final String EXTRA_ID = "id";
	public static final String EXTRA_TAG = "tag";

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
					protected void afterHookedMethod(MethodHookParam param)
							throws Throwable {
						Context context = (Context) XposedHelpers
								.getObjectField(param.thisObject, "mContext");
						if (context.getPackageName().equals(PACKAGE_NAME)
								|| context.getPackageName().equals(
										PACKAGE_HANGOUTS)) {

							// AppOpsManager appOpsManager = (AppOpsManager)
							// context.getSystemService(Context.APP_OPS_SERVICE);

							Notification notification = (Notification) param.args[2];
							int defaults = notification.defaults;
							if (defaults == 6) {
								boolean notificationHasBeenAlreadyIntercepted = false;
								long currentTime = System.currentTimeMillis();
								if (currentTime > (mSendOrderedBroadcastTimeMillis + 250)) {
									mSendOrderedBroadcastTimeMillis = currentTime;
									notificationHasBeenAlreadyIntercepted = true;
								}
								if (notificationHasBeenAlreadyIntercepted) {
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
			LOGI(TAG, "Hooking Android");
			hookAndroid(lpparam, prefs);
		}
		if (lpparam.packageName.equals(PACKAGE_HANGOUTS)) {
			LOGI(TAG, "Hooking Hangouts");
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
						LOGD(TAG, "BootReceiver.onReceived");
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
		// cb
		final Class<?> cbClass = XposedHelpers.findClass("cb",
				lpparam.classLoader);
		XposedHelpers.findAndHookMethod(cbClass, "b", new XC_MethodHook() {

			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {
				LOGD(TAG, "[cb.b hooked before]");

				// Check if we should add buttons
				Bundle b = (Bundle) XposedHelpers.getObjectField(
						param.thisObject, "b");
				if (b.containsKey("android.support.isGroupSummary")
						&& b.getBoolean("android.support.isGroupSummary", false)) {
					LOGI(TAG, "Group summary, no action taken");
					return;
				} else if (b.containsKey("android.support.sortKey")) {
					LOGI(TAG, "Sort key found, no action taken");
					return;
				} else if (b.containsKey("android.support.actionExtras")) {
					LOGI(TAG, "Actions already exist, no action taken");
					return;
				}

				// Get notification builder
				Notification.Builder builder = (Builder) XposedHelpers
						.getObjectField(param.thisObject, "a");

				// Add intercept action as flag for later
				builder.addAction(0, "INTERCEPT", null);
			}

		});

		// // cc
		// final Class<?> ccClass = XposedHelpers.findClass("cc",
		// lpparam.classLoader);
		// XposedHelpers.findAndHookMethod(ccClass, "a", String.class,
		// int.class,
		// Notification.class, new XC_MethodHook() {
		//
		// @Override
		// protected void beforeHookedMethod(MethodHookParam param)
		// throws Throwable {
		// LOGE(TAG, "[cc.a hooked before]");
		// final Class<?> bfClass = XposedHelpers.findClass("bf",
		// lpparam.classLoader);
		// Notification notification = (Notification) param.args[2];
		// Bundle bundle = (Bundle) XposedHelpers.callStaticMethod(
		// bfClass, "a", notification);
		// LOGE(TAG, "BUNDLE: " + bundle + " " + bundle.size());
		// Set<String> set = bundle.keySet();
		// String[] keys = set.toArray(new String[set.size()]);
		// for (String key : keys) {
		// LOGD(TAG,
		// "KEY: " + key + " VALUE: "
		// + bundle.get(key));
		// }
		//
		// }
		//
		// });

		// cf
		final Class<?> cfClass = XposedHelpers.findClass("cf",
				lpparam.classLoader);
		// notify
		XposedHelpers.findAndHookMethod(cfClass, "a",
				NotificationManager.class, String.class, int.class,
				Notification.class, new XC_MethodReplacement() {

					@Override
					protected Object replaceHookedMethod(MethodHookParam param)
							throws Throwable {
						LOGE(TAG, "[cf.a hooked replace] notify");
						NotificationManager nm = (NotificationManager) param.args[0];
						Context context = (Context) XposedHelpers
								.getObjectField(nm, "mContext");
						String tag = (String) param.args[1];
						int id = (Integer) param.args[2];
						Notification notification = (Notification) param.args[3];

						boolean isIntercept = false;
						Action[] actions = (Action[]) XposedHelpers
								.getObjectField(notification, "actions");
						for (Action action : actions) {
							String actionText = (String) XposedHelpers
									.getObjectField(action, "title");
							if (actionText.equals("INTERCEPT")) {
								isIntercept = true;
								continue;
							}
						}
						if (isIntercept) {
							LOGI(TAG, "Intercepted notification");
							Intent intent = new Intent(
									NotificationActionReceiver.ACTION_NOTIFY);
							Bundle bundle = new Bundle();
							bundle.putString(EXTRA_TAG, tag);
							bundle.putInt(EXTRA_ID, id);

							RemoteViews contentView = notification.contentView;
							bundle.putParcelable(EXTRA_CONTENT_VIEW,
									contentView);
							PendingIntent contentIntent = notification.contentIntent;
							bundle.putParcelable(EXTRA_CONTENT_INTENT,
									contentIntent);
							int icon = notification.icon;
							bundle.putInt(EXTRA_ICON, icon);
							int iconLevel = notification.iconLevel;
							bundle.putInt(EXTRA_ICON_LEVEL, iconLevel);
							int flags = notification.flags;
							bundle.putInt(EXTRA_FLAGS, flags);
							long when = notification.when;
							bundle.putLong(EXTRA_WHEN, when);
							CharSequence tickerText = notification.tickerText;
							bundle.putCharSequence(EXTRA_TICKER_TEXT,
									tickerText);
							RemoteViews tickerView = notification.tickerView;
							bundle.putParcelable(EXTRA_TICKER_VIEW, tickerView);

							int layoutId = contentView.getLayoutId();
							ViewGroup localView = (ViewGroup) LayoutInflater
									.from(context).inflate(layoutId, null);
							contentView.reapply(context, localView);

							int titleResId = XResources.getSystem()
									.getIdentifier("title", "id", "android");
							TextView title = (TextView) localView
									.findViewById(titleResId);
							CharSequence contentTitle = title.getText();
							bundle.putCharSequence(EXTRA_CONTENT_TITLE,
									contentTitle);

							int textResId = XResources.getSystem()
									.getIdentifier("text", "id", "android");
							TextView text = (TextView) localView
									.findViewById(textResId);
							CharSequence contentText = text.getText();
							bundle.putCharSequence(EXTRA_CONTENT_TEXT,
									contentText);

							Bitmap largeIcon = notification.largeIcon;
							bundle.putParcelable(EXTRA_LARGE_ICON, largeIcon);

							int defaults = notification.defaults;
							bundle.putInt(EXTRA_DEFAULTS, defaults);

							Uri sound = notification.sound;
							bundle.putParcelable(EXTRA_SOUND, sound);

							PendingIntent deleteIntent = notification.deleteIntent;

							intent.putExtras(bundle);
							context.sendBroadcast(intent);
						} else {
							LOGI(TAG, "No intercept");
							nm.notify(tag, id, notification);
						}
						return null;
					}

				});
		// cancel
		XposedHelpers.findAndHookMethod(cfClass, "a",
				NotificationManager.class, String.class, int.class,
				new XC_MethodHook() {

					@Override
					protected void beforeHookedMethod(MethodHookParam param)
							throws Throwable {
						LOGE(TAG, "[cf.a hooked] cancel");
						NotificationManager nm = (NotificationManager) param.args[0];
						Context context = (Context) XposedHelpers
								.getObjectField(nm, "mContext");
						String tag = (String) param.args[1];
						int id = (Integer) param.args[2];
						LOGI(TAG, "context: " + context.getPackageName());
						LOGI(TAG, "tag: " + tag + " id: " + id);
					}

				});

		// RealTimeChatService
		Class<?> rtsClass = XposedHelpers
				.findClass(
						"com.google.android.apps.hangouts.realtimechat.RealTimeChatService",
						lpparam.classLoader);
		XposedHelpers.findAndHookMethod(rtsClass, "t", new XC_MethodHook() {

			@Override
			protected void beforeHookedMethod(MethodHookParam param)
					throws Throwable {
				LOGD(TAG, "[RealTimeChatService.t hooked]");
			}

		});

	}
}