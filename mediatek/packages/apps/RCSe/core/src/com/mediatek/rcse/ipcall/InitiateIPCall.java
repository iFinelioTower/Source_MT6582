package com.mediatek.rcse.ipcall;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Spinner;

import com.orangelabs.rcs.provider.settings.RcsSettings;
import com.orangelabs.rcs.R;
import com.orangelabs.rcs.service.api.client.ClientApiListener;
import com.orangelabs.rcs.service.api.client.ImsEventListener;
import com.orangelabs.rcs.service.api.client.ipcall.IPCallApi;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Initiate an IP call
 */
public class InitiateIPCall extends Activity implements ClientApiListener, ImsEventListener {

	/**
	 * Logger
	 */
	private Logger logger = Logger.getLogger(InitiateIPCall.class.getName());
	
	/**
	 * UI handler
	 */
	private Handler handler = new Handler();
	
	/**
	 * refresh of sessions list when api is connected
	 */
	private Boolean getIncomingSessionWhenApiConnected = false ;
	
	/**
	 * audio call button
	 */
	private Button audioVideoInviteBtn;  
	
	/**
	 * audio+video call button
	 */
	private Button audioInviteBtn;
	
	/**
	 * contacts list spinner
	 */
	private Spinner spinner;
	
	public static ProgressDialog initCallProgressDialog ;
	
	
	 /* *****************************************
     *                Activity
     ***************************************** */
    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (logger.isActivated()) {
			logger.info("onCreate()");
		}
		// Set layout
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		setContentView(R.layout.ipcall_initiate_call);

		// Set title
		setTitle(R.string.menu_initiate_ipcall);

		// get Buttons listview and spinner
		audioVideoInviteBtn = (Button) findViewById(R.id.audio_video_invite_btn);
		audioInviteBtn = (Button) findViewById(R.id.audio_invite_btn);
		spinner = (Spinner) findViewById(R.id.contact);

		audioInviteBtn.setEnabled(false);
		audioVideoInviteBtn.setEnabled(false);

		RcsSettings.createInstance(getApplicationContext());

		// Set the contact selector
		spinner.setAdapter(Utils.createRcsContactListAdapter(this));

		// Set button listeners
		audioVideoInviteBtn.setOnClickListener(audioVideoInviteBtnListener);
		audioInviteBtn.setOnClickListener(audioInviteBtnListener);

		if (IPCallSessionsData.getInstance().isCallApiConnected) {
			// activate buttons at least on contact
			if (spinner.getAdapter().getCount() > 0) {
				audioVideoInviteBtn.setEnabled(true);
				audioInviteBtn.setEnabled(true);
			}

			// remove "old" listeners and set "new" listeners
			IPCallSessionsData.getInstance().callApi.removeAllApiEventListeners();
			IPCallSessionsData.getInstance().callApi
					.removeImsEventListener(IPCallSessionsData.getInstance().imsEventListener);
			IPCallSessionsData.getInstance().callApi.addApiEventListener(this);
			IPCallSessionsData.getInstance().callApiListener = this;
			IPCallSessionsData.getInstance().callApi.addImsEventListener(this);
			IPCallSessionsData.getInstance().imsEventListener = this;
		}
		else { // wait Api connection - activate buttons when connected			
			// Instantiate callApi if null 
			if (IPCallSessionsData.getInstance().callApi == null) {
				IPCallSessionsData.getInstance().callApi = new IPCallApi(getApplicationContext());
			} else { // callApi already exists remove "old" listeners
				IPCallSessionsData.getInstance().callApi.removeAllApiEventListeners();
				IPCallSessionsData.getInstance().callApi
						.removeImsEventListener(IPCallSessionsData.getInstance().imsEventListener);
			}
			
			// set listeners
			IPCallSessionsData.getInstance().callApi.addApiEventListener(this);
			IPCallSessionsData.getInstance().callApiListener = this;
			IPCallSessionsData.getInstance().callApi.addImsEventListener(this);
			IPCallSessionsData.getInstance().imsEventListener = this;

			// connect api
			IPCallSessionsData.getInstance().callApi.connectApi();
		}
		
