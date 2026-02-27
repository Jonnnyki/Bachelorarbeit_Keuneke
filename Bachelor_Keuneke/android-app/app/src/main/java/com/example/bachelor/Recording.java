package com.example.bachelor;

import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Recording extends AppCompatActivity implements SensorEventListener {

    private static final int PRINTING_INTERVAL = 40;
    private static final int PICK_DOWNLOADS_DIRECTORY_REQUEST_CODE = 456;

    private ArrayList<Integer> selectedSensorsInteger;
    private String label;
    private SensorManager sensorManager;
    private ArrayList<String> selectedSensorsString;
    private HashMap<String, String> sensorData;
    private DocumentFile labelDocumentDirectory;
    private boolean isRecording;
    private OutputStream outputStream;
    private ScheduledExecutorService printScheduler;
    private List<LineGraphSeries<DataPoint>> lineGraphSeriesList;
    private GraphView graph;
    private int graphIndex;
    private List<String> yLabelsList;
    private String recordingIterationsLabel;
    private int recordingIterations;
    private boolean isDirectorySelected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording);

        // Initiates the data from previous activity.
        Bundle b = getIntent().getExtras();
        if (b != null) {
            selectedSensorsInteger = b.getIntegerArrayList("selectedSensors");
            label = b.getString("label");
        }

        // Initiates graph stuff
        graphIndex = 0;
        lineGraphSeriesList = new ArrayList<>();
        graph = findViewById(R.id.sensorGraph);
        graph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        graph.setVisibility(View.INVISIBLE);

        // Initiates sensorManager with devices Sensors
        sensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        selectedSensorsString = createSelectedSensors(selectedSensorsInteger);

        // Displays selected Label and selected Sensors.
        TextView recordingLabel = findViewById(R.id.recordingLabel);
        String selectedLabel = "Selected Label: " + label;
        recordingLabel.setText(selectedLabel);

        // Helper for recording set amount of motions
        recordingIterationsLabel = selectedLabel;
        recordingIterations = 0;

        // Fill sensorData with all selectedValues as key and no value as value
        sensorData = new HashMap<>();
        for (String s : selectedSensorsString) {
            sensorData.put(s, "0");
        }

        // Let user pick a directory for storage
        if (labelDocumentDirectory == null) {
            requestDirectoryAccess();
        } else {
            isDirectorySelected = true;
        }

    }

    /**
     * Creates a string list with all the selected sensors stringTypes
     *
     * @param sensorTypes List of sensor IDs
     * @return List of sensor stringTypes
     */
    public ArrayList<String> createSelectedSensors(List<Integer> sensorTypes) {
        // Creating arraylist to return and a list of all available sensors
        ArrayList<String> sensorNames = new ArrayList<>();
        List<Sensor> Sensors = sensorManager.getSensorList(Sensor.TYPE_ALL);

        // Iterate over the sensorTypes list and checks for the matching sensor in the list
        // of all available sensors and adds them to the return arraylist
        for (Integer sensorType : sensorTypes) {
            for (Sensor s : Sensors) {
                if (s.getType() == sensorType) {
                    sensorNames.add(s.getStringType());
                    break;
                }
            }
        }
        return sensorNames;
    }

    /**
     * Method to request user to select a folder using Storage Access Framework (SAF).
     */
    private void requestDirectoryAccess() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

        // Preset external download folder for file picker if possible
        Uri downloadsUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADownload");
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsUri);

        startActivityForResult(intent, PICK_DOWNLOADS_DIRECTORY_REQUEST_CODE);
    }

    /**
     * Handles the result from the Storage Access Framework (SAF) folder picker.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Assume wrong file structure, sets true if correct file structure achieved
        isDirectorySelected = false;

        // Is the result for the folder picking request?
        if (requestCode != PICK_DOWNLOADS_DIRECTORY_REQUEST_CODE) {
            return;
        }

        // Was a folder selected by the user?
        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        Uri treeUri = data.getData();

        // Is the selected folder valid?
        if (treeUri == null) {
            return;
        }

        // Permanent read and write permission for the selected folder
        getContentResolver().takePersistableUriPermission(treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        DocumentFile baseDownloadsDir = DocumentFile.fromTreeUri(this, treeUri);

        // Was the selected folder created?
        if (baseDownloadsDir == null) {
            return;
        }
        DocumentFile recordingStorageDir = baseDownloadsDir.findFile("Bio-Signal-Recording Storage");
        if (recordingStorageDir == null || !recordingStorageDir.isDirectory()) {
            recordingStorageDir = baseDownloadsDir.createDirectory("Bio-Signal-Recording Storage");

            // Was the main folder found or created?
            if (recordingStorageDir == null) {
                return;
            }
        }
        labelDocumentDirectory = createDuplicateDirectoriesSAF(label + "_Recordings", recordingStorageDir);

        // Folder structure correctly created? valid to start recording.
        if (labelDocumentDirectory == null) {
            return;
        }

        isDirectorySelected = true;
    }


    /**
     * Updating or Initializing the graph.
     */
    public void updateGraphView() {

        HashMap<String, String> graphValueTemp = new HashMap<>(sensorData);
        graph.setVisibility(View.VISIBLE);

        int[] colors = {Color.RED, Color.BLUE, Color.YELLOW};

        // If the lineGraphSeriesList has no entries, create a graph with the given values
        // If not just update the graph with the given values
        if (lineGraphSeriesList.isEmpty()) {
            initializeGraph(graphValueTemp, colors);
        } else {
            updateGraphData(graphValueTemp);
        }

        // Setting the layout of the graph
        setLayout();
    }

    /**
     * Initializing the graph if no previous graph existed.
     * @param graphValueTemp clone of the sensorData hashmap to get the needed values.
     * @param colors an array of colors for the lineGraphs.
     */
    private void initializeGraph(HashMap<String, String> graphValueTemp, int[] colors) {

        yLabelsList = new ArrayList<>();
        graphIndex = 0;
        graph.removeAllSeries();

        // Iterates through all selected Sensors and their values
        int graphVerticalIndex = 0;
        for (Map.Entry<String, String> entry : graphValueTemp.entrySet()) {

            // If one sensor hasn't been updated once since the start of the recording
            // remove all previously created data and return
            if (entry.getValue().equals("0")) {
                lineGraphSeriesList.clear();
                graph.removeAllSeries();
                return;
            }

            Sensor wantedSensor = getWantedSensor(entry.getKey());
            String yLabelSensorName = getSensorName(wantedSensor);

            // Grabs the values of the sensor and creates a line graph series for each value
            String[] valuesSplit = entry.getValue().split(",");
            for (int i = 2; i < valuesSplit.length; i++) {
                double valueDouble = Double.parseDouble(valuesSplit[i]);
                assert wantedSensor != null;
                double yValue = valueDouble / wantedSensor.getMaximumRange() + (double) graphVerticalIndex;

                LineGraphSeries<DataPoint> series = new LineGraphSeries<>();
                series.appendData(new DataPoint(graphIndex, yValue)
                        , true
                        , 25);
                series.setColor(colors[graphVerticalIndex % colors.length]);

                yLabelsList.add(yLabelSensorName + (i - 1));
                graphVerticalIndex++;
                lineGraphSeriesList.add(series);
            }
        }

        // Fills the graph with the created lineGraphSeriesList
        for (LineGraphSeries<DataPoint> series : lineGraphSeriesList) {
            graph.addSeries(series);
        }

        // Custom LabelFormatter to use sensor names as vertical labels
        graph.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter() {
            @Override
            public String formatLabel(double value, boolean isValueX) {

                // Check if the value is within the bounds of customLabels
                int index = (int) value;
                if (index >= 0 && index < yLabelsList.size()) {

                    // Return the corresponding label from customLabels
                    return yLabelsList.get(index);
                }

                // If the value is out of bounds, return an empty string
                return "";
            }
        });
    }

    /**
     * Updating the graph with data from the sensorData hashmap.
     * @param graphValueTemp clone of the sensorData hashmap to get the needed values.
     */
    private void updateGraphData(HashMap<String, String> graphValueTemp) {
        int graphVerticalIndex = 0;

        // Iterates through all selected Sensors and their values.
        for (Map.Entry<String, String> entry : graphValueTemp.entrySet()) {

            Sensor wantedSensor = getWantedSensor(entry.getKey());

            // Grabs the values of the sensor and creates a line graph series for each value
            String[] valuesSplit = entry.getValue().split(",");
            for (int i = 2; i < valuesSplit.length; i++) {
                assert wantedSensor != null;
                float maxRange = wantedSensor.getMaximumRange();
                float minRange = -maxRange;
                float currentValue = Float.parseFloat(valuesSplit[i]);
                float maximumRangeFloat = getMaxRangeFloat(currentValue, maxRange, minRange);

                double valueDouble = Double.parseDouble(valuesSplit[i]);
                double yValue = valueDouble / maximumRangeFloat + (double) graphVerticalIndex;

                lineGraphSeriesList.get(graphVerticalIndex).appendData(new DataPoint(graphIndex, yValue)
                        , true
                        , 25);
                graphVerticalIndex++;
            }
        }
    }

    /**
     * Helper function to get the absolute maximum range of a sensors value.
     * @param currentValue Value of the most recent sensor call.
     * @param maxRange The maximum range of the sensor according to the method.
     * @param minRange The negative max range according to the method
     * @return the current absolute max range of the sensors values.
     */
    private float getMaxRangeFloat(float currentValue, float maxRange, float minRange) {
        // Checks which is bigger and returns the biggest
        if (currentValue >= maxRange) {
            return currentValue;
        } else if (currentValue <= minRange) {
            return Math.abs(currentValue);
        } else {
            return maxRange;
        }
    }

    /**
     * Helper function to get the sensor object for the selected sensor.
     * @param sensorKey the stringType of the desired sensor.
     * @return the desired sensor.
     */
    private Sensor getWantedSensor(String sensorKey) {
        // Gets the Sensor from list of all Sensors
        List<Sensor> sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor sensor : sensorList) {
            if (sensor.getStringType().equals(sensorKey)) {
                return sensor;
            }
        }
        return null;
    }

    /**
     * Helper function to get the first 3 letters of the sensor name.
     * @param wantedSensor the sensor object for the desired name.
     * @return the first 3 letters of the sensor name or the first letters if shorter then 3.
     */
    private String getSensorName(Sensor wantedSensor) {
        //returns the stringType names first 3 letters
        if (wantedSensor == null) return null;
        String[] labelName = wantedSensor.getStringType().split("\\.");
        return labelName[2].substring(0, Math.min(labelName[2].length(), 3));
    }

    /**
     * Sets the layout for the graph.
     */
    private void setLayout() {

        // Increase the graphIndex for the x axis
        graphIndex++;

        // Sets the label size and viewport behaviour of the graph
        graph.onDataChanged(true, false);

        // Setting the viewport bounds
        double maxX = graph.getViewport().getMaxX(true);

        // Setting max and min Y +/- 1 to have a bit of a buffer between the edge
        // and the highest/lowest graph point
        // Setting the minX to be never less then 0 and at least 24 away from the maxX
        // (maxDataPoints:25)
        graph.getViewport().setMaxY(yLabelsList.size());
        graph.getViewport().setMinY(-1);
        graph.getViewport().setMaxX(maxX);
        graph.getViewport().setMinX(Math.max(maxX - 24.0, 0.0));

        // Setting the Labels
        graph.getGridLabelRenderer().setNumVerticalLabels(yLabelsList.size() + 2);
        graph.getGridLabelRenderer().setLabelsSpace(10);
        graph.getGridLabelRenderer().setLabelVerticalWidth(100);

        // Enabling manual viewport bound setting
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setXAxisBoundsManual(true);
    }

    /**
     * Starts/Stops the recording if either the volume up or down key was released.
     *
     * @param event Event of the key press
     * @return True if volume key has been pressed and started/stopped recording.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {

        // Overwrite volume key release to start or stop and holding/pressing the keys does nothing
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_UP) {
                    try {
                        if (!isRecording) {
                            startRecording(findViewById(R.id.labelingRecordingStart));
                        } else {
                            stopRecording(findViewById(R.id.labelingRecordingStop));
                        }
                        return true;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    /**
     * Gets called whenever the sensor values change.
     * Builds a String for each sensor and their values and stores them in the
     * HashMap sensorData.
     *
     * @param sensorEvent The event of a sensor value changing.
     */
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        // Getting the string and int typ of the sensor that triggered the SensorEvent
        String sensorType = sensorEvent.sensor.getStringType();
        Integer sensorIntType = sensorEvent.sensor.getType();



        // Fills the sensorData Hashmap with the sensor values
        StringBuilder s = new StringBuilder();
        s.append(sensorIntType).append(",").append(System.currentTimeMillis());
        for (float value : sensorEvent.values) {
            String valueString = String.valueOf(value);
            s.append(",").append(valueString);
        }

        s.append("\n");
        sensorData.put(sensorType, s.toString());
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        //nothing
    }

    /**
     * Connects the sensor manager for every selected sensor to the listener
     * and starts to write the sensor data every x ms.
     *
     * @param view The button used to start this method
     */
    public void startRecording(View view) throws IOException {
        if (isRecording) return;
        if (!isDirectorySelected || labelDocumentDirectory == null) {
            requestDirectoryAccess();
            return;
        }

        isRecording = true;
        view.setEnabled(false);

        // Clean the graph
        graph.removeAllSeries();
        lineGraphSeriesList.clear();
        graphIndex=0;

        //Helper for recording set number of motions
        recordingIterations++;
        TextView recordingLabel = findViewById(R.id.recordingLabel);
        String setTextTemp = recordingIterationsLabel + "; Recording  " + recordingIterations;
        recordingLabel.setText(setTextTemp);

        //Registers the sensors to the listener.
        List<Sensor> toBeRegistered = sensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor sensor : toBeRegistered) {
            for (String sensorType : selectedSensorsString) {
                if (sensor.getStringType().equals(sensorType)) {
                    sensorManager.registerListener(this
                            , sensor
                            , SensorManager.SENSOR_DELAY_GAME);
                }
            }
        }

        //Create the csv DocumentFile and creates an OutputStream
        DocumentFile csvDocumentFile = createCSVFiles(labelDocumentDirectory, label);
        outputStream = getContentResolver().openOutputStream(csvDocumentFile.getUri());


        //Starts the writing of the sensor date every x ms.
        Runnable printData = () -> {
            try {
                printData();
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    stopRecording(findViewById(R.id.labelingRecordingStop));
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        };
        printScheduler = Executors.newSingleThreadScheduledExecutor();
        printScheduler.scheduleAtFixedRate(printData
                , 0
                , PRINTING_INTERVAL
                , TimeUnit.MILLISECONDS);
    }

    /**
     * Creates directories with duplicate names and adds an increasing identifier in the name
     * using DocumentFile.
     *
     * @param name Name of the directory.
     * @param parentDirectory The parent DocumentFile representing the directory.
     * @return Returns the DocumentFile of the created directory.
     */
    public DocumentFile createDuplicateDirectoriesSAF(String name, DocumentFile parentDirectory) {
        int i = 1;
        DocumentFile file;
        // Searches the directory for directories with the given name if one is found
        // the index is increased, added to the name and searched again.
        // If no directories with that name is found
        // create a directory with that name.
        do {
            String fileName = name + (i > 1 ? "_" + i : "");
            file = parentDirectory.findFile(fileName); // Check if exists
            if (file != null && file.exists() && file.isDirectory()) {
                i++;
            } else {
                return parentDirectory.createDirectory(fileName);
            }
        } while (true); // Loop until a unique directory is created
    }

    /**
     * Creates csv files with the date of creation in the name.
     *
     * @param parentDir DocumentFile representing the parent directory where the file is stored.
     * @param label     Name of the file
     * @return the DocumentFile of the created csv file.
     */
    private DocumentFile createCSVFiles(DocumentFile parentDir, String label) {
        // Create CSV file with name format for current Date
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy_HH-mm-ss", Locale.GERMAN);
        String fileName = label + "_" + dateFormat.format(date) + ".csv";

        return parentDir.createFile("text/csv", fileName);
    }


    /**
     * Disconnects the sensorManager and stops writing the sensor data.
     */
    public void stopRecording(View view) throws IOException {
        if (!isRecording) return;

        // Shuts down the execution scheduler
        if (printScheduler != null && !printScheduler.isShutdown()) {
            printScheduler.shutdown();
            printScheduler = null;
        }

        //Closing outputStream
        if (outputStream != null) {
            outputStream.close();
            outputStream = null;

        }

        // Enables the start recording button
        Button button = findViewById(R.id.labelingRecordingStart);
        button.setEnabled(true);

        // Unregisters the listener
        sensorManager.unregisterListener(this);
        isRecording = false;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    /**
     * Unregisters the sensorManager from the Listener after leaving the activity.
     */
    @Override
    public void onPause() {
        super.onPause();
        if (isRecording) {
            try {
                stopRecording(null);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Writes the sensor data into a file using the OutputStream.
     */
    public void printData() throws IOException {
        if (outputStream == null) {
            throw new IOException("OutputStream is null. File not opened.");
        }

        // Appends the sensorData values to the file writer
        for (String data : sensorData.values()) {
            if (!Objects.equals(data, "0")) {
                outputStream.write(data.getBytes());
                outputStream.flush(); // Flush to ensure data is written
            }
        }

        // Updates the graphView
        runOnUiThread(this::updateGraphView);

    }
}