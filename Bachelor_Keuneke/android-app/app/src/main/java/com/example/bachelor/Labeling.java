package com.example.bachelor;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class Labeling extends AppCompatActivity {

    private ArrayList<Sensor> allSensors;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_labeling);
        setSensorListView();
    }

    /**
     * Sets the listView in activity_labeling with all sensors of the device and
     */
    public void setSensorListView() {
        // Initializing  the sensorManager and creating a Arraylist of all the available sensors
        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        allSensors = new ArrayList<>(sensors);

        // Gets the listView for displaying the sensors list
        ListView listView = findViewById(R.id.sensorList);
        ArrayList<String> sensorName = new ArrayList<>();

        // Fills the sensorName list with the string type of all available sensors
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
     * in the activity_labeling listView.
     */
    public ArrayList<Integer> getSelectedSensors() {
        // Creates a arraylist to return, the listView with the checked items and
        // sparse boolean array to get the selected sensors from the listView
        ArrayList<Integer> selectedSensors = new ArrayList<>();
        ListView listView = findViewById(R.id.sensorList);
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
     * Starts the activity Recording after checking if a label and
     * at least one sensor has been selected.
     *
     * @param v The button in the view that gets used for this action.
     */
    public void launchRecording(View v) {
        ArrayList<Integer> selectedSensors = getSelectedSensors();
        String label = ((EditText) findViewById(R.id.labelingInputText)).getText().toString();

        //Checks if label is empty.
        if (label.isEmpty()) {
            Toast.makeText(Labeling.this
                    , "No label selected."
                    , Toast.LENGTH_LONG).show();
            return;
        }

        //Check if at least one sensor has been selected.
        if (selectedSensors.isEmpty()) {
            Toast.makeText(Labeling.this
                    , "Need to select at least one sensor."
                    , Toast.LENGTH_LONG).show();
            return;
        }


        //Intent gives List of selected sensors and label to the next activity
        Intent intent = new Intent(this, Recording.class);
        intent.putIntegerArrayListExtra("selectedSensors", selectedSensors);
        intent.putExtra("label", label);
        startActivity(intent);
    }

}