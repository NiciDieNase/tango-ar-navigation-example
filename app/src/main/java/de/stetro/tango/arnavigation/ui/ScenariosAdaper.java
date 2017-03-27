package de.stetro.tango.arnavigation.ui;

import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import de.stetro.tango.arnavigation.R;

/**
 * Created by felix on 27/03/17.
 */
class ScenariosAdaper extends RecyclerView.Adapter {

    private static final String TAG = ScenariosAdaper.class.getSimpleName();

    private final int numScenarios = 10;
    private final String[] scenarios = {"Scenario 1", "Scenario 2", "Scenario 3",
            "Scenario 4", "Scenario 5"};

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView title;
        public TextView subtitle;

        public ViewHolder(View itemView) {
            super(itemView);
            Log.d(TAG,"Creating View Holder");
            title = (TextView) itemView.findViewById(R.id.scenario_title);
            subtitle = (TextView) itemView.findViewById(R.id.scenario_subtitle);
        }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.scenario_item_layout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        Log.d(TAG,"Binding item to view");
        ((ViewHolder) holder).title.setText("Scenario " + (position+1));
        ((ViewHolder) holder).subtitle.setText("<your description here>");
        holder.itemView.setOnClickListener(new ScenarioClickListener(position));

    }

    @Override
    public int getItemCount() {
        return numScenarios;
    }

    private class ScenarioClickListener implements View.OnClickListener {
        private int position;

        public ScenarioClickListener(int position) {
            this.position = position;
        }

        @Override
        public void onClick(View view) {
            switch (position){
                case 1:
                    // TODO start Scenarios
                    break;
            }
        }
    }
}
