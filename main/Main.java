package main;

import controllers.AppController;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * @author Izabella Szabo
 * Created on 24/12/2015.
 */
public class Main extends Application {

    public static void main(String[] args){
        launch(args);   // Method will call the Start Method below.
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        new AppController().start(primaryStage);
    }
}
