module com.mateobodenlle.englishauction {
    requires javafx.controls;
    requires javafx.fxml;
    requires jade;


    opens com.mateobodenlle.englishauction to javafx.fxml;
    exports com.mateobodenlle.englishauction;
}