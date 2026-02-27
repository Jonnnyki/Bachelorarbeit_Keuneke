# Erfassung und Klassifizierung von Sensordaten (HAR)
Bachelorarbeit | Universität Bremen

Dieses Repository enthält eine vollständige Pipeline zur synchronisierten Erfassung von Smartphone-Sensordaten sowie deren automatisierte Klassifizierung mittels Machine Learning.

## Übersicht
Das Ziel dieser Arbeit war die Entwicklung eines Systems zur effizienten Aufnahme und Annotation von Bewegungsdaten. Ein Kernaspekt liegt in der Reduktion des manuellen Preprocessing-Aufwands durch eine direkt in den Aufnahmeprozess integrierte Segmentierung. Die Validierung erfolgte durch die Erkennung komplexer Bewegungsmuster.

## Kernkomponenten
* Native Android App (Java): Implementierung einer hochfrequenten Datenaufzeichnung für Smartphone-Sensordaten.
* On-Device Segmentierung: Integration einer Benutzeroberfläche zum Starten und Stoppen der Aufnahmen sowie die Nutzung der Lautstärketasten, um eine Segmentierung während der Aufnahme zu gewährleisten.
* Signalverarbeitung und Feature Extraction: Berechnung von statistischen Merkmalen (Mittelwert, Standardabweichung, Minimum, Maximum) über gleitende Zeitfenster zur Charakterisierung der Signale.
* ML-Klassifizierung: Einsatz eines Random Forest Classifiers zur Unterscheidung verschiedener Aktivitätsklassen wie Stehen, Gehen oder Drehen.



## Struktur des Repositories
* android-app/: Quellcode der Android-App zur Sensordatenerfassung und manuellen Annotation.
* ml-pipeline/: Python-Umgebung zur Datenanalyse und Modellentwicklung.
* Models/: Das trainierte Random-Forest-Modell als serialisierte Datei (.pkl).
* TestDataAll/: Rohdaten und prozessierte CSV-Dateien aus 125 kontrollierten Versuchsreihen.

## Technischer Stack
* Sprachen: Java (Android SDK), Python
* Datenanalyse: Pandas, NumPy, Scikit-learn
* Kommunikation: Flask-basiertes REST-Interface zur Datenübertragung

## Ergebnisse der Validierung
In den Testreihen wurde eine Klassifikationsgenauigkeit von bis zu 100% erreicht. Dies belegt die hohe Qualität der durch die App annotierten Trainingsdaten und die Eignung der gewählten Merkmalsextraktion für die Unterscheidung von Bewegungsmustern.

## Installation und Nutzung
1. Android: Den Ordner android-app in Android Studio öffnen und auf einem Smartphone (Android 8.0+) installieren.
2. ML-Pipeline: Die notwendigen Bibliotheken via pip install -r requirements.txt installieren. Das Training und die Evaluation erfolgen über das Skript ModelTrain.py.

Kontakt: Johann Joachim Keuneke | Johannkeuneke@gmail.com