		// if incoming session (intent generated by notif) => launch IPCallSessionActivity		
		if ((getIntent() != null) && (getIntent().getAction()!= null)&&(getIntent().getAction().equals("incoming"))) {
			if (IPCallSessionsData.getInstance().isCallApiConnected){
				Intent launchIntent = new Intent(getIntent());
				launchIntent.setClass(getApplicationContext(),
						IPCallView.class);
				getApplicationContext().startActivity(launchIntent);
				this.finish();
			}
			else{getIncomingSessionWhenApiConnected = true;}

		}
	}

   
    @Override
    public void onResume() {
    	super.onResume();
    	
    	if (logger.isActivated()) {
			logger.info("onResume()");
		}	
    }
    
    
    @Override
    public void onDestroy() {
    	super.onDestroy();   	
    	if (logger.isActivated()) {
			logger.info("onDestroy()");
		}
	
    }

    /**
     * Dial button listener
     */
    private OnClickListener audioInviteBtnListener = new OnClickListener() {
        public void onClick(View v) {
        	if (logger.isActivated()) {
    			logger.debug("audioInviteBtnListener - onClick()");
    		}
        	
        	initCallProgressDialog = Utils.showProgressDialog(InitiateIPCall.this, getString(R.string.label_command_in_progress));
        	// Get the remote contact
            Spinner spinner = (Spinner)findViewById(R.id.contact);
            MatrixCursor cursor = (MatrixCursor)spinner.getSelectedItem();
            final String remote = cursor.getString(1);
            
            getApplicationContext().startActivity(setIntentOutgoingSession(remote, false));
            InitiateIPCall.this.finish();
        }
    };

    /**
     * Invite button listener
     */
    private OnClickListener audioVideoInviteBtnListener = new OnClickListener() {
        public void onClick(View v) {
			//Utils.showMessage(InitiateIPCall.this, getString(R.string.label_not_implemented) + ": video may be added when voice call is established");
       	if (logger.isActivated()) {
    			logger.debug("audioVideoInviteBtnListener - onClick()");
    		}
       	
       		initCallProgressDialog = Utils.showProgressDialog(InitiateIPCall.this, getString(R.string.label_command_in_progress));
        	// Get the remote contact
            Spinner spinner = (Spinner)findViewById(R.id.contact);
            MatrixCursor cursor = (MatrixCursor)spinner.getSelectedItem();
            final String remote = cursor.getString(1);

            getApplicationContext().startActivity(setIntentOutgoingSession(remote, true));
            
            InitiateIPCall.this.finish();
        }
    };



	@Override
	public void handleApiConnected() {
		if (logger.isActivated()) {
			logger.debug("API, connected");
		}

		
		IPCallSessionsData.getInstance().isCallApiConnected = true;
		if (logger.isActivated()) {
			logger.debug("IPCallSessionData.isCallApiConnected set to "+IPCallSessionsData.getInstance().isCallApiConnected);
		}	
		
		// activate buttons if at least one contact
		handler.post(new Runnable() {
			public void run() {
				if (spinner.getAdapter().getCount() > 0) {
					audioVideoInviteBtn.setEnabled(true);
					audioInviteBtn.setEnabled(true);
				}
			}
		});

		
		if (getIncomingSessionWhenApiConnected) {
			Intent launchIntent = new Intent(getIntent());
			launchIntent.setClass(getApplicationContext(), IPCallView.class);
			getApplicationContext().startActivity(launchIntent);
			this.finish();
		}

	}

	@Override
	public void handleApiDisabled() {
		if (logger.isActivated()) {
			logger.debug("API, disabled");
		}
		IPCallSessionsData.getInstance().isCallApiConnected = false;

		String msg = InitiateIPCall.this.getString(R.string.label_api_disabled);

		// Api disabled
		Intent intent = new Intent(InitiateIPCall.this.getApplicationContext(),
				IPCallView.class);
		intent.setAction("ExitActivity");
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra("messages", msg);
		getApplicationContext().startActivity(intent);
	}

	@Override
	public void handleApiDisconnected() {
		if (logger.isActivated()) {
			logger.debug("API, disconnected");
		}
		IPCallSessionsData.getInstance().isCallApiConnected = false;
		
		String msg = InitiateIPCall.this.getString(R.string.label_api_disconnected);

		// Service has been disconnected
		Intent intent = new Intent(InitiateIPCall.this.getApplicationContext(), IPCallView.class);
		intent.setAction("ExitActivity");
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra("messages", msg);
		getApplicationContext().startActivity(intent);
	}
	
	@Override
	public void handleImsConnected() {
		if (logger.isActivated()) {
			logger.debug("IMS, connected");
		}
		// nothing to do
	}

	@Override
	public void handleImsDisconnected(int arg0) {
		if (logger.isActivated()) {
			logger.debug("IMS, disconnected");
		}
		
		String msg = InitiateIPCall.this.getString(R.string.label_ims_disconnected);
		
		// IMS has been disconnected
		Intent intent = new Intent(InitiateIPCall.this.getApplicationContext(), IPCallView.class);
		intent.setAction("ExitActivity");
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra("messages", msg);
		getApplicationContext().startActivity(intent);
	}
	
	
	private Intent  setIntentOutgoingSession(String remote, boolean video){
		// Initiate Intent to launch outgoing IP call
        Intent intent = new Intent(getApplicationContext(), IPCallView.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("contact", remote);
        intent.putExtra("video", video);
        intent.setAction("outgoing");
        
        return intent;
	}


	/**
	 * Add IP call notification
	 * 
	 * @param context
	 *            Context
	 * @param Intent
	 *            invitation
	 */
	public static void addIPCallInvitationNotification(Context context,	Intent invitation) {
		// Initialize settings
		RcsSettings.createInstance(context);

		// Create notification
		Intent intent = new Intent(invitation);
		intent.setClass(context, InitiateIPCall.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.setAction("incoming");
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
				intent, PendingIntent.FLAG_UPDATE_CURRENT);
		String notifTitle = context.getString(R.string.title_recv_ipcall);

		Notification notif = new Notification(R.drawable.ri_notif_ipcall_icon,
				notifTitle, System.currentTimeMillis());
		notif.setLatestEventInfo(
				context,
				notifTitle,
				context.getString(R.string.label_from) + " "
						+ Utils.formatCallerId(invitation), contentIntent);

		// Set ringtone
		String ringtone = RcsSettings.getInstance().getCShInvitationRingtone();
		if (!TextUtils.isEmpty(ringtone)) {
			notif.sound = Uri.parse(ringtone);
		}

		// Set vibration
		if (RcsSettings.getInstance().isPhoneVibrateForCShInvitation()) {
			notif.defaults |= Notification.DEFAULT_VIBRATE;
		}

		// Send notification
		String sessionId = invitation.getStringExtra("sessionId");
		NotificationManager notificationManager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(Utils.NOTIF_ID_IPCALL);
		notificationManager.notify(sessionId, Utils.NOTIF_ID_IPCALL, notif);
	}

	/**
	 * Remove IP call notification
	 * 
	 * @param context
	 *            Context
	 * @param sessionId
	 *            Session ID
	 */
	public static void removeIPCallNotification(Context context,
			String sessionId) {
		NotificationManager notificationManager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(sessionId, Utils.NOTIF_ID_IPCALL);
	}
}

	
