package sms.xposed.activity;

import static sms.xposed.util.LogUtils.LOGD;
import static sms.xposed.util.LogUtils.makeLogTag;
import sms.xposed.R;
import sms.xposed.SMSXposed;
import sms.xposed.util.ContactUtils;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

public class ReplyActionActivity extends Activity {

	private static final String TAG = makeLogTag(ReplyActionActivity.class);

	private String mNumber;
	private String mMessage;
	private EditText mEditText;

	private AlertDialog mDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		LOGD(TAG, "[onCreate]");
		String contentTitle = getIntent().getStringExtra(
				SMSXposed.EXTRA_CONTENT_TITLE);
		try {
			Integer.parseInt(contentTitle.replace("+", ""));
			mNumber = contentTitle;
		} catch (NumberFormatException e) {
			mNumber = ContactUtils.getContactNumber(this, contentTitle);
		}

		View layout = View.inflate(this, R.layout.activity_reply_action, null);
		TextView titleText = (TextView) layout.findViewById(android.R.id.title);
		titleText.setText(contentTitle);
		mEditText = (EditText) layout.findViewById(android.R.id.message);
		FrameLayout cancel = (FrameLayout) layout
				.findViewById(android.R.id.button1);
		FrameLayout send = (FrameLayout) layout
				.findViewById(android.R.id.button2);
		OnClickListener buttonListener = new OnClickListener() {

			@Override
			public void onClick(View v) {
				switch (v.getId()) {
				case android.R.id.button1:
					mDialog.dismiss();
					finish();
					break;
				case android.R.id.button2:
					mEditText.setError(null);
					mMessage = mEditText.getText().toString();
					if (mMessage.length() == 0) {
						mEditText.setError("Required");
						return;
					}
					mDialog.dismiss();
					sendAndFinish();
					break;

				default:
					break;
				}

			}
		};
		cancel.setOnClickListener(buttonListener);
		send.setOnClickListener(buttonListener);

		mDialog = new AlertDialog.Builder(this).setCancelable(true)
				.setView(layout).create();
		mDialog.setCanceledOnTouchOutside(true);
		mDialog.show();
	}

	private void sendAndFinish() {
		SmsManager smsManager = SmsManager.getDefault();
		smsManager.sendTextMessage(mNumber, null, mMessage, null, null);
		finish();
	}

}
