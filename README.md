# Google Cloud IoT Core Provisioning App
Android App that demonstrates how to provision a device into [Google Cloud IoT Core](https://cloud.google.com/iot-core/).

This project is the device half of the Cloud IoT Core demo. See the [IoT Core Demo Node.js App
](https://github.com/agosto-dev/iotcore-raspbian-demo) for the device component of this demo. 

We also have an [Android Things Demo App](https://github.com/Agosto/iotcore-androidthings-demo)

## Install App from Google Play
You can install this app from google play here.  You must sign in with a Google account with the required IAM permissions. 

https://play.google.com/store/apps/details?id=com.agosto.iotcoreprovisioning

## Building App

If you want to build this app for your own project, you will need to generate a configuration file from your own Google Cloud Project. 
See [Get a configuration file](https://developers.google.com/identity/sign-in/android/start-integrating#get-config) section of 
[Google Integration guide](https://developers.google.com/identity/sign-in/android/start-integrating).

# APIs
The following APIs must be enabled in your gcp project

- Google Cloud IoT API
- Google Cloud Resource Manager API
- Google android sign in apis?
