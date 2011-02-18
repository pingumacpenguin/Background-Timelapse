/****************************************************************\
*                Android Background Timelapse                    *
* Copyright (c) 2009-10 by Florian Echtler <floe@butterbrot.org> *
*  Licensed under GNU General Public License (GPL) 3 or later    *
\****************************************************************/

package floe.timelapse;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.view.SurfaceView;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Looper;
import android.widget.Toast;
import java.util.TimerTask;
import java.util.Timer;
import java.lang.Thread;
import java.lang.String;
import android.hardware.Camera;
import android.graphics.PixelFormat;
import java.io.File;
import java.io.FileOutputStream;
import android.util.Log;
import android.text.format.Time;


public class TimelapseService extends Service {

	private NotificationManager mNM;
	private Notification notification;
	private PendingIntent contentIntent;

	private final String TAG = "TimelapseService";

	private int counter;
	private Camera cam;
	private String outdir;

	private Timer timer = null;
	private TimelapseTask task = null;

	// preview callback with actual image data
	private Camera.PreviewCallback imageCallback = new Camera.PreviewCallback() {

		@Override public void onPreviewFrame( byte[] _data, Camera _camera ) {

			Log.v( TAG, "::imageCallback: picture retrieved, storing.." );
			//String myname = outdir.concat("img").concat(String.valueOf(counter++)).concat(".yuv");
			String myname = outdir.concat("img").concat(String.format("%06d",counter++)).concat(".yuv");

			// store JPEG data
			try {

				FileOutputStream outfile = new FileOutputStream( myname );
				outfile.write( _data );
				outfile.close();

				CharSequence text = "Images: ".concat(String.format("%d",counter));
				updateNotification( text );

				Log.v( TAG, "::imageCallback: picture stored successfully as " + myname );

			} catch (Exception e) {
				Log.e( TAG, "::imageCallback: ", e );
			}
		}
	};

	/*private Camera.AutoFocusCallback afCallback = new Camera.AutoFocusCallback() {
		@Override
		public void onAutoFocus( boolean success, Camera camera ) {
			if (success) {
				Log.v( TAG, "autofocus done, taking picture" );
				cam.takePicture( null, null, imageCallback );
			} else {
				Log.v( TAG, "autofocus failed" );
			}
		}
	};*/


	// Timer task for continuous triggering of preview callbacks
	private class TimelapseTask extends TimerTask {
		@Override public void run() {
			//Log.v( TAG, "starting autofocus" );
			//cam.autoFocus( afCallback );
			Log.v( TAG, "taking picture" );
			//cam.takePicture( null, null, imageCallback );
			cam.setOneShotPreviewCallback( imageCallback );
		}
	}


	// Binder class for activity <-> service interface
	public class TimelapseBinder extends Binder {
		TimelapseService getService() {
			return TimelapseService.this;
		}
	}
	
	private final IBinder mybinder = new TimelapseBinder();

	@Override public IBinder onBind( Intent intent ) {
		return mybinder;
	}


	// called when service gets created
	@Override public void onCreate() {

		mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

		try {
			cam = Camera.open();
			Toast.makeText(this, "Timelapse service loaded", Toast.LENGTH_SHORT).show();
		} catch (Exception e) {
			Log.e( TAG, "::onCreate: ", e );
			Toast.makeText(this, TAG + " error (camera problem?)", Toast.LENGTH_SHORT).show();
		}
	}

	// called when service quits
	@Override public void onDestroy() {

		// Tell the user we stopped.
		Toast.makeText(this, TAG + " stopped", Toast.LENGTH_SHORT).show();

		// cleanup everything
		cleanup();
		cam.release();
	}


	// after service has been started, this is called from the Activity to set the preview surface and launch the timer task
	public void launch( SurfaceView sv, int delay ) {

		try {

			if (timer != null) {
				Log.e( TAG, "::launch: already running." );
				return;
			}

			setupCamera( sv );
			setupOutdir();

			timer = new Timer();
			task = new TimelapseTask();
			timer.scheduleAtFixedRate( task, delay, delay );

			setupNotification();

		} catch (Exception e) {
			Log.e( TAG, "::launch: ", e );
			cleanup();
		}
	}


	// cleanup all resources
	public void cleanup() {

		// Cancel the persistent notification.
		mNM.cancel( R.drawable.camera_tiny );

		// stop the timer
		if (timer != null) {
			timer.cancel();
			timer = null;
			task = null;
		}

		// cleanup the camera
		cam.stopPreview();
	}


	// initialize camera
	private void setupCamera( SurfaceView sv ) {

		Log.v( TAG, "::setupCamera: " + sv.toString() );

		Camera.Parameters param = cam.getParameters();
		param.setPreviewFormat( PixelFormat.YCbCr_420_SP );
		param.setPreviewSize( 640, 480 );
		cam.setParameters( param );

		try {
			cam.setPreviewDisplay( sv.getHolder() );
			cam.startPreview();
		} catch (Exception e) {
			throw new RuntimeException( e.toString() );
		}
	}


	// initialize output directory
	private void setupOutdir() {

		Time now = new Time();
		now.set( System.currentTimeMillis() );

		outdir = "/sdcard/floe.timelapse/" + now.format("%Y%m%d-%H%M/");
		File tmp = new File(outdir);

		if ((tmp.isDirectory() == false) && (tmp.mkdirs() == false)) {
			Toast.makeText( this, "Error creating output directory - SD card not mounted?", Toast.LENGTH_SHORT ).show();
			throw new RuntimeException( "Error creating output directory." );
		}
	}


	// initialize the persistent notification
	public void setupNotification() {

		Toast.makeText(this, TAG + " started", Toast.LENGTH_SHORT).show();

		// Display a notification about us starting.  We put an icon in the status bar.
		notification = new Notification( R.drawable.camera_tiny, TAG + " started", System.currentTimeMillis() );
		notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_ONLY_ALERT_ONCE;

		// The PendingIntent to launch our activity if the user selects this notification
		contentIntent = PendingIntent.getActivity(this, 0, new Intent(TimelapseService.this, Timelapse.class), 0);

		updateNotification( "Images: 0" );
	}

	// update persistent notification
	private void updateNotification( CharSequence text ) {

		// Set the info for the views that show in the notification panel.
		notification.setLatestEventInfo( TimelapseService.this, "Timelapse Image Service", text, contentIntent );

		// Send the notification.
		// We use a layout id because it is a unique number.  We use it later to cancel.
		mNM.notify( R.drawable.camera_tiny, notification );
	}
}

