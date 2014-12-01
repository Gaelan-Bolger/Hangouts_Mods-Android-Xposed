package sms.xposed.receiver;

import static sms.xposed.util.LogUtils.LOGD;
import static sms.xposed.util.LogUtils.LOGI;
import static sms.xposed.util.LogUtils.makeLogTag;
import sms.xposed.R;
import sms.xposed.SMSXposed;
import sms.xposed.activity.ReplyActionActivity;
import sms.xposed.util.ContactUtils;
import sms.xposed.util.DeviceUtils;
import sms.xposed.util.SMSUtils;
import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.widget.RemoteViews;

public class NotificationActionReceiver extends BroadcastReceiver {

	private static final String TAG = makeLogTag(NotificationActionReceiver.class);
	public static final String ACTION_NOTIFY = "sms.xposed.notificationaction_receiver.notify";
	public static final String ACTION_DISMISS = "sms.xposed.notificationaction_receiver.dismiss";
	public static final String ACTION_REPLY = "sms.xposed.notificationaction_receiver.reply";
	public static final String ACTION_DELETE = "sms.xposed.notificationaction_receiver.delete";

	@Override
	public void onReceive(Context context, Intent intent) {
		LOGD(TAG, "[onReceive]");

		if (null == intent.getExtras() || !intent.hasExtra(SMSXposed.EXTRA_TAG)
				|| !intent.hasExtra(SMSXposed.EXTRA_ID)) {
			LOGI(TAG, "intent does not contain proper extras");
			return;
		}

		String action = intent.getAction();
		String tag = intent.getStringExtra("tag");
		int id = intent.getIntExtra("id", -1);

		cancel(context, tag, id);

		CharSequence contentTitle = intent
				.getCharSequenceExtra(SMSXposed.EXTRA_CONTENT_TITLE);
		CharSequence contentText = intent
				.getCharSequenceExtra(SMSXposed.EXTRA_CONTENT_TEXT);

		if (action.equals(ACTION_NOTIFY)) {
			LOGI(TAG, "Notify");
			int iconLevel = intent.getIntExtra(SMSXposed.EXTRA_ICON_LEVEL, 0);
			int flags = intent.getIntExtra(SMSXposed.EXTRA_FLAGS, -1);
			int defaults = intent.getIntExtra(SMSXposed.EXTRA_DEFAULTS, -1);
			long when = intent.getLongExtra(SMSXposed.EXTRA_WHEN, -1);
			CharSequence tickerText = intent
					.getCharSequenceExtra(SMSXposed.EXTRA_TICKER_TEXT);
			RemoteViews tickerView = intent
					.getParcelableExtra(SMSXposed.EXTRA_TICKER_VIEW);
			Bitmap largeIcon = intent
					.getParcelableExtra(SMSXposed.EXTRA_LARGE_ICON);
			Uri sound = intent.getParcelableExtra(SMSXposed.EXTRA_SOUND);
			PendingIntent contentIntent = intent
					.getParcelableExtra(SMSXposed.EXTRA_CONTENT_INTENT);

			// Rebuild notification in package context
			Builder builder = new Notification.Builder(context)
					.setContentTitle(contentTitle).setContentText(contentText)
					.setContentIntent(contentIntent)
					.setSmallIcon(R.drawable.ic_stat_notify_chat, iconLevel)
					.setLargeIcon(largeIcon).setDefaults(defaults)
					.setSound(sound).setWhen(when)
					.setTicker(tickerText, tickerView);

			// Add dismiss intent
			intent.setAction(NotificationActionReceiver.ACTION_DISMISS);
			PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(
					context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			builder.setDeleteIntent(dismissPendingIntent);

			// Add a reply action
			intent.setAction(NotificationActionReceiver.ACTION_REPLY);
			PendingIntent replyPendingIntent = PendingIntent.getBroadcast(
					context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			builder.addAction(android.R.drawable.ic_menu_set_as, "Reply",
					replyPendingIntent);

			// Add a delete action
			intent.setAction(NotificationActionReceiver.ACTION_DELETE);
			PendingIntent deletePendingIntent = PendingIntent.getBroadcast(
					context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			builder.addAction(android.R.drawable.ic_menu_close_clear_cancel,
					"Delete", deletePendingIntent);

			Notification notification = builder.build();

			// Set flags
			if (flags != -1) {
				notification.flags = flags;
			}
			notify(context, tag, id, notification);
		} else if (action.equals(ACTION_DISMISS)) {
			LOGI(TAG, "Dismiss");
			SharedPreferences preferences = PreferenceManager
					.getDefaultSharedPreferences(context);
			if (preferences.getBoolean("action_mark_read", true)) {
				markRead(context, contentTitle, contentText);
			}
		} else if (action.equals(ACTION_DELETE)) {
			LOGI(TAG, "Delete");
			SMSUtils.delete(context, 0, contentText.toString());
			cleanUpHangoutsDb(context);
		} else if (action.equals(ACTION_REPLY)) {
			LOGI(TAG, "Reply");
			markRead(context, contentTitle, contentText);
			DeviceUtils.hideStatusBar(context);
			intent.setComponent(new ComponentName(context,
					ReplyActionActivity.class));
			context.startActivity(intent);
		}
	}

	private void markRead(Context context, CharSequence contentTitle,
			CharSequence contentText) {
		String number = ContactUtils.getContactNumber(context,
				contentTitle.toString());
		SMSUtils.markAsRead(context, number, contentText.toString());
		cleanUpHangoutsDb(context);
	}

	private void notify(Context context, String tag, int id,
			Notification notification) {
		NotificationManager nm = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.notify(tag, id, notification);
	}

	private void cancel(Context context, String tag, int id) {
		NotificationManager nm = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancel(tag, id);
	}

	private void cleanUpHangoutsDb(Context context) {
		LOGI(TAG, "cleanUpHangoutsDb");
		Intent intent = new Intent(
				"android.provider.Telephony.ACTION_CHANGE_DEFAULT");
		context.sendBroadcast(intent);
		// AccountManager accountManager = AccountManager.get(context);
		// Account[] accounts = accountManager.getAccountsByType("com.google");
		// String accountName = accounts[0].name;
		// Intent intent = new Intent(
		// "com.google.android.apps.hangouts.CLEANUP_DB");
		// intent.putExtra("account_name", accountName);
		// context.sendBroadcast(intent);
	}

}
