package sms.xposed.service;

import static sms.xposed.util.LogUtils.LOGD;
import static sms.xposed.util.LogUtils.makeLogTag;
import sms.xposed.receiver.ScreenStateReceiver;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;

public class FlashFlashlightService extends Service {

	private static final String TAG = makeLogTag(FlashFlashlightService.class);

	private static int FLASH_INTERVAL = 2500;
	private static int FLASH_DURATION = 300;
	private static int FLASH_REPETITIONS = 10;

	private SharedPreferences prefs;
	private Handler mHandler = new Handler();
	private Camera mCamera;
	private Parameters mParameters;
	private int mFlashCount;
	private ScreenStateReceiver screenReceiver;
	private boolean previewOn = false;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		LOGD(TAG, "onCreate");
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		openCamera();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		LOGD(TAG, "onStartCommand");
		boolean flashScreenOn = prefs.getBoolean("flash_screen_on", false);
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		if (!pm.isScreenOn() || flashScreenOn) {
			FLASH_INTERVAL = Integer.parseInt(prefs.getString("flash_interval",
					String.valueOf(FLASH_INTERVAL)));
			FLASH_DURATION = Integer.parseInt(prefs.getString("flash_duration",
					String.valueOf(FLASH_DURATION)));
			FLASH_REPETITIONS = Integer.parseInt(prefs.getString(
					"flash_repetitions", String.valueOf(FLASH_REPETITIONS)));
			if (prefs.getBoolean("flash_fast_cancel", true)) {
				screenReceiver = new ScreenStateReceiver();
				screenReceiver.register(this);
			}
			mFlashCount = 0;
			flashOn();
		} else {
			releaseCamera();
			stopSelf();
		}
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		LOGD(TAG, "onDestroy");
		releaseCamera();
		if (null != screenReceiver) {
			screenReceiver.unregister(this);
		}
	}

	private void flashOn() {
		if (null != mCamera) {
			mParameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
			mCamera.setParameters(mParameters);
			mHandler.postDelayed(new Runnable() {

				@Override
				public void run() {
					flashOff();
				}
			}, FLASH_DURATION);
		}
	}

	private void flashOff() {
		if (null != mCamera) {
			mParameters.setFlashMode(Parameters.FLASH_MODE_OFF);
			mCamera.setParameters(mParameters);
			mFlashCount++;
			if (mFlashCount < FLASH_REPETITIONS) {
				mHandler.postDelayed(new Runnable() {

					@Override
					public void run() {
						flashOn();
					}
				}, FLASH_INTERVAL);
			} else {
				releaseCamera();
				stopSelf();
			}
		}
	}

	private void openCamera() {
		if (null == mCamera) {
			mCamera = Camera.open();
			mParameters = mCamera.getParameters();
			startPreview();
			LOGD(TAG, "Camera created");
		}
	}

	private void releaseCamera() {
		if (null != mCamera) {
			stopPreview();
			mCamera.release();
			mCamera = null;
			mParameters = null;
			LOGD(TAG, "Camera released");
		}
	}

	private void startPreview() {
		if (!previewOn && mCamera != null) {
			mCamera.startPreview();
			previewOn = true;
		}
	}

	private void stopPreview() {
		if (previewOn && mCamera != null) {
			mCamera.stopPreview();
			previewOn = false;
		}
	}

}
