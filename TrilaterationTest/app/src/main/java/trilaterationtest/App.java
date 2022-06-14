/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package trilaterationtest;
import com.lemmingapex.trilateration.TrilaterationFunction;
import com.lemmingapex.trilateration.NonLinearLeastSquaresSolver;
import java.io.File;  // Import the File class
import java.io.FileNotFoundException;  // Import this class to handle errors
import java.util.Scanner; // Import the Scanner class to read text files

import java.util.ArrayList;

import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer.Optimum;

public class App {

    private static final String RSS_ONE_FLOOR = "app/src/main/resources/RSSOneFloor.txt";
    private static final String RSS_TWO_FLOORS = "app/src/main/resources/RSSTwoFloors.txt";
    private static final String RTT_ONE_FLOOR = "app/src/main/resources/RTTOneFloor.txt";
    private static final String RTT_TWO_FLOORS = "app/src/main/resources/RTTTwoFloors.txt";

    public static void main(String[] args) {
        ArrayList<ArrayList<String>> records = readFromFile(RSS_ONE_FLOOR);
        if(records == null || records.size() == 0){
            System.out.println("No records read.");
        }
        for(int i = 0; i < records.size(); i++){
            ArrayList<String> record = records.get(i);
            double[][] positions = new double[record.size()][3];
            double[] distances = new double[record.size()];
            double x=0, y=0, z=0;
            for(int j = 0; j < record.size(); j++){
                String[] measurementString = record.get(j).replace(",", ".").split("\t");
                x = Double.parseDouble(measurementString[0]);
                y = Double.parseDouble(measurementString[1]);
                z = Double.parseDouble(measurementString[2]);
                positions[j][0] = Double.parseDouble(measurementString[3]);
                positions[j][1] = Double.parseDouble(measurementString[4]);
                positions[j][2] = Double.parseDouble(measurementString[5]);
                distances[j] = Double.parseDouble(measurementString[6]);
                // System.out.printf("%.4f\t%.4f\t%.4f\n", positions[j][0], positions[j][1], positions[j][2]);
                // System.out.println(distances[j]);
            }
            if(positions.length < 2){
                continue;
            }
            TrilaterationFunction trilateration = new TrilaterationFunction(
                positions, distances
            );
            LevenbergMarquardtOptimizer optimizer = new LevenbergMarquardtOptimizer();
            NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(trilateration, optimizer);
            Optimum solution = solver.solve();
            double[] estimatedPosition = solution.getPoint().toArray();
            System.out.printf("%.3f\t%.3f\t%.3f\t%s\t%s\t%s\t%d\n", estimatedPosition[0], estimatedPosition[1], estimatedPosition[2], x,y,z, record.size());
        }
    }

    private static ArrayList<ArrayList<String>> readFromFile(String path){
        try {
            File file = new File(path);
            System.out.println(file.getAbsolutePath());
            Scanner reader = new Scanner(file);
            ArrayList<ArrayList<String>> allLines = new ArrayList<>();
            ArrayList<String> records = new ArrayList<>();
            while (reader.hasNextLine()) {
                String data = reader.nextLine().strip();
                if(data.equals("")){
                    allLines.add(records);
                    records = new ArrayList<>();
                } else {
                    records.add(data);
                }
            }
            reader.close();
            return allLines;
        } catch (FileNotFoundException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
        return null;
    }
}
