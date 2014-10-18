package diegomendes.sendpics;

import java.util.HashMap;

import sneer.android.ui.MessageActivity;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;

public class ReceivePicsActivity extends MessageActivity {

	ImageView image;
	private byte[] ret;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_receive_pics);

		image = (ImageView) findViewById(R.id.picture_received);

		extractImage(messagePayload());

		//recordImage("temp.jpg");
		addImageToGallery(getApplicationContext().getFilesDir().getAbsolutePath(), getApplicationContext(), toBitmap(ret));
		
	}

/*	
	private void recordImage(String filename) {
		try {
			File file = new File(new File(Environment.getExternalStorageDirectory(), filename).getAbsolutePath());
			BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
			bos.write(ret);
			bos.flush();
			bos.close();	
			image.setImageBitmap((Bitmap) toBitmap(ret));
			
		} catch (Exception e) {
		  e.printStackTrace();
		}
	}
*/
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.receive_pics, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	
	public void addImageToGallery(final String filePath, final Context context, Bitmap yourBitmap) {
		
	    ContentValues values = new ContentValues();
	    
	    values.put(Images.Media.DATE_TAKEN, System.currentTimeMillis());
	    values.put(Images.Media.MIME_TYPE, "image/jpeg");
	    values.put(MediaStore.MediaColumns.DATA, filePath);

	    
	    context.getContentResolver().insert(Images.Media.EXTERNAL_CONTENT_URI, values);
	    
	    MediaStore.Images.Media.insertImage(context.getContentResolver(), yourBitmap, "Sendpics" , "");
	    
	    image.setImageBitmap(yourBitmap);
	    
	}
	
	private void extractImage(Object message) {
		@SuppressWarnings("unchecked")
		HashMap<String, Object> map = (HashMap<String, Object>) message;

			
		ret = (byte[]) map.get("pics");
		image.setImageBitmap((Bitmap) toBitmap(ret));

	}

	

	
}
