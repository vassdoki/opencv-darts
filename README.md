# This project is an old project

I leave the code here, but right now I'm not releasing my work
open source. I'm working on the commercial version which is
diverted from this code quite a lot. If you are interested
see www.dartsee.com


# Darts hit recognition using opencv

The goal of the project is to build a system that can provide a scoreboard for
bristile dartsboard without any modification of the board or of the steel tip darts.

The score recognition is done using two webcameras and opencv library.

I'll provide a detailed description of how to setup the board, webcams, led strip
lights and the software. 

You may see the current status of the project here: https://www.youtube.com/channel/UCJ4G7xQRHF6RL3PTgsYAe1w/videos


## Build the cabinet

TBD

## Setup raspberry pi and the recognition software

Right now this is quite complicated procedure. Some of the steps:

Install the operation system on the raspberry pi. I'm using rpi 3.

Use oracle java. OpenJdk was not running the software.

Compile the package with mvn package. If you are building for rpi, use the build.arm command.

Copy the config-sample.xml to config.xml and place it in the same directory as the jar.
Go through the comments in the config.xml and change the appropiate values.
The BOARD_ID and videoDeviceNumber is important.

## Test the installation

You may test java, opencv and the camera with this command

```bash
java -cp darts-1.0-jar-with-dependencies.jar experimental.FpsTest --help
java -cp darts-1.0-jar-with-dependencies.jar experimental.FpsTest --cam 0 --width 640 --height 480
```

The result should look like this

```text
===== 11.3 fps
===== 11.4 fps
===== 11.4 fps
```

## Configure the recognition software

Run the program using the -config switch:

```bash
java -jar darts-1.0-jar-with-dependencies.jar -config
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
java -jar darts-1.0-jar-with-dependencies.jar -calib
```

Place the dart in the far right double corner of the number shown on the UI. Keep
 it there until the counter reaches 0. Hold the dart in the right angle so that the red
 dot is where the dart is pointing. Repeat this from 6, 13, 4, ... bull as seen on the
image below.
 
![alt tag](https://github.com/vassdoki/opencv-darts/blob/master/docs/images/cali_order.png)


After this process a calib_points.csv is saved. The calibration code is not included,
send me the calib_points.csv and I will send you the calibration. This will be
an online service later. My username is the same on gmail.


## Scoreboard

You may use dartsee online scoreboard. Here is an intro how it works: https://www.youtube.com/watch?v=2BHW8SJZbM4&t=1s
The address is: http://game.eu.dartsee.com:8080/game/scoreboard/id
Replace id with you dart boards uniqe id, e.g. your city and nickname.

## Run the system

```bash
java -jar darts-1.0-jar-with-dependencies.jar -run
```

