package de.stetro.tango.arnavigation.data;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import de.stetro.tango.arnavigation.R;
import de.stetro.tango.arnavigation.data.persistence.PoiDAO;

/**
 * Created by felix on 20/03/17.
 */

public class PoiAdapter extends RecyclerView.Adapter<PoiAdapter.ViewHolder> {

	List<PoiDAO> elements;
	private OnPoiSelectedListener listener;

	public void update(List<PoiDAO> elements) {
		this.elements = elements;
		this.notifyDataSetChanged();
	}

	public interface OnPoiSelectedListener{
		public void onPoiSelected(PoiDAO poi);
	}

	public PoiAdapter(List<PoiDAO> elements,OnPoiSelectedListener listener){
		this.elements = elements;
		this.listener = listener;
	}

	@Override
	public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.poi_listitem_layout, parent, false);
		return new ViewHolder(view);
	}

	@Override
	public void onBindViewHolder(ViewHolder holder, int position) {
		PoiDAO poi = elements.get(position);

		holder.text1.setText(poi.getName());
		holder.text2.setText(poi.getDescription());
	}

	@Override
	public int getItemCount() {
		return elements.size();
	}

	public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

		@BindView(R.id.text1) TextView text1;
		@BindView(R.id.text2) TextView text2;

		public ViewHolder(View itemView) {
			super(itemView);
			itemView.setOnClickListener(this);
			ButterKnife.bind(this,itemView);
		}

		@Override
		public void onClick(View view) {
			PoiDAO poi = elements.get(getAdapterPosition());
			if(listener != null){
				listener.onPoiSelected(poi);
			}
		}
	}
}