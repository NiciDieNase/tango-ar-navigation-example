package de.stetro.tango.arnavigation.ui;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import butterknife.BindView;
import butterknife.ButterKnife;
import de.stetro.tango.arnavigation.R;

/**
 * Created by felix on 17/03/17.
 */

public class SaveDialogFragment extends DialogFragment {

	private OnSaveListener listener;
	@BindView(R.id.et_name) EditText etName;
	@BindView(R.id.et_description) EditText etDescription;

	interface OnSaveListener{
		void onSave(String title, String description);
	}

	public SaveDialogFragment setListener(OnSaveListener listener){
		this.listener = listener;
		return this;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		LayoutInflater inflater = getActivity().getLayoutInflater();
		final View layout = inflater.inflate(R.layout.savedialog_layout, null);
		ButterKnife.bind(layout);
		builder.setView(layout);
		builder.setTitle("Save");

		builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				String title = etName.getText().toString();
				String description = etDescription.getText().toString();
				if(listener != null){
					listener.onSave(title,description);
				}
			}
		});
		builder.setCancelable(true);

		return builder.create();
	}
}
