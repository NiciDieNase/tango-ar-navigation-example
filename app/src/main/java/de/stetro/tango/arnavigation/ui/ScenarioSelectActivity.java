package de.stetro.tango.arnavigation.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;

import de.stetro.tango.arnavigation.R;
import de.stetro.tango.arnavigation.data.persistence.EnvironmentDAO;

/**
 * Created by felix on 27/03/17.
 */

public class ScenarioSelectActivity extends AppCompatActivity {

    public static final String KEY_PATH_ENABLED = "path_enabled";
    public static final String KEY_MOTIVATION_ENABELD = "motivation_enabled";
    public static final String KEY_ENVIRONMENT_ID = "environment_id";
    public static final String KEY_FLOORPLAN_ENABLED = "floorplan_enabled";
    public static final String KEY_DELAY_SEC = "delay_seconds";

    private static final String SAVED_ENVIRONMENT_ID = "saved_environment";
    private static final String TAG = ScenarioSelectActivity.class.getSimpleName();
    public static final int COL_SPAN = 4;
    private Long environmentId;

    RecyclerView recyclerView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scenario_select);
        recyclerView = (RecyclerView) findViewById(R.id.recycler_view);

        SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
        environmentId = preferences.getLong(SAVED_ENVIRONMENT_ID,0);
        if(environmentId == 0){
            selectEnvironment();
        }

        GridLayoutManager layoutManager = new GridLayoutManager(this, COL_SPAN);
        recyclerView.setLayoutManager(layoutManager);
        RecyclerView.Adapter adapter = new ScenariosAdaper(this,environmentId);
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
        preferences.edit().putLong(SAVED_ENVIRONMENT_ID,environmentId).commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.scenarios_menu,menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.select_environment:
                selectEnvironment();
                break;
            case R.id.start_mapping_mode:
                startActivity(new Intent(this,ArActivity.class));
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void selectEnvironment() {
        new SelectEnvironmentFragment().setEnvironmentSelectionListener(new SelectEnvironmentFragment.EnvironmentSelectionListener() {
            @Override
            public void onEnvironmentSelected(EnvironmentDAO environment) {
                setEnvironment(environment);
            }
        }).show(getFragmentManager(),"SelectEnvFragment");
    }

    public void setEnvironment(EnvironmentDAO environment) {
        environmentId = environment.getId();
    }

}
