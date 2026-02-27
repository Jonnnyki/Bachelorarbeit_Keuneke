import os
import sys

import numpy as np
import pandas as pd
from flask import Flask, flash, request, redirect
from joblib import load
from werkzeug.utils import secure_filename

# Defines paths to correctly find the required folders
script_dir = os.path.dirname(os.path.abspath(sys.argv[0]))

UPLOAD_FOLDER = os.path.join(script_dir, 'UploadFolder')

prediction_rest_api = Flask(__name__)
prediction_rest_api.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER
if not os.path.exists(UPLOAD_FOLDER):
    os.makedirs(UPLOAD_FOLDER)


# Only allows CSV files
def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() == 'csv'


# Sets up the prediction method.
@prediction_rest_api.route('/prediction', methods=['GET', 'POST'])
def upload_file():
    if request.method == 'POST':

        if 'file' not in request.files:
            flash('File missing from the post request')
            return redirect(request.url)

        file = request.files['file']
        if file.filename == '':
            flash('No file selected')
            return redirect(request.url)

        if file and allowed_file(file.filename):
            if file.content_length == 0:
                return ''

            # Saves the file locally and uses it for prediction
            filename = secure_filename(file.filename)
            file.save(os.path.join(prediction_rest_api.config['UPLOAD_FOLDER'], filename))
            predicted_motion = predict(UPLOAD_FOLDER + '/' + filename)
            return predicted_motion

    return 'Not a POST request'


# Define paths
saved_model_path = os.path.join(script_dir, 'Models')


def preprocess_file(file_path):
    # Preprocessing the data for prediction
    with open(file_path, 'r') as file:
        rows = [line.strip().split(',') for line in file]

    max_length = max(len(row) for row in rows)
    data_frame = pd.read_csv(file_path, header=None, names=range(max_length))
    data_frame.columns = ['sensorname' if i == 0 else 'timestamp' if i == 1 else f'sensorvalues{i}'
                          for i in range(data_frame.shape[1])]
    padded_data_frame = data_frame.fillna(0.0)

    # Start feature extraction
    unique_sensor_names_list = padded_data_frame['sensorname'].unique()

    feature_data = []

    for sensor in unique_sensor_names_list:
        sensor_data = padded_data_frame[padded_data_frame['sensorname'] == sensor]

        sensor_values = sensor_data.iloc[:, 2:].values

        mean_values = np.mean(sensor_values, axis=0)
        std_values = np.std(sensor_values, axis=0)
        min_values = np.min(sensor_values, axis=0)
        max_values = np.max(sensor_values, axis=0)

        feature_dictionary = {'sensorname': sensor}
        for i in range(len(mean_values)):
            feature_dictionary[f'mean{i + 1}'] = mean_values[i]
            feature_dictionary[f'std{i + 1}'] = std_values[i]
            feature_dictionary[f'min{i + 1}'] = min_values[i]
            feature_dictionary[f'max{i + 1}'] = max_values[i]

        feature_data.append(feature_dictionary)

    feature_dataframe = pd.DataFrame(feature_data)

    return feature_dataframe.to_numpy()


def label_decoding(label_number):
    # Decodes the predictions to the names of the motions
    motion_labels = {
        1: 'Idle',
        2: 'Shaking',
        3: 'Spin',
        4: 'Standing up',
        5: 'Walking',
    }
    return motion_labels.get(label_number, 'unknown')


def predict(file_path):
    # Load the model
    model = load(os.path.join(saved_model_path, 'RFThesisModel.joblib'))

    # Preprocess the input file
    features = preprocess_file(file_path)

    # Make predictions
    predictions = model.predict(features)

    print('Predictions:', predictions)

    # Getting the decoded prediction
    most_common_prediction = np.bincount(predictions).argmax()
    motion_name = label_decoding(most_common_prediction)

    print('Decoded Prediction:', motion_name)

    # Returns decoded prediction
    return motion_name


if __name__ == '__main__':
    prediction_rest_api.run(host='0.0.0.0', port=5000)
