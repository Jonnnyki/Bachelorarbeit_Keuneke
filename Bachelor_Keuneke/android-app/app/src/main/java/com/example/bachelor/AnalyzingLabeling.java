package com.example.bachelor;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class AnalyzingLabeling extends AppCompatActivity {

    private ArrayList<Sensor> allSensors;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analyzing_labeling);
        setSensorListView();
    }

    /**
     * Sets the listView in activity_analyzing with all sensors of the device
     *
     */
    public void setSensorListView() {
        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        allSensors = new ArrayList<>(sensors);

        ListView listView = findViewById(R.id.analyzingSensorList);
        ArrayList<String> sensorName = new ArrayList<>();

        for (Sensor s : sensors) {
            sensorName.add(s.getStringType());
        }

        //Makes the listView Checkable
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this
                , android.R.layout.simple_list_item_activated_1
                , sensorName);
        listView.setAdapter(arrayAdapter);
    }

    /**
     * Returns all selected Sensors.
     *
     * @return A list of all the Sensors, which have been selected
     * in the activity_analyzing listView.
     */
    public ArrayList<Integer> getSelectedSensors() {
        // Creates a arraylist to return, the listView with the checked items and
        // sparse boolean array to get the selected sensors from the listView
        ArrayList<Integer> selectedSensors = new ArrayList<>();
        ListView listView = findViewById(R.id.analyzingSensorList);
        SparseBooleanArray checked = listView.getCheckedItemPositions();

        // Iterates over the list of all sensors and adds the ones that got selected
        // in the list view to the selectedSensors arraylist.
        for (int i = 0; i < allSensors.size(); i++) {
            if (checked.get(i)) {
                selectedSensors.add(allSensors.get(i).getType());
            }
        }
        return selectedSensors;
    }

    /**
     * Starts the activity AnalyzingRecording after checking if at least
     * one sensor has been selected.
     *
     * @param v The button in the view that gets used for this action.
     */
    public void launchAnalyzeRecording(View v) {
        ArrayList<Integer> selectedSensors = getSelectedSensors();

        //Check if at least one sensor has been selected.
        if (selectedSensors.isEmpty()) {
            Toast.makeText(AnalyzingLabeling.this
                    , "Need to select at least one sensor."
                    , Toast.LENGTH_LONG).show();
            return;
        }

        //Intent gives List of selected sensors and Label to the next activity
        Intent intent = new Intent(this, AnalyzingRecording.class);
        intent.putIntegerArrayListExtra("selectedSensors", selectedSensors);
        startActivity(intent);
    }
}