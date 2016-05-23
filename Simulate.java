
import java.util.Random;

/**
 * Author: Andrew Jarombek
 * Date: 3/25/2016 - 3/29/2016
 * Methods to help simulate loss and corruption in UFT Client and Server.
 */
public class Simulate {

    public static boolean isCorrupt(double corruptionRate) {
        Random random = new Random();
        int corruption = (int) (corruptionRate*100);
        int randomCorruption = Math.abs(random.nextInt() % 100);
        return (randomCorruption < corruption);
    }

    public static boolean isLost(double lossRate) {
        return isCorrupt(lossRate);
    }
}
