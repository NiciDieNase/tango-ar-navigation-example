package de.stetro.tango.arnavigation.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
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

    private final int numScenarios = 20;
    private  final String[] description = {
            "path only",
            "path2 and loadingspinner",
            "path2 and motivation",
            "coins and motivation",
            "everything"
    };

    private Context mContext;
    private long environmentID;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView title;
        public TextView subtitle;
        public CardView card;

        public ViewHolder(View itemView) {
            super(itemView);
            title = (TextView) itemView.findViewById(R.id.scenario_title);
            subtitle = (TextView) itemView.findViewById(R.id.scenario_subtitle);
            card = (CardView) itemView.findViewById(R.id.card_view);
        }
    }

    public ScenariosAdaper(Activity context, long environmentID){
        this.mContext = context;
        this.environmentID = environmentID;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.scenario_item_layout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        ((ViewHolder) holder).title.setText("Scenario " + (position));
        ((ViewHolder) holder).subtitle.setText(
                position < description.length
                        ? description[position]
                        : "<no description>");
        ((ViewHolder) holder).card.setOnClickListener(new ScenarioClickListener(position));

    }

    @Override
    public int getItemCount() {
        return description.length;
    }

    private class ScenarioClickListener implements View.OnClickListener {
        private int position;

        public ScenarioClickListener(int position) {
            this.position = position;
        }

        @Override
        public void onClick(View view) {
            Intent i = getIntent();
            switch (position){
                case 0:
                    i.putExtra(ScenarioSelectActivity.KEY_ENABLED_DEFAULT, false);
                    i.putExtra(ScenarioSelectActivity.KEY_PATH_ENABLED, true);
                    mContext.startActivity(i);
                    break;
                case 1:
                    i.putExtra(ScenarioSelectActivity.KEY_ENABLED_DEFAULT, false);
                    i.putExtra(ScenarioSelectActivity.KEY_LOADINGSPINNER_ENABLED,true);
                    i.putExtra(ScenarioSelectActivity.KEY_PATH2_ENABLED,true);
                    mContext.startActivity(i);
                    break;
                case 2:
                    i.putExtra(ScenarioSelectActivity.KEY_ENABLED_DEFAULT, false);
                    i.putExtra(ScenarioSelectActivity.KEY_MOTIVATION_ENABELD,true);
                    i.putExtra(ScenarioSelectActivity.KEY_PATH2_ENABLED,true);
                    i.putExtra(ScenarioSelectActivity.KEY_MIN_DISTANCE,5.0);
                    mContext.startActivity(i);
                    break;
                case 3:
                    i.putExtra(ScenarioSelectActivity.KEY_ENABLED_DEFAULT, false);
                    i.putExtra(ScenarioSelectActivity.KEY_MOTIVATION_ENABELD,true);
                    i.putExtra(ScenarioSelectActivity.KEY_COINS_ENABLED,true);
                    i.putExtra(ScenarioSelectActivity.KEY_MIN_DISTANCE,5.0);
                    mContext.startActivity(i);
                    break;
                case 4:
                    i.putExtra(ScenarioSelectActivity.KEY_ENABLED_DEFAULT, true);
                    i.putExtra(ScenarioSelectActivity.KEY_MIN_DISTANCE, 2.0);
                    mContext.startActivity(i);
                    break;
                case 5:
                    i.putExtra(ScenarioSelectActivity.KEY_ENABLED_DEFAULT, false);
                    mContext.startActivity(i);
                    break;
                case 6:
                    i.putExtra(ScenarioSelectActivity.KEY_ENABLED_DEFAULT, true);
                    i.putExtra(ScenarioSelectActivity.KEY_LOADINGSPINNER_ENABLED, true);
                    i.putExtra(ScenarioSelectActivity.KEY_MOTIVATION_ENABELD, false);
                    i.putExtra(ScenarioSelectActivity.KEY_FLOORPLAN_ENABLED,false);
                    i.putExtra(ScenarioSelectActivity.KEY_DELAY_SEC,10l);
                    mContext.startActivity(i);
                    break;
                case 7:
                    i.putExtra(ScenarioSelectActivity.KEY_ENABLED_DEFAULT,false);
                    i.putExtra(ScenarioSelectActivity.KEY_COINS_ENABLED, true);
                    mContext.startActivity(i);
                    break;
                default:
                    Snackbar.make(view,"No Scenario defined",Snackbar.LENGTH_SHORT).show();
                    break;
            }
        }

        Intent getIntent(){
            Intent i = new Intent(mContext, ArActivity.class);
            i.putExtra(ScenarioSelectActivity.KEY_ENVIRONMENT_ID,environmentID);
            return i;
        }
    }
}
