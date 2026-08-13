Welcome to 2009scape's official client plugin repository! This serves as a method to distribute all of our plugins that we created for use with 2009scape. 

# Guidelines
## Plugin Rules
1. No Plugin should enable any kind of feature that could be considered 'cheating.' This includes, but is not limited to: 
    - Telling the player where to stand when fighting bosses
    - Telling the player where to go when doing clues
    - Telling the player what to click when doing quests
    - Exposing information to the player that is meant to be behind-the-scenes (e.g. timers for tick manipulation, etc)
2. All plugins MUST have their sourcecode published under a permissive license (GPL, AGPL, MIT, etc) in order to be offered here.
3. All plugins should avoid accessing any client APIs or variables outside of the scope of the API class provided for plugins. Failure to do this can get your plugin rejected.

## plugin.properties Rules
1. The Version of your plugin should be bumped every time you make changes and create a Merge Request with those changes.
2. The Versioning scheme of plugins should use only one decimal place (e.g. 1.0 and NOT 1.0.0)
3. The Author field should be populated with a comma-separated list of everyone who was involved with producing the plugin
4. The Description field can contain a decent amount of information, but please try to keep it below 500 characters. 
5. Newlines can be achieved in the Description field by placing a `\` at the end of each line. e.g.:
    ```
    DESCRIPTION=A demonstration\
    of\
    newlines
    ```
# Quickstart
1. Fork the [client repository](https://gitlab.com/2009scape/rt4-client) and clone your fork.
2. Initialize a new plugin:
    - For java, run the `plugin-playground:initializeNewJavaPlugin` gradle task
    - For kotlin, run the `plugin-playground:initializeNewKotlinPlugin` gradle task
3. Navitage to the `rt4-client/plugin-playground/src/main/{lang}` folder
4. Your generated plugin template is here under the `MyPlugin` folder.
5. Rename `MyPlugin` to whatever you want your plugin to be named.
6. Navigate to the `plugin.{java, kt}` file in this folder, and change the package statement to match your folder name.
7. For a reference to what all can be done with plugins, check the `rt4-client/client/src/main/java/plugin/Plugin.java` and `rt4-client/client/src/main/java/plugin/api/API.java` files.
8. To test your plugin, run the `plugin-playground:buildPlugins` task to compile your plugins and automatically place them in the correct location (`rt4-client/client/plugins`)
9. Now run the client with the `client:run` task, which will automatically connect to our public test server. You can now test out your plugin.

# Merge Requests
Once you have a plugin you're happy with, the source code MUST be MR'd to the [client repository](https://gitlab.com/2009scape/rt4-client). Once that's done, it will be reviewed and potentially merged. Once merged, we will publish a build of the plugin here.