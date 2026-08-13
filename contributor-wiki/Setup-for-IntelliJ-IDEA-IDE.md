**_Note: This assumes you have followed the [Git tutorial](git-basics)_**

## Initial Setup
#### Once you have IntelliJ installed and open, click the Open button on the top bar:

![image](uploads/667783cb5fc1cda639f3639fef0056bd/image.png)

#### Navigate to the folder you cloned your fork to, select the 2009scape folder, and then click ok on the bottom.

If IntelliJ prompts you to trust the project, say that you trust it and let it finish opening. 

#### Once it has finished opening, find the file browser on the left: 

![image](uploads/74b0cacfb02bb329a6fc0f5af9a21331/image.png)

#### Navigate to the "Server" folder inside the file browser, and locate `pom.xml`: 

![image](uploads/efa4d3ee8cb2746ce960eda31beb4258/image.png)

#### Right click this, and click `Add as Maven Project`: 

![image](uploads/850d6ccb6dccdc1f2ec043fc807a0013/image.png)

This might take a while, just let it do its thing for a moment.

#### Now select the Maven tab on the top right corner of the screen:

![image](uploads/3857819c4915c4ce40292751699f5fec/image.png)

#### Expand the server -> Plugins -> exec tabs:

![image](uploads/264b1af5afa4845d6752a9eece5fdad6/image.png)

#### Right click the `exec:java` task, and click `Modify Run Configuration`:

![image](uploads/ff6ce5ba3e3b7f04eff55147ce6a23c1/image.png)

#### Click modify options, and then click `Add before launch task`

![image](uploads/f7ec3408966bd7bda770424f4d07331c/image.png)

#### Select `Run Maven Goal`

![image](uploads/fc6377a13f993b396e848ba20388f10d/image.png)

#### Put `compile` in the Command Line section, and press Ok

![image](uploads/0e640fd297a0cfa1f9e6997a47365e0a/image.png)

#### Now click apply, and then ok

#### Now you can select the run configuration in the top right, and press the green play button to build and run the project: 

![image](uploads/5c24e060bd4dbfce0f9d18e2dbdd6d98/image.png)