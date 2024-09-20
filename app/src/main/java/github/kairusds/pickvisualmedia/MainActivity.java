package github.kairusds.pickvisualmedia;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.ext.SdkExtensions;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia;
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior;

import github.kairusds.pickvisualmedia.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity{

	private ActivityMainBinding binding;
	private SharedPreferences preferences;

	private ActivityResultLauncher<PickVisualMediaRequest> pickSingleMedia =
		registerForActivityResult(new PickVisualMedia(), uri -> {
			if(uri != null){
				addMediaToView(uri);
				if(getBoolPref("persistFiles")){
					int flag = Intent.FLAG_GRANT_READ_URI_PERMISSION;
					getContentResolver().takePersistableUriPermission(uri, flag);
				}
			}
		});

	// blame google for fucking up lifecycle management for ActivityResultLauncher
	private int maxMediaLimit = ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
		&& SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 2) ||
		Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ? MediaStore.getPickImagesMaxLimit() : 5;
	private ActivityResultLauncher<PickVisualMediaRequest> pickMultipleMedia =
		registerForActivityResult(new PickMultipleVisualMedia(maxMediaLimit), uris -> {
			if(!uris.isEmpty()){
				for(Uri uri : uris){
					addMediaToView(uri);
					if(getBoolPref("persistFiles")){
						int flag = Intent.FLAG_GRANT_READ_URI_PERMISSION;
						getContentResolver().takePersistableUriPermission(uri, flag);
					}
				}
			}
		});

	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);

		preferences = PreferenceManager.getDefaultSharedPreferences(this);
		binding = ActivityMainBinding.inflate(getLayoutInflater());
		setContentView(binding.getRoot());
		setSupportActionBar(binding.toolbar);

		/* if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
			requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
		} */
		binding.fab.setOnClickListener((View view) -> {
			try{
				startPicker();
			}catch(Exception e){
				showDialog(e.getMessage());
			}
		});
	}

	private void addMediaToView(Uri uri){
		try{
			var mediaContainer = binding.mediaContainer;

			var mimetype = getContentResolver().getType(uri);
			if(mimetype == null){
				showDialog("Cannot get media");
				return;
			}

			View mediaView;
			MediaPlayer mediaPlayer = new MediaPlayer();
			if(mimetype.startsWith("video/")){
				SurfaceView surfaceView = new SurfaceView(this);
				mediaView = surfaceView;
				mediaPlayer.setDataSource(this, uri);
	
				SurfaceHolder surfaceHolder = surfaceView.getHolder();
				final MediaPlayer finalMediaPlayer = mediaPlayer;
				surfaceHolder.addCallback(new SurfaceHolder.Callback(){
					@Override
					public void surfaceCreated(SurfaceHolder holder){
						finalMediaPlayer.setDisplay(holder);
					}
	
					@Override
					public void surfaceChanged(SurfaceHolder holder, int format, int width, int height){}
	
					@Override
					public void surfaceDestroyed(SurfaceHolder holder) {}
				});

				mediaPlayer.setOnPreparedListener(mp -> {
					int videoWidth = mp.getVideoWidth();
					int videoHeight = mp.getVideoHeight();
					if(videoWidth > 0 && videoHeight > 0){
						int screenWidth = getResources().getDisplayMetrics().widthPixels;
						int screenHeight = getResources().getDisplayMetrics().heightPixels;
						float aspectRatio = (float) videoHeight / videoWidth;
						int newWidth = screenWidth;
						int newHeight = (int) (newWidth * aspectRatio);
						if(newHeight > screenHeight){
							newHeight = screenHeight;
							newWidth = (int) (newHeight / aspectRatio);
						}
						ViewGroup.LayoutParams layoutParams = surfaceView.getLayoutParams();
						layoutParams.width = newWidth;
						layoutParams.height = newHeight;
						surfaceView.setLayoutParams(layoutParams);
					}
				});

				mediaPlayer.setLooping(true);
				mediaPlayer.prepareAsync();
			}else if(mimetype.startsWith("image/")){
				mediaPlayer = null;
				mediaView = new ImageView(this);
				((ImageView) mediaView).setImageURI(uri);
				((ImageView) mediaView).setAdjustViewBounds(true);
				mediaView.setLayoutParams(new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT));
			}else{
				mediaPlayer = null;
				showDialog("Unknown media type");
				return;
			}
			mediaContainer.addView(mediaView);

			var controlButton = new MaterialButton(this);
			final MaterialButton finalControlButton = controlButton;
			if(mimetype.startsWith("video/")){
				controlButton.setLayoutParams(new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.WRAP_CONTENT));
				controlButton.setText("Play");

				mediaContainer.addView(controlButton);

				final MediaPlayer finalMediaPlayer = mediaPlayer;
				controlButton.setOnClickListener(v -> {
					if (finalMediaPlayer.isPlaying()) {
						finalMediaPlayer.pause();
						finalControlButton.setText("Play");
					} else {
						finalMediaPlayer.start();
						finalControlButton.setText("Pause");
					}
				});
			}else{
				controlButton = null;
			}

			var removeButton = new MaterialButton(this);
			removeButton.setLayoutParams(new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT));
			removeButton.setText("Remove");
			mediaContainer.addView(removeButton);

			final MediaPlayer finalMediaPlayer = mediaPlayer;
			removeButton.setOnClickListener(v -> {
				if(finalMediaPlayer != null){
					finalMediaPlayer.release();
					mediaContainer.removeView(finalControlButton);
				}
				mediaContainer.removeView(mediaView);
				mediaContainer.removeView(removeButton);
				showFab();
			});
		}catch(Exception e){
			showDialog(e.getMessage());
		}
	}

	private void showFab(){
		var params = (CoordinatorLayout.LayoutParams) binding.fab.getLayoutParams();
		var behavior = (HideBottomViewOnScrollBehavior) params.getBehavior();
		if(behavior.isScrolledDown()){
			behavior.slideUp(binding.fab);
		}
	}

	@SuppressLint("RestrictedApi")
	private void showDialog(String msg){
		var text = new AppCompatTextView(this);
		text.setText(msg);
		text.setTextSize(20.0f);
		text.setTextIsSelectable(true);

		new AlertDialog.Builder(this)
			.setView(text, 16, 16, 16, 16)
			.setPositiveButton("Close", (dialog, id) -> dialog.dismiss())
			.create()
			.show();
	}

	private boolean getBoolPref(String preferenceName){
		return preferences.getBoolean(preferenceName, false);
	}

	private void startPicker(){
		ActivityResultLauncher<PickVisualMediaRequest> pickMedia = pickSingleMedia;
		if(getBoolPref("pickMultiple")){
			pickMedia = pickMultipleMedia;
		}

		if((!getBoolPref("imagesOnly") && !getBoolPref("videosOnly")) ||
			(getBoolPref("imagesOnly") && getBoolPref("videosOnly"))){
			pickMedia.launch(new PickVisualMediaRequest.Builder()
				.setMediaType(PickVisualMedia.ImageAndVideo.INSTANCE)
				.build());
		}else if(getBoolPref("imagesOnly") && !getBoolPref("videosOnly")){
			pickMedia.launch(new PickVisualMediaRequest.Builder()
				.setMediaType(PickVisualMedia.ImageOnly.INSTANCE)
				.build());
		}else if(getBoolPref("videosOnly") && !getBoolPref("imagesOnly")){
			pickMedia.launch(new PickVisualMediaRequest.Builder()
				.setMediaType(PickVisualMedia.VideoOnly.INSTANCE)
				.build());
		}
		// will add custom mimetypes if i feel like it
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu){
		getMenuInflater().inflate(R.menu.menu_main, menu);
		// i am NOT going to copypaste a single-line code for every setting
		var settingsIds = new int[]{
			R.id.settings_pick_multiple,
			R.id.settings_images_only,
			R.id.settings_videos_only,
			R.id.settings_persist_files
		};
		var settingsValues = new String[]{
			"pickMultiple", "imagesOnly", "videosOnly", "persistFiles"
		};
		for(var i = 0; i < settingsIds.length; i++){
			menu.findItem(settingsIds[i]).setChecked(getBoolPref(settingsValues[i]));
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		if(item.isCheckable()){
			if(item.isChecked()) item.setChecked(false);
			else item.setChecked(true);
		}
		int id = item.getItemId();

		if(id == R.id.menu_clear_all){
			var mediaContainer = binding.mediaContainer;
			for(int i = mediaContainer.getChildCount() - 1; i >= 0; i--){
				View child = mediaContainer.getChildAt(i);
				if (child instanceof MaterialButton){
					MaterialButton button = (MaterialButton) child;
					if("Remove".equals(button.getText().toString())){
						button.performClick();
					}
				}
			}
			showFab();
			return true;
		}else if(id == R.id.settings_pick_multiple){
			preferences.edit().putBoolean("pickMultiple", item.isChecked()).apply();
			return true;
		}else if(id == R.id.settings_images_only){
			preferences.edit().putBoolean("imagesOnly", item.isChecked()).apply();
			return true;
		}else if(id == R.id.settings_videos_only){
			preferences.edit().putBoolean("videosOnly", item.isChecked()).apply();
			return true;
		}else if(id == R.id.settings_persist_files){
			preferences.edit().putBoolean("persistFiles", item.isChecked()).apply();
			return true;
		}else{
			return super.onOptionsItemSelected(item);
		}
	}

}