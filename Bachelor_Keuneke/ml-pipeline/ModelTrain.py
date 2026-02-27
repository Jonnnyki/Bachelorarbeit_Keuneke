import os
import re
import sys

import numpy as np
import pandas as pd
from joblib import dump
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import accuracy_score
from sklearn.model_selection import train_test_split, cross_val_score

# Define paths
script_dir = os.path.dirname(os.path.abspath(sys.argv[0]))

saved_model_path = os.path.join(script_dir, 'Models')
recorded_data_path = os.path.join(script_dir, 'TestDataAll')


def label_encoding(motion_name):
    # Encodes the labels
    motion_name = motion_name.lower()
    motion_labels = {
        'idle': 1,
        'shaking': 2,
        'spin': 3,
        'standing up': 4,
        'walking': 5,

    }
    return motion_labels.get(motion_name, 0)


def preprocess_file(file_path):
    # Preprocess the CSV file and return feature and label dataframes.
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
    print(feature_dataframe)

    return feature_dataframe


def main():
    feature_data_frames = []
    label_data_frames = []

    # Checks if the correct file is being used
    for filename in os.listdir(recorded_data_path):
        if filename.endswith(".csv"):
            match = re.match(r'^(.+?)[\s_\-]*\d{2}\.\d{2}\.\d{4}_\d{2}-\d{2}-\d{2}\.csv$', filename)
            if match:
                motion_name = match.group(1)
                label = label_encoding(motion_name)
                file_path = os.path.join(recorded_data_path, filename)

                # Preprocesses file and creates label data frame
                data_frame = preprocess_file(file_path)
                label_data_frame = pd.DataFrame({'label': [label] * len(data_frame)})

                feature_data_frames.append(data_frame)
                label_data_frames.append(label_data_frame)

    # Concatenate dataframes
    concat_feature = pd.concat(feature_data_frames, ignore_index=True)
    concat_label = pd.concat(label_data_frames, ignore_index=True)

    # Split data into training and testing sets
    x_train, x_test, y_train, y_test = train_test_split(concat_feature, concat_label,
                                                        test_size=0.2, random_state=42, shuffle=True)

    # Convert dataframes to numpy arrays
    x_train, x_test = x_train.to_numpy(), x_test.to_numpy()
    y_train, y_test = y_train.to_numpy().ravel(), y_test.to_numpy().ravel()

    # Create and train the random forest model
    rf_model = RandomForestClassifier(n_estimators=100, random_state=29)
    rf_model.fit(x_train, y_train)

    # Cross validation
    cv_scores = cross_val_score(rf_model, x_train, y_train, cv=5)
    print("Cross-validation scores:", cv_scores)
    mean_cv_score = np.mean(cv_scores)
    print("Mean cross-validation score:", mean_cv_score)

    # Make predictions and calculate accuracy
    predictions = rf_model.predict(x_test)
    accuracy = accuracy_score(y_test, predictions)
    print('Accuracy:', accuracy)

    # Save the model
    dump(rf_model, os.path.join(saved_model_path, 'RFThesisModel.joblib'))


if __name__ == '__main__':
    main()
