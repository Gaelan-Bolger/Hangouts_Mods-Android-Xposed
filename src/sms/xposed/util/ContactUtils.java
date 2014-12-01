package sms.xposed.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.TextUtils;

public class ContactUtils {

	public static String getContactNumber(Context context, String name) {
		String number = null;
		ContentResolver cr = context.getContentResolver();
		Cursor cursor = cr.query(ContactsContract.Contacts.CONTENT_URI, null,
				ContactsContract.Contacts.DISPLAY_NAME + "= ?",
				new String[] { name }, null);
		cursor.moveToFirst();
		if (!cursor.isAfterLast()) {
			do {
				String contactId = cursor.getString(cursor
						.getColumnIndex(ContactsContract.Contacts._ID));
				Cursor phones = cr.query(Phone.CONTENT_URI, null,
						Phone.CONTACT_ID + " = " + contactId, null, null);
				phones.moveToFirst();
				if (!phones.isAfterLast()) {
					do {
						int type = phones.getInt(phones
								.getColumnIndex(Phone.TYPE));
						if (type == Phone.TYPE_MOBILE) {
							number = phones.getString(phones
									.getColumnIndex(Phone.NUMBER));
							continue;
						}
					} while (phones.moveToNext());
				}
				phones.close();
			} while (cursor.moveToNext());
		}
		cursor.close();
		return number;
	}

}
