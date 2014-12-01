package sms.xposed.util;

import static sms.xposed.util.LogUtils.LOGI;
import static sms.xposed.util.LogUtils.makeLogTag;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public class SMSUtils {

	private static final String CONTENT_URI_SMS_INBOX = "content://sms/";
	private static final String TAG = makeLogTag(SMSUtils.class);

	public static boolean isSMS(Context context, int number, String body) {
		LOGI(TAG, "isSMS");
		Uri uri = Uri.parse(CONTENT_URI_SMS_INBOX);
		Cursor cursor = context.getContentResolver().query(uri, null,
				"body = ?", new String[] { body }, null);
		cursor.moveToFirst();
		boolean exists = cursor.getCount() > 0;
		cursor.close();
		return exists;
	}

	public static boolean isUnread(Context context, int number, String body) {
		LOGI(TAG, "isUnread");
		Uri uri = Uri.parse(CONTENT_URI_SMS_INBOX);
		Cursor cursor = context.getContentResolver().query(uri, null,
				"body = ? AND read = '0'", new String[] { body }, null);
		cursor.moveToFirst();
		boolean unread = cursor.getCount() > 0;
		cursor.close();
		return unread;
	}

	public static void markAsRead(Context context, String number, String body) {
		LOGI(TAG, "markAsRead");
		Uri uri = Uri.parse(CONTENT_URI_SMS_INBOX);
		Cursor cursor = context.getContentResolver().query(uri, null, null,
				null, null);
		LOGI(TAG, "found " + cursor.getCount() + " unread messages");
		cursor.moveToFirst();
		if (!cursor.isAfterLast()) {
			do {
				String n = cursor.getString(cursor.getColumnIndex("address"));
				int read = cursor.getInt(cursor.getColumnIndex("read"));
				if (n.equals(number) && read == 0) {
					String b = cursor.getString(cursor.getColumnIndex("body"));
					if (b.startsWith(body)) {
						long id = cursor.getLong(cursor.getColumnIndex("_id"));
						ContentValues values = new ContentValues();
						values.put("read", true);
						context.getContentResolver().update(uri, values,
								"_id = ?", new String[] { String.valueOf(id) });
						LOGI(TAG, "Message marked as read");
					}
				}
			} while (cursor.moveToNext());
		}
		cursor.close();
	}

	public static void delete(Context context, int number, String body) {
		LOGI(TAG, "delete");
		Uri uri = Uri.parse(CONTENT_URI_SMS_INBOX);
		int deleted = context.getContentResolver().delete(uri,
				"body = ? AND read = '0'", new String[] { body });
		LOGI(TAG, deleted + " records deleted");
	}

}
