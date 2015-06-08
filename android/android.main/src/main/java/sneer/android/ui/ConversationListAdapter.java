package sneer.android.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

import sneer.main.R;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static sneer.android.ui.SneerActivity.findView;
import static sneer.convos.Convos.Summary;

class ConversationListAdapter extends ArrayAdapter<Summary> {

	private final LayoutInflater inflater;

	public ConversationListAdapter(Activity activity) {
        super(activity, R.layout.list_item_main);
		inflater = activity.getLayoutInflater();
    }

	void update(List<Summary> summaries) {
		clear();
		addAll(summaries);
		notifyDataSetChanged();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View view = convertView == null
			? inflateConversationView(parent)
			: convertView;

		updateConversation(position, view);
		return view;
	}

	private View inflateConversationView(ViewGroup parent) {
		return inflater.inflate(R.layout.list_item_main, parent, false);
	}

	private void updateConversation(int position, View view) {
		Summary summary = getItem(position);

//		ImageView pic     = findView(view, R.id.conversationPicture);
		TextView  nickname   = findView(view, R.id.conversationNickname);
		TextView  date    = findView(view, R.id.conversationDate);
		TextView  unread  = findView(view, R.id.conversationUnread);
		TextView  preview = findView(view, R.id.conversationSummary);
		preview.getPaint().setShader(textShader);

		nickname.setText(summary.nickname);
		preview.setText(summary.textPreview);
		date   .setText(summary.date);
		unread .setText(summary.unread);
		unread.setVisibility(summary.unread.isEmpty() ? GONE : VISIBLE);
	}

	private final Shader textShader = new LinearGradient(200, 0, 650, 0,
			new int[] {Color.DKGRAY, Color.LTGRAY},
			new float[] {0, 1}, Shader.TileMode.CLAMP);

}
