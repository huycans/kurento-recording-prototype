kurento-recording-prototype
===================

Prototype for a kurento media server project. Works in Linux environment only.

Running this tutorial
---------------------
This prototpye require Java 8 and maven to run. You must run Kurento Media Server before starting this prototype, to do it enter 
```
sudo service kurento-media-server start
```
To run this prototype, enter the following commands:
```
git clone https://github.com/huycans/kurento-recording-prototype.git
cd kurento-recording-prototype/kurento-recording-prototype
mvn compile exec:java
```
After the Java server has started, go to https://localhost:8443/ in a WebRTC capable browser to run the app.
By default, the recorded file is stored in folder /tmp/, you will only see it after the room is closed (when the last user exits the chat room).


What is kurento-recording-prototype
---------------
The purpose of this project is to create a prototype implements kurento media server to record multiple audio/video streams. 


Source
------
This project is based on this repository written by Kurento developers: https://github.com/Kurento/kurento-tutorial-java/tree/master/kurento-group-call


Licensing and distribution
--------------------------

Copyright 2018 Kurento

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
