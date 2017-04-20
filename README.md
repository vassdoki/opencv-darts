# Darts hit recognition using opencv

The goal of the project is to build a system that can provide a scoreboard for
bristile dartsboard without any modification of the board or of the steel tip darts.

The score recognition is done using two webcameras and opencv library.

I'll provide a detailed description of how to setup the board, webcams, led strip
lights and the software. 

You may see the current status of the project here: https://youtu.be/oBwwNzYzmf8

## Warning

This software is under heavy development. It is not ready for anything yet.

## Build the cabinet

TBD

## Setup raspberry pi and the recognition software

Right now this is quite complicated procedure. Some of the steps:

Install the operation system on the raspberry pi. I'm using rpi 3.

Use oracle java. OpenJdk was not running the software.

Compile the package with mvn package. If you are building for rpi, use the build.arm command.

Copy the config-sample.xml to config.xml and place it in the same directory as the jar.
Go through the comments in the config.xml and change the appropiate values.
The videoDeviceNumber is important.

## Configure the recognition software

Run the program using the -config switch:

```
java -jar darts-1.0.jar -config
```

This will show the image from the two cameras. 

![alt tag](https://github.com/vassdoki/opencv-darts/blob/master/docs/images/config.png)

Align the webcam, so that the yellow line in the middle of the blue rectangle points
directly to the center of the bull.

The blue rectangle defined by the area part of the config.xml.
Change the config.xml so that the blue area does not contain anything else, just the white
background. Save the config.xml and the change is visible on the fly.

If the image is not bright enough, change the exposure of the webcam

```aidl
v4l2-ctl -d /dev/video0 --list-ctrls
v4l2-ctl -d /dev/video0 --set-ctrl=exposure_absolute=30
```

Next set the area/zeroy value. This changes the red line. The red line should set to
the surface of the dartboard.

Set the threshold/min value, so that the image without a dart will be completly black.
And if there is a dart, it is clearly visible. 
But you can't see it right now in the config view <= TBD (run the jar with the -run parameter and set DEBUG_DART_FINDER to 1 for now)

## Calibrate the table.
 
First save data for the calibration

```bash
java -jar darts-1.0.jar -calib
```

Place the dart in the far right tripple corner of the number shown on the screen. Keep
 it there until the counter reaches 0. Hold the dart in the right angle so that the red
 dot is where the dart is pointing.

After this process a calib_points.csv is saved. Run the calibration.
There is no switch for that now. But it would run slowly on the rpi, so I would
copy the csv to a strong computer, and raun the DartsCalibration.scala there.
TBD: make this easier.

The calibration prints the xml values to the output, copy those into the config.xml.
TBD: Later this will be automatic.

## Scoreboard

Setup the scoreboard server: https://github.com/IPeter/darts-go

## Run the system

```bash
java -jar darts-1.0.jar -calib
```

This is just a quick first version of the readme. Create an issue if you have any problems.