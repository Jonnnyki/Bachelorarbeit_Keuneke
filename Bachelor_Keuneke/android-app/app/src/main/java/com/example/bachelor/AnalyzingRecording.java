package com.example.bachelor;

import android.annotation.SuppressLint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class AnalyzingRecording extends AppCompatActivity implements SensorEventListener {

    private static final int PRINTING_INTERVAL = 40;
    private ArrayList<Integer> selectedSensorsInteger;
    private SensorManager sensorManager;
    private ArrayList<String> selectedSensorsString;
    private boolean isRecording;
    private ConcurrentHashMap<String, String> sensorData;
    private FileWriter fileWriter;
    private ScheduledExecutorService printScheduler;
    private ScheduledExecutorService snapshotScheduler;

    public AnalyzingRecording() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analyzing_recording);

        // Unpacks the extras carried over with the intent
        Bundle b = getIntent().getExtras();
        if (b != null) {
            selectedSensorsInteger = b.getIntegerArrayList("selectedSensors");
        }

        // Creating sensor manager and the selected sensors
        sensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
        selectedSensorsString = createSelectedSensors(selectedSensorsInteger);

        // Fill the sensorData with the selected Sensors and an empty string.
        sensorData = new ConcurrentHashMap<>();
        for (String s : selectedSensorsString) {
            sensorData.put(s, "");
        }
    }

    /**
     * Creates a string list with all the selected sensors stringTypes
     *
     * @param sensorTypes List of sensor types
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
     * Starts/Stops the recording if either the volume up or down key was released.
     *
     * @param event Event of the key press
     * @return True if volume key has been pressed and started/stopped recording.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_UP) {
                    if (!isRecording) {
                        try {
                            startRecording(findViewById(R.id.analyzingRecordingStart));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        stopRecording(findViewById(R.id.analyzingRecordingStop));
                    }
                    return true;
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
        // Gets the string type and integer typ of the sensor that triggered the sensorEvent
        String sensorType = sensorEvent.sensor.getStringType();
        Integer sensorIntType = sensorEvent.sensor.getType();

        // Builds a string as input for the sensorData.
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
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //nothing
    }

    /**
     * Connects the sensor manager for every selected sensor to the listener
     * ,starts to write the sensor data every 40 ms into a CSV and creates a Snapshot
     * every 3 seconds for the Flask server.
     *
     * @param view The button used to start this method
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressLint("SetTextI18n")
    public void startRecording(View view) throws IOException {
        if (isRecording) return;
        isRecording = true;

        // Disables the start recording button if its recording
        view.setEnabled(false);

        // Displays text that the app is currently recording
        TextView recordingLabel = findViewById(R.id.recordingStatement);
        recordingLabel.setText(R.string.currently_recording);
        recordingLabel.setVisibility(View.VISIBLE);

        // Set current predicted motion visible
        TextView currentPredictedMotion = findViewById(R.id.currentPrediction);
        currentPredictedMotion.setText("");
        currentPredictedMotion.setVisibility(View.VISIBLE);

        // Remove previous predicted motion on start
        TextView previousPredictedMotion = findViewById(R.id.previousPrediction);
        previousPredictedMotion.setText("");
        previousPredictedMotion.setVisibility(View.INVISIBLE);

        //Registers the sensors to the listener.
        List<Sensor> toBeRegistered = sensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor sensor : toBeRegistered) {
            for (String sensorType : selectedSensorsString) {
                if (sensor.getStringType().equals(sensorType)) {
                    sensorManager
                            .registerListener(this
                                    , sensor
                                    , SensorManager.SENSOR_DELAY_GAME);
                }
            }
        }


        // Create the file writer and csv file where the data gets stored
        File csvFile = new File(this.getFilesDir(), "predictionDataSet.csv");
        if (csvFile.exists()) csvFile.delete();
        fileWriter = new FileWriter(csvFile);

        // Starts the writing of the sensor date every x ms.
        Runnable printData = () -> {
            try {
                printData();
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
        printScheduler = Executors.newSingleThreadScheduledExecutor();
        printScheduler.scheduleAtFixedRate(printData
                , 0
                , PRINTING_INTERVAL
                , TimeUnit.MILLISECONDS);

        // Starts the snapshotting of the CSV for posting to the Flask server.
        snapshotScheduler = Executors.newSingleThreadScheduledExecutor();
        snapshotScheduler.scheduleAtFixedRate(() -> {
            synchronized (fileWriter) {
                try {

                    File snapshotFile = new File(this.getFilesDir()
                            , "snapshot.csv");
                    Files.copy(csvFile.toPath()
                            , snapshotFile.toPath()
                            , StandardCopyOption.REPLACE_EXISTING);

                    //Sends the snapshot and callbacks when motion was predicted
                    sendCsvSnapshot(snapshotFile, prediction -> {
                        String predictedMotion = "Predicted current motion: " + prediction;
                        currentPredictedMotion.setText(predictedMotion);
                    });

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 0, 3, TimeUnit.SECONDS);
    }

    /**
     * Sends a POST request with a CSV file attached to the Python Flask Server
     * and handles the response of the predicted motion.
     *
     * @param csvFile The CSV file we want to send.
     */
    private void sendCsvSnapshot(File csvFile, Consumer<String> callback) {

        // Sets up the OkHttpClient for the csv post request
        OkHttpClient client = new OkHttpClient();
        RequestBody fileBody = RequestBody.create(MediaType.parse("text/csv"), csvFile);

        // Creating the MultipartyBody to send a csv with the request
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", csvFile.getName(), fileBody)
                .build();

        // Creating the post request and attaching the MultipartBody to it
        Request request = new Request.Builder()
                .url("http://192.168.178.43:5000/prediction")
                .post(requestBody)
                .build();

        // Sends the request and handles the responses
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected code " + response);
                }

                // Read the server's response (predicted_motion)
                assert response.body() != null;
                String responseBody = response.body().string();

                runOnUiThread(() -> {
                    callback.accept(responseBody);

                    TextView recordingLabel = findViewById(R.id.currentPrediction);
                    String predictedMotion = "Predicted current motion: " + responseBody;
                    recordingLabel.setText(predictedMotion);
            });
            }
        });
    }

    /**
     * Disconnects the sensorManager and stops writing the sensor data.
     */
    public void stopRecording(View view) {
        if (!isRecording) return;

        // Enables the start recording butting if its not recording
        Button button = findViewById(R.id.analyzingRecordingStart);
        button.setEnabled(true);

        // Create the last snapshot of the CSV file before shutting down
        synchronized (fileWriter) {
            try {
                File csvFile = new File(this.getFilesDir(), "predictionDataSet.csv");
                File snapshotFile = new File(this.getFilesDir(), "snapshot.csv");
                Files.copy(csvFile.toPath(), snapshotFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                sendCsvSnapshot(snapshotFile, prediction -> {
                    TextView previousPredicted = findViewById(R.id.previousPrediction);
                    String prevPredictedText = "Previous predicted motion: " + prediction;
                    previousPredicted.setText(prevPredictedText);
                    previousPredicted.setVisibility(View.VISIBLE);
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Shutting down the execution schedulers
        printScheduler.shutdown();
        snapshotScheduler.shutdown();

        try {
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Empty's the recordingLabel
        TextView recordingStatus = findViewById(R.id.recordingStatement);
        recordingStatus.setVisibility(View.INVISIBLE);

        // Empty the analyzingLabel
        TextView currentPredictedMotion = findViewById(R.id.currentPrediction);
        currentPredictedMotion.setVisibility(View.INVISIBLE);

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
            stopRecording(null);
        }
    }

    /**
     * Writes the sensor data into a file.
     */
    public void printData() throws IOException {

        // Appends the sensorData values to the file writer
        for (String data : sensorData.values()) {
            if (!Objects.equals(data, "0")) {
                fileWriter.append(data);
                fileWriter.flush();
            }
        }
    }
}