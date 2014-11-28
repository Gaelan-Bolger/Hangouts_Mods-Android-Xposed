package sms.xposed;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceFragment;
import android.widget.Toast;

public class MainActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState == null)
			getFragmentManager().beginTransaction()
					.replace(android.R.id.content, new PrefsFragment())
					.commit();
	}

	public static class PrefsFragment extends PreferenceFragment implements
			SharedPreferences.OnSharedPreferenceChangeListener {

		private boolean mToastRebootOnChangeHasBeenShow = false;

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			getPreferenceManager()
					.setSharedPreferencesMode(MODE_WORLD_READABLE);
			addPreferencesFromResource(R.xml.preferences);

			PackageManager packageManager = getActivity().getPackageManager();
			if (!packageManager
					.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
				CheckBoxPreference flashPref = (CheckBoxPreference) findPreference("flash_flashlight");
				flashPref.setChecked(false);
				flashPref.setEnabled(false);
				AlertDialog.Builder builder = new AlertDialog.Builder(
						getActivity());
				builder.setIcon(android.R.drawable.ic_dialog_alert)
						.setTitle("Unsupported device")
						.setMessage(
								"Your device does not have a camera flash.\nSome features have been disabled.")
						.setPositiveButton(android.R.string.ok,
								new DialogInterface.OnClickListener() {

									@Override
									public void onClick(DialogInterface dialog,
											int which) {
										dialog.dismiss();
									}
								});
				builder.show();
			}

		}

		@Override
		public void onSharedPreferenceChanged(
				SharedPreferences sharedPreferences, String key) {
			if ((key.equals("wake_on_new_sms") || key
					.equals("flash_flashlight"))
					&& mToastRebootOnChangeHasBeenShow == false) {
				Toast.makeText(getActivity().getApplicationContext(),
						getString(R.string.changes_apply_on_reboot),
						Toast.LENGTH_SHORT).show();
				mToastRebootOnChangeHasBeenShow = true;
			}
		}

		@Override
		public void onResume() {
			super.onResume();
			getPreferenceManager().getSharedPreferences()
					.registerOnSharedPreferenceChangeListener(this);

		}

		@Override
		public void onPause() {
			getPreferenceManager().getSharedPreferences()
					.unregisterOnSharedPreferenceChangeListener(this);
			super.onPause();
		}
	}

}
