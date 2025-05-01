# QuPath extension for Glomerular Lesion Segmentation
<img src="https://github.com/poleonia/Segmentation-Extension-for-Qupath/blob/main/Seg01.png" width="768">


This is a QuPath extension


## Build and Install the extension

#### install Docker
```
sudo apt-get install apt-transport-https ca-certificates curl software-properties-common
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
sudo add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"
sudo add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu bionic stable"
sudo apt-get update
sudo apt-get install docker-ce
```

#### install Nvidia-Docker
```
curl -s -L https://nvidia.github.io/nvidia-docker/gpgkey | sudo apt-key add -
distribution=$(. /etc/os-release;echo $ID$VERSION_ID)
curl -s -L https://nvidia.github.io/nvidia-docker/$distribution/nvidia-docker.list | sudo tee /etc/apt/sources.list.d/nvidia-docker.list
sudo apt-get update
sudo apt-get install -y nvidia-docker2
```
#### Get our docker image

```
sudo docker pull hrlblab333/public:glo_in_one_v2
```


### Building your extension
Building the extension with Gradle should be pretty easy - you don't even need to install Gradle separately, because the 
[Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) will take care of that.

Open a command prompt, navigate to where the code lives, and use
```bash
gradlew build
```

The built extension should be found inside `build/libs`.

### Extension Installation
You can drag this onto QuPath to install it.
You'll be prompted to create a user directory if you don't already have one.

The extension here will install a new command `Java extension` under the `Extensions` menu in QuPath.

> In case your extension contains external dependencies beyond what QuPath already includes, you can create a 
> [single jar file](https://imperceptiblethoughts.com/shadow/introduction/#benefits-of-shadow) that bundles these along 
> with your extension by using
> ```bash
> gradlew shadowJar
> ```
> If you don't do that, you'll need to drag *all* the extra dependences onto QuPath to install them as well.



## Usage
<img src="https://github.com/poleonia/Segmentation-Extension-for-Qupath/blob/main/Seg02.png" width="768">
1.Drag the image into Qupath<br>
2.Select  `Extensions`  >`Java extension` >  `Run GLO Seg`  from the menu bar.<br>

## How to create a simple extension

This video demonstrates how to create a simple QuPath plugin to execute your existing Python code. It covers the essential steps to set up the plugin, integrate Python scripts, and run custom analyses within the QuPath environment.<br>
[Click here to watch the video on creating a QuPath plugin](https://youtu.be/T_TlwO1F628)



## Acknowledgement
[Qupath](https://qupath.github.io/)
