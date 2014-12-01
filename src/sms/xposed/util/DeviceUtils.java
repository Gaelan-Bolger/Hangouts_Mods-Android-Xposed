package sms.xposed.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;

public class DeviceUtils {

	public static void hideStatusBar(Context context) {
		context = context.getApplicationContext();
		Object sbservice = context.getSystemService("statusbar");
		Class<?> statusbarManager = null;
		try {
			statusbarManager = Class.forName("android.app.StatusBarManager");
			Method hidesb;
			if ((Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN_MR1))
				hidesb = statusbarManager.getMethod("collapse");
			else
				hidesb = statusbarManager.getMethod("collapsePanels");
			hidesb.invoke(sbservice);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
	}

	public static int dp(int i) {
		return (int) (Resources.getSystem().getDisplayMetrics().density * i);
	}
}
