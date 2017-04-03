package de.stetro.tango.arnavigation.ui;

import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import de.stetro.tango.arnavigation.R;

/**
 * Created by felix on 03/04/17.
 */

public class LoadingDialogFragment extends DialogFragment {

	@BindView(R.id.textView)
	TextView messageView;
	private String message;

	public void setMessage(String message){
		this.message = message;
		if(messageView != null){
			messageView.setText(message);
		}
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		LayoutInflater inflater = getActivity().getLayoutInflater();
		final View layout = inflater.inflate(R.layout.loading_dialog, null);
		ButterKnife.bind(this,layout);
		if(message != null){
			messageView.setText(message);
		}
		builder.setView(layout);
		return builder.create();
	}
}
