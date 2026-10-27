import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.tangowiz.app.SongEndPredictor;

/** Line protocol for driving the application's actual policy from the native host replay. */
public final class SongEndReplay {
    public static void main(String[] args) throws Exception {
        SongEndPredictor policy = new SongEndPredictor();
        // The host replay batches searches up to the armed deadline. Keep this
        // diagnostic access in the test harness, outside the app's public API.
        java.lang.reflect.Field pending = SongEndPredictor.class.getDeclaredField("pending");
        pending.setAccessible(true);
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = input.readLine()) != null) {
            String[] f = line.split("\t");
            if (f[0].equals("C")) { policy.clear(); System.out.println("OK"); }
            else if (f[0].equals("B")) {
                SongEndPredictor.Prediction reset = policy.takeReset(Double.parseDouble(f[1]));
                System.out.println(reset == null ? "NONE" : "RESET\t" + reset.endSeconds + "\t" + reset.identity);
            } else if (f[0].equals("O")) {
                policy.observe(Double.parseDouble(f[1]), f[2], Double.parseDouble(f[3]),
                        Double.parseDouble(f[4]), Double.parseDouble(f[5]),
                        Integer.parseInt(f[6]), Double.parseDouble(f[7]));
                SongEndPredictor.Prediction prediction = (SongEndPredictor.Prediction) pending.get(policy);
                System.out.println(prediction == null ? "OK" : "ARMED\t" + prediction.endSeconds);
            } else throw new IllegalArgumentException("Unknown command");
        }
    }
}
