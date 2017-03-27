package de.stetro.tango.arnavigation.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.CardView;
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

    private final int numScenarios = 7;
    private  final String[] description =
            {"All enabled", "path disabled", "motivation disabled",
                    "floorplan disabled, 15s delay", "everything disabled" };

    private Context mContext;
    private long environmentID;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView title;
        public TextView subtitle;
        public CardView card;

        public ViewHolder(View itemView) {
            super(itemView);
            Log.d(TAG,"Creating View Holder");
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
        Log.d(TAG,"Binding item to view");
        ((ViewHolder) holder).title.setText("Scenario " + (position+1));
        ((ViewHolder) holder).subtitle.setText(
                position < description.length
                        ? description[position]
                        : description[description.length-1]);
        ((ViewHolder) holder).card.setOnClickListener(new ScenarioClickListener(position));

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
            Log.d(TAG, "OnClick");
            switch (position){
                case 0:
                    mContext.startActivity(getIntent(environmentID,true,true,true,0));
                    break;
                case 1:
                    mContext.startActivity(getIntent(environmentID,false,true,true,0));
                    break;
                case 2:
                    mContext.startActivity(getIntent(environmentID,true,false,true,0));
                    break;
                case 3:
                    mContext.startActivity(getIntent(environmentID,true,true,false,15));
                    break;
                case 4:
                    mContext.startActivity(getIntent(environmentID,false,false,false,5));
                    break;
                default:
                    Snackbar.make(view,"No Scenario defined",Snackbar.LENGTH_SHORT).show();
                    break;
            }
        }

        Intent getIntent(long environmentID, boolean pathEnabled, boolean motivationEnabled, boolean floorplanEnabled, long delaySec){
            Intent i = new Intent(mContext, ArActivity.class);
            i.putExtra(ScenarioSelectActivity.KEY_ENVIRONMENT_ID,environmentID);
            i.putExtra(ScenarioSelectActivity.KEY_FLOORPLAN_ENABLED,floorplanEnabled);
            i.putExtra(ScenarioSelectActivity.KEY_MOTIVATION_ENABELD,motivationEnabled);
            i.putExtra(ScenarioSelectActivity.KEY_PATH_ENABLED,pathEnabled);
            i.putExtra(ScenarioSelectActivity.KEY_DELAY_SEC,delaySec);
            return i;
        }
    }
}
