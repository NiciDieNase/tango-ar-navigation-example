package de.stetro.tango.arnavigation.ui;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import de.stetro.tango.arnavigation.data.persistence.EnvironmentDAO;

/**
 * Created by felix on 10/03/17.
 */

public class SelectEnvironmentFragment extends DialogFragment {

	private EnvironmentSelectionListener listener;

	public interface EnvironmentSelectionListener{
		void onEnvironmentSelected(EnvironmentDAO environment);
	}

	public SelectEnvironmentFragment setEnvironmentSelectionListener(EnvironmentSelectionListener listener){
		this.listener = listener;
		return this;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Iterator<EnvironmentDAO> all = EnvironmentDAO.findAll(EnvironmentDAO.class);
		if(!all.hasNext()){
			this.dismiss();
		}
		final List<EnvironmentDAO> items = new LinkedList<>();
		List<String> strings = new LinkedList<>();
		while(all.hasNext()){
			EnvironmentDAO next = all.next();
			items.add(next);
			strings.add(next.getADFUUID());
		}
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle("Select environment")
				.setItems(strings.toArray(new CharSequence[strings.size()]), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if(listener != null){
							listener.onEnvironmentSelected(items.get(which));
						}
					}
				});
		return builder.create();
	}
}